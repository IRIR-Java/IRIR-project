package com.chuka.irir.service;

import com.chuka.irir.exception.FileStorageException;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Handles storage of uploaded project files and text extraction via Apache Tika.
 */
@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "zip");
    private static final long MAX_EXTRACTED_CHARS = 200_000L;

    private final Path uploadRoot;
    private final Tika tika;

    public FileStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.tika = new Tika();
        initStorage();
    }

    private void initStorage() {
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException ex) {
            throw new FileStorageException("Failed to create upload directory: " + uploadRoot, ex);
        }
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Uploaded file is empty.");
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (originalName.isBlank()) {
            throw new FileStorageException("Uploaded file name is invalid.");
        }

        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileStorageException("Unsupported file type. Allowed: PDF, DOCX, ZIP.");
        }

        String storedFileName = UUID.randomUUID() + "." + extension;
        Path targetPath = uploadRoot.resolve(storedFileName).normalize();

        if (!targetPath.startsWith(uploadRoot)) {
            throw new FileStorageException("Invalid file path.");
        }

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new FileStorageException("Failed to store file: " + originalName, ex);
        }

        String extractedText = extractTextSafely(targetPath);

        return new StoredFile(
                originalName,
                file.getContentType(),
                file.getSize(),
                targetPath.toString(),
                extractedText
        );
    }

    public Resource loadAsResource(String storagePath) {
        try {
            Path filePath = Path.of(storagePath).toAbsolutePath().normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new FileStorageException("File not found: " + storagePath);
        } catch (MalformedURLException ex) {
            throw new FileStorageException("File path is invalid: " + storagePath, ex);
        }
    }

    private String extractTextSafely(Path filePath) {
        try {
            String text = tika.parseToString(filePath.toFile());
            if (text == null) {
                return "";
            }
            if (text.length() > MAX_EXTRACTED_CHARS) {
                return text.substring(0, (int) MAX_EXTRACTED_CHARS);
            }
            return text;
        } catch (IOException | TikaException ex) {
            logger.warn("Text extraction failed for {}: {}", filePath.getFileName(), ex.getMessage());
            return "";
        }
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    public record StoredFile(
            String originalName,
            String contentType,
            long size,
            String storagePath,
            String extractedText
    ) {}
}
