package com.chuka.irir.service;

import com.chuka.irir.model.Project;
import com.chuka.irir.model.ProjectInsight;
import com.chuka.irir.repository.ProjectInsightRepository;
import com.chuka.irir.repository.SimilarityReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AiInsightService {

    private static final Logger logger = LoggerFactory.getLogger(AiInsightService.class);
    private static final int MAX_CONTEXT_CHARS = 6000;

    private final ProjectInsightRepository insightRepository;
    private final SimilarityReportRepository similarityReportRepository;
    private final KeywordExtractionService keywordExtractionService;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public AiInsightService(ProjectInsightRepository insightRepository,
                            SimilarityReportRepository similarityReportRepository,
                            KeywordExtractionService keywordExtractionService,
                            GeminiClient geminiClient,
                            ObjectMapper objectMapper) {
        this.insightRepository = insightRepository;
        this.similarityReportRepository = similarityReportRepository;
        this.keywordExtractionService = keywordExtractionService;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    public ProjectInsight getOrGenerate(Project project) {
        Optional<ProjectInsight> existing = insightRepository.findByProjectId(project.getId());
        if (existing.isPresent()) {
            ProjectInsight insight = existing.get();
            if (shouldRefresh(project, insight)) {
                return refresh(project, insight);
            }
            return insight;
        }
        return generate(project);
    }

    private boolean shouldRefresh(Project project, ProjectInsight insight) {
        if (project.getUpdatedAt() == null || insight.getGeneratedAt() == null) {
            return false;
        }
        return project.getUpdatedAt().isAfter(insight.getGeneratedAt());
    }

    private ProjectInsight refresh(Project project, ProjectInsight insight) {
        ProjectInsight updated = buildInsight(project);
        insight.setSummary(updated.getSummary());
        insight.setStrengths(updated.getStrengths());
        insight.setGaps(updated.getGaps());
        insight.setSuggestedKeywords(updated.getSuggestedKeywords());
        insight.setNextSteps(updated.getNextSteps());
        insight.setRiskFlags(updated.getRiskFlags());
        insight.setNoveltyScore(updated.getNoveltyScore());
        insight.setSource(updated.getSource());
        insight.setGeneratedAt(updated.getGeneratedAt());
        return insightRepository.save(insight);
    }

    private ProjectInsight generate(Project project) {
        ProjectInsight insight = buildInsight(project);
        insight.setProject(project);
        return insightRepository.save(insight);
    }

    private ProjectInsight buildInsight(Project project) {
        Double maxSimilarity = similarityReportRepository.findMaxSimilarityScoreByProjectId(project.getId());
        InsightResult result = generatePayload(project, maxSimilarity);
        AiInsightPayload payload = result.payload();

        return ProjectInsight.builder()
                .summary(payload.summary())
                .strengths(joinLines(payload.strengths()))
                .gaps(joinLines(payload.gaps()))
                .suggestedKeywords(joinKeywords(payload.suggestedKeywords()))
                .nextSteps(joinLines(payload.nextSteps()))
                .riskFlags(joinLines(payload.riskFlags()))
                .noveltyScore(payload.noveltyScore())
                .source(result.source())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private InsightResult generatePayload(Project project, Double maxSimilarity) {
        String prompt = buildPrompt(project, maxSimilarity);
        Optional<String> aiResponse = geminiClient.generateContent(prompt);
        if (aiResponse.isPresent()) {
            AiInsightPayload parsed = parsePayload(aiResponse.get(), maxSimilarity);
            if (parsed != null) {
                return new InsightResult(parsed, "gemini");
            }
        }
        return new InsightResult(heuristicPayload(project, maxSimilarity), "heuristic");
    }

    private AiInsightPayload parsePayload(String response, Double maxSimilarity) {
        try {
            String cleaned = stripCodeFences(response);
            AiInsightPayload payload = objectMapper.readValue(cleaned, AiInsightPayload.class);
            if (!StringUtils.hasText(payload.summary())) {
                return null;
            }
            Double noveltyScore = payload.noveltyScore() != null ? payload.noveltyScore() : deriveNovelty(maxSimilarity);
            return new AiInsightPayload(
                    payload.summary(),
                    safeList(payload.strengths()),
                    safeList(payload.gaps()),
                    safeList(payload.suggestedKeywords()),
                    safeList(payload.nextSteps()),
                    safeList(payload.riskFlags()),
                    noveltyScore
            );
        } catch (Exception e) {
            logger.warn("Failed to parse Gemini JSON response: {}", e.getMessage());
            return null;
        }
    }

    private AiInsightPayload heuristicPayload(Project project, Double maxSimilarity) {
        String abstractText = project.getAbstractText();
        String summary = StringUtils.hasText(abstractText)
                ? truncateSentence(abstractText, 220)
                : "Project titled \"" + project.getTitle() + "\" with no abstract provided.";

        List<String> strengths = new ArrayList<>();
        if (StringUtils.hasText(project.getTitle())) {
            strengths.add("Clear project focus based on the title.");
        }
        if (StringUtils.hasText(abstractText) && abstractText.length() > 140) {
            strengths.add("Detailed abstract that outlines the work.");
        }
        if (project.getKeywords() != null && !project.getKeywords().isEmpty()) {
            strengths.add("Keywords are provided to guide discovery.");
        }

        List<String> gaps = new ArrayList<>();
        if (!StringUtils.hasText(abstractText) || abstractText.length() < 80) {
            gaps.add("Abstract is missing or too brief for reviewers.");
        }
        if (project.getKeywords() == null || project.getKeywords().isEmpty()) {
            gaps.add("Keywords are missing; add at least 3 domain terms.");
        }
        if (project.getFiles() == null || project.getFiles().isEmpty()) {
            gaps.add("No supporting files attached to the submission.");
        }

        List<String> suggestedKeywords = suggestKeywords(project);

        List<String> nextSteps = new ArrayList<>();
        nextSteps.add("Validate methodology and include measurable evaluation metrics.");
        nextSteps.add("Add references to at least 5 related works for positioning.");
        nextSteps.add("Review formatting and submission requirements before final submission.");

        List<String> riskFlags = new ArrayList<>();
        if (maxSimilarity != null) {
            if (maxSimilarity >= 0.70) {
                riskFlags.add("High similarity detected; review citations and originality.");
            } else if (maxSimilarity >= 0.40) {
                riskFlags.add("Moderate similarity detected; ensure proper attribution.");
            }
        }

        return new AiInsightPayload(
                summary,
                strengths,
                gaps,
                suggestedKeywords,
                nextSteps,
                riskFlags,
                deriveNovelty(maxSimilarity)
        );
    }

    private record InsightResult(AiInsightPayload payload, String source) {}

    private List<String> suggestKeywords(Project project) {
        Set<String> existing = project.getKeywords() != null
                ? project.getKeywords().stream()
                .filter(StringUtils::hasText)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet())
                : Set.of();

        String sourceText = StringUtils.hasText(project.getExtractedText())
                ? project.getExtractedText()
                : project.getAbstractText();

        if (!StringUtils.hasText(sourceText)) {
            return List.of();
        }

        Set<String> extracted = new LinkedHashSet<>(keywordExtractionService.extractKeywords(sourceText, 12));
        return extracted.stream()
                .filter(k -> !existing.contains(k.toLowerCase(Locale.ROOT)))
                .limit(6)
                .collect(Collectors.toList());
    }

    private String buildPrompt(Project project, Double maxSimilarity) {
        StringBuilder context = new StringBuilder();
        context.append("Title: ").append(project.getTitle()).append("\n");
        if (StringUtils.hasText(project.getAbstractText())) {
            context.append("Abstract: ").append(project.getAbstractText()).append("\n");
        }
        if (project.getKeywords() != null && !project.getKeywords().isEmpty()) {
            context.append("Keywords: ").append(String.join(", ", project.getKeywords())).append("\n");
        }
        if (StringUtils.hasText(project.getExtractedText())) {
            String extracted = project.getExtractedText();
            context.append("ExtractedText: ")
                    .append(extracted.length() > MAX_CONTEXT_CHARS ? extracted.substring(0, MAX_CONTEXT_CHARS) : extracted)
                    .append("\n");
        }
        if (maxSimilarity != null) {
            context.append("MaxSimilarityScore: ").append(String.format(Locale.ROOT, "%.3f", maxSimilarity)).append("\n");
        }

        return """
                You are an academic research assistant. Generate actionable, concise insights for a student project.
                Respond ONLY with valid JSON matching this schema:
                {
                  "summary": "1-2 sentences",
                  "strengths": ["bullet", "bullet"],
                  "gaps": ["bullet", "bullet"],
                  "suggestedKeywords": ["keyword1", "keyword2"],
                  "nextSteps": ["bullet", "bullet"],
                  "riskFlags": ["bullet"],
                  "noveltyScore": 0.0
                }
                Rules:
                - Use short, practical bullets.
                - suggestedKeywords must be 3-6 items.
                - noveltyScore is 0-1 (higher is more novel). If no similarity score is provided, estimate.
                - Do not include markdown, only JSON.
                Project Context:
                """ + context;
    }

    private Double deriveNovelty(Double maxSimilarity) {
        if (maxSimilarity == null) {
            return null;
        }
        double score = 1.0 - maxSimilarity;
        return Math.max(0.0, Math.min(1.0, score));
    }

    private String joinLines(List<String> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining("\n"));
    }

    private String joinKeywords(List<String> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private List<String> safeList(List<String> items) {
        return items == null ? List.of() : items;
    }

    private String stripCodeFences(String input) {
        String trimmed = input.trim();
        if (trimmed.startsWith("```")) {
            int first = trimmed.indexOf('\n');
            int last = trimmed.lastIndexOf("```");
            if (first > -1 && last > first) {
                return trimmed.substring(first + 1, last).trim();
            }
        }
        return trimmed;
    }

    private String truncateSentence(String text, int maxChars) {
        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxChars) {
            return cleaned;
        }
        int period = cleaned.indexOf('.');
        if (period > 60 && period < maxChars) {
            return cleaned.substring(0, period + 1);
        }
        return cleaned.substring(0, maxChars).trim() + "...";
    }
}
