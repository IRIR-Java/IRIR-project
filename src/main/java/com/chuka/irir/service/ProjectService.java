package com.chuka.irir.service;

import com.chuka.irir.dto.ProjectCreateDto;
import com.chuka.irir.exception.FileStorageException;
import com.chuka.irir.exception.ResourceNotFoundException;
import com.chuka.irir.model.Project;
import com.chuka.irir.model.ProjectFile;
import com.chuka.irir.model.ProjectStatus;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service layer for project submission and collaborator recommendations.
 */
@Service
@Transactional
public class ProjectService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final FileStorageService fileStorageService;

    public ProjectService(ProjectRepository projectRepository,
                          FileStorageService fileStorageService) {
        this.projectRepository = projectRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Creates a new project for a student, stores uploaded files, and extracts text.
     */
    public Project createProject(ProjectCreateDto dto, User submittedBy) {
        List<MultipartFile> files = normalizeFiles(dto.getFiles());
        if (files.isEmpty()) {
            throw new FileStorageException("At least one file is required.");
        }

        Project project = new Project();
        project.setTitle(dto.getTitle().trim());
        project.setAbstractText(dto.getAbstractText());
        project.setSubmittedBy(submittedBy);
        project.setStatus(ProjectStatus.DRAFT);
        project.setAcademicYear(dto.getAcademicYear() != null ? dto.getAcademicYear() : Year.now().getValue());
        project.setKeywords(parseKeywords(dto.getKeywords()));

        StringBuilder extractedBuilder = new StringBuilder();

        for (MultipartFile file : files) {
            FileStorageService.StoredFile storedFile = fileStorageService.store(file);

            ProjectFile projectFile = ProjectFile.builder()
                    .fileName(storedFile.originalName())
                    .fileType(storedFile.contentType())
                    .fileSize(storedFile.size())
                    .storagePath(storedFile.storagePath())
                    .build();

            project.addFile(projectFile);

            if (storedFile.extractedText() != null && !storedFile.extractedText().isBlank()) {
                if (extractedBuilder.length() > 0) {
                    extractedBuilder.append("\n\n---\n\n");
                }
                extractedBuilder.append(storedFile.extractedText());
            }
        }

        if (extractedBuilder.length() > 0) {
            project.setExtractedText(extractedBuilder.toString());
        }

        Project saved = projectRepository.save(project);
        logger.info("Project created by {}: {}", submittedBy.getEmail(), saved.getTitle());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Project> getProjectsForStudent(User student) {
        return projectRepository.findBySubmittedByOrderByCreatedAtDesc(student);
    }

    @Transactional(readOnly = true)
    public Project getProjectForStudent(Long projectId, User student) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        if (!project.getSubmittedBy().getId().equals(student.getId())) {
            throw new ResourceNotFoundException("Project", "id", projectId);
        }
        // Initialize lazy collections before leaving the transaction
        project.getFiles().size();
        return project;
    }

    /**
     * Updates a draft project before final submission.
     */
    public Project updateDraft(ProjectCreateDto dto, Long projectId, User student) {
        Project project = getProjectForStudent(projectId, student);
        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new FileStorageException("Only draft projects can be edited.");
        }

        project.setTitle(dto.getTitle().trim());
        project.setAbstractText(dto.getAbstractText());
        project.setAcademicYear(dto.getAcademicYear() != null ? dto.getAcademicYear() : Year.now().getValue());
        project.setKeywords(parseKeywords(dto.getKeywords()));

        List<MultipartFile> files = normalizeFiles(dto.getFiles());
        if (!files.isEmpty()) {
            StringBuilder extractedBuilder = new StringBuilder(
                    project.getExtractedText() != null ? project.getExtractedText() : ""
            );
            for (MultipartFile file : files) {
                FileStorageService.StoredFile storedFile = fileStorageService.store(file);

                ProjectFile projectFile = ProjectFile.builder()
                        .fileName(storedFile.originalName())
                        .fileType(storedFile.contentType())
                        .fileSize(storedFile.size())
                        .storagePath(storedFile.storagePath())
                        .build();

                project.addFile(projectFile);

                if (storedFile.extractedText() != null && !storedFile.extractedText().isBlank()) {
                    if (extractedBuilder.length() > 0) {
                        extractedBuilder.append("\n\n---\n\n");
                    }
                    extractedBuilder.append(storedFile.extractedText());
                }
            }
            project.setExtractedText(extractedBuilder.toString());
        }

        Project saved = projectRepository.save(project);
        logger.info("Project draft updated by {}: {}", student.getEmail(), saved.getTitle());
        return saved;
    }

    /**
     * Final submission of a draft project.
     */
    public Project submitProject(Long projectId, User student) {
        Project project = getProjectForStudent(projectId, student);
        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new FileStorageException("Only draft projects can be submitted.");
        }
        if (project.getFiles() == null || project.getFiles().isEmpty()) {
            throw new FileStorageException("At least one file is required before submission.");
        }
        project.setStatus(ProjectStatus.SUBMITTED);
        project.setSubmittedAt(LocalDateTime.now());
        Project saved = projectRepository.save(project);
        logger.info("Project submitted by {}: {}", student.getEmail(), saved.getTitle());
        return saved;
    }

    /**
     * Finds collaborator recommendations based on keyword overlap.
     */
    @Transactional(readOnly = true)
    public List<Project> recommendCollaborators(Project baseProject, int limit) {
        Set<String> baseKeywords = normalizeKeywordSet(baseProject.getKeywords());
        if (baseKeywords.isEmpty()) {
            return List.of();
        }

        List<String> keywordList = new ArrayList<>(baseKeywords);
        List<Project> candidates = projectRepository.findByKeywordsAndNotSubmittedBy(keywordList, baseProject.getSubmittedBy());

        Map<Project, Integer> scores = new HashMap<>();
        for (Project candidate : candidates) {
            if (candidate.getId().equals(baseProject.getId())) {
                continue;
            }
            if (candidate.getStatus() == ProjectStatus.DRAFT) {
                continue;
            }
            int score = overlapScore(baseKeywords, candidate.getKeywords());
            if (score > 0) {
                scores.put(candidate, score);
            }
        }

        return scores.entrySet().stream()
                .sorted((a, b) -> {
                    int cmp = Integer.compare(b.getValue(), a.getValue());
                    if (cmp != 0) {
                        return cmp;
                    }
                    LocalDateTime aTime = a.getKey().getSubmittedAt();
                    LocalDateTime bTime = b.getKey().getSubmittedAt();
                    if (aTime == null && bTime == null) {
                        return 0;
                    }
                    if (aTime == null) {
                        return 1;
                    }
                    if (bTime == null) {
                        return -1;
                    }
                    return bTime.compareTo(aTime);
                })
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<MultipartFile> normalizeFiles(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }
        return Arrays.stream(files)
                .filter(Objects::nonNull)
                .filter(file -> !file.isEmpty())
                .collect(Collectors.toList());
    }

    private Set<String> parseKeywords(String keywordsCsv) {
        if (keywordsCsv == null || keywordsCsv.isBlank()) {
            return new LinkedHashSet<>();
        }
        String[] tokens = keywordsCsv.split("[,;]");
        Set<String> keywords = new LinkedHashSet<>();
        for (String token : tokens) {
            String cleaned = token.trim().toLowerCase();
            if (cleaned.length() >= 2) {
                keywords.add(cleaned);
            }
        }
        return keywords;
    }

    private Set<String> normalizeKeywordSet(Set<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return Set.of();
        }
        return keywords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private int overlapScore(Set<String> baseKeywords, Set<String> candidateKeywords) {
        if (candidateKeywords == null || candidateKeywords.isEmpty()) {
            return 0;
        }
        int score = 0;
        for (String keyword : candidateKeywords) {
            if (keyword == null) {
                continue;
            }
            if (baseKeywords.contains(keyword.trim().toLowerCase())) {
                score++;
            }
        }
        return score;
    }
}
