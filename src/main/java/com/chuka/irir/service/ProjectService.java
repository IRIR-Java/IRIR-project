package com.chuka.irir.service;

import com.chuka.irir.dto.ProjectCreateDto;
import com.chuka.irir.dto.SimilarityResult;
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

    private static final int KEYWORD_AUGMENT_THRESHOLD = 3;

    private final ProjectRepository projectRepository;
    private final FileStorageService fileStorageService;
    private final LuceneIndexService luceneIndexService;
    private final SimilarityDetectionService similarityDetectionService;
    private final NotificationService notificationService;
    private final KeywordExtractionService keywordExtractionService;

    public ProjectService(ProjectRepository projectRepository,
                          FileStorageService fileStorageService,
                          LuceneIndexService luceneIndexService,
                          SimilarityDetectionService similarityDetectionService,
                          NotificationService notificationService,
                          KeywordExtractionService keywordExtractionService) {
        this.projectRepository = projectRepository;
        this.fileStorageService = fileStorageService;
        this.luceneIndexService = luceneIndexService;
        this.similarityDetectionService = similarityDetectionService;
        this.notificationService = notificationService;
        this.keywordExtractionService = keywordExtractionService;
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
            String extractedText = extractedBuilder.toString();
            project.setExtractedText(extractedText);

            // Auto-augment keywords from extracted text when student provides few/none
            if (project.getKeywords().size() < KEYWORD_AUGMENT_THRESHOLD) {
                try {
                    Set<String> nlpKeywords = keywordExtractionService.extractKeywords(extractedText);
                    Set<String> merged = new LinkedHashSet<>(project.getKeywords());
                    merged.addAll(nlpKeywords);
                    project.setKeywords(merged);
                    logger.debug("NLP extracted {} keywords for project '{}'",
                            nlpKeywords.size(), project.getTitle());
                } catch (Exception e) {
                    logger.warn("Keyword extraction failed for project '{}': {}",
                            project.getTitle(), e.getMessage());
                }
            }
        }

        Project saved = projectRepository.save(project);
        logger.info("Project created by {}: {}", submittedBy.getEmail(), saved.getTitle());

        // Index the document in Lucene for future similarity checks
        if (saved.getExtractedText() != null && !saved.getExtractedText().isBlank()) {
            try {
                luceneIndexService.indexDocument(saved, saved.getExtractedText());
                logger.debug("Indexed new project [id={}] in Lucene", saved.getId());
            } catch (Exception e) {
                // Don't fail the project creation if indexing fails — log and continue
                logger.error("Failed to index project [id={}] in Lucene: {}", saved.getId(), e.getMessage());
            }
        }

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
     *
     * <p>After setting the status to SUBMITTED, the system performs an automatic
     * similarity check (UC-02) against all indexed documents. Based on the score:
     * <ul>
     *   <li>Score &lt; 0.40 → "Original Work" — index the document, no flag</li>
     *   <li>Score 0.40–0.69 → "Similar Work Detected" — warn, index, allow</li>
     *   <li>Score ≥ 0.70 → "Potential Duplicate" → set status to FLAGGED, persist reports</li>
     * </ul></p>
     *
     * @param projectId the project to submit
     * @param student   the student submitting the project
     * @return the saved project with updated status and (optionally) similarity results
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

        // ---- UC-02: Automatic Similarity Detection ----
        if (project.getExtractedText() != null && !project.getExtractedText().isBlank()) {
            try {
                SimilarityResult result = similarityDetectionService.checkSimilarity(
                        project.getExtractedText(), project.getId());

                // Persist similarity reports for all matches
                similarityDetectionService.buildSimilarityReport(result, project);

                // Apply threshold-based behavior
                if (result.getAboveThreshold()) {
                    // Score >= 0.70 → "Potential Duplicate" → hold for supervisor review
                    project.setStatus(ProjectStatus.FLAGGED);
                    logger.warn("Project [id={}, title='{}'] FLAGGED — similarity score {} ({})",
                            project.getId(), project.getTitle(),
                            result.getMaxSimilarityScore(), result.getVerdictLabel());
                    notificationService.sendFlaggedProjectNotification(
                            project.getSupervisor(), student, project, result.getMaxSimilarityScore());
                } else if (result.getMaxSimilarityScore() >= 0.40) {
                    // Score 0.40–0.69 → "Similar Work Detected" → warn, allow submission
                    logger.info("Project [id={}] has moderate similarity ({}): {}",
                            project.getId(), result.getMaxSimilarityScore(), result.getVerdictLabel());
                    notificationService.sendSimilarityWarningNotification(
                            student, project, result.getMaxSimilarityScore());
                } else {
                    // Score < 0.40 → "Original Work" → auto-approve for indexing
                    logger.info("Project [id={}] is original work (score={})",
                            project.getId(), result.getMaxSimilarityScore());
                }

                // Index the submitted document (unless it's flagged as a potential duplicate)
                if (project.getStatus() != ProjectStatus.FLAGGED) {
                    luceneIndexService.indexDocument(project, project.getExtractedText());
                }

            } catch (Exception e) {
                // Don't fail the submission if the similarity check fails
                logger.error("Similarity check failed for project [id={}]: {}",
                        project.getId(), e.getMessage(), e);
            }
        }

        Project saved = projectRepository.save(project);
        logger.info("Project submitted by {}: {} (status={})",
                student.getEmail(), saved.getTitle(), saved.getStatus());

        // Notify assigned supervisor (if any) that a new project needs review
        if (saved.getSupervisor() != null && saved.getStatus() != ProjectStatus.FLAGGED) {
            notificationService.sendProjectSubmittedNotification(saved.getSupervisor(), saved);
        }

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
