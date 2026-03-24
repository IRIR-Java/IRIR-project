package com.chuka.irir.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * JPA entity representing a file uploaded as part of a {@link Project}.
 *
 * Supported file types include PDF, DOCX, and ZIP archives.
 * Files are stored on the filesystem (path in {@code storagePath}),
 * while metadata is stored in the database.
 *
 * <p>Apache Tika is used to extract text content from these files
 * for similarity detection and full-text search indexing.</p>
 */
@Entity
@Table(name = "project_files")
public class ProjectFile {

    public ProjectFile() {
    }

    public ProjectFile(Long id, Project project, String fileName,
                       String fileType, String storagePath, Long fileSize,
                       LocalDateTime uploadedAt) {
        this.id = id;
        this.project = project;
        this.fileName = fileName;
        this.fileType = fileType;
        this.storagePath = storagePath;
        this.fileSize = fileSize;
        this.uploadedAt = uploadedAt;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Parent project this file belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Original filename as uploaded by the student. */
    @NotBlank
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** MIME type of the file (e.g., "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"). */
    @Column(name = "file_type", length = 150)
    private String fileType;

    /** Absolute or relative path to the stored file on the server filesystem. */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /** File size in bytes. */
    @Column(name = "file_size")
    private Long fileSize;

    /** Timestamp when the file was uploaded. */
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}

