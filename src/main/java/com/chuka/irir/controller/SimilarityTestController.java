package com.chuka.irir.controller;

import com.chuka.irir.dto.SimilarityResult;
import com.chuka.irir.model.SimilarityReport;
import com.chuka.irir.repository.SimilarityReportRepository;
import com.chuka.irir.service.LuceneIndexService;
import com.chuka.irir.service.SimilarityDetectionService;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dev-only REST controller for testing and exploring the similarity detection engine.
 *
 * <p>Provides endpoints to:</p>
 * <ul>
 *   <li>Run a similarity check with arbitrary text (without submitting a project)</li>
 *   <li>View persisted similarity reports for a project</li>
 *   <li>Check Lucene index statistics</li>
 * </ul>
 *
 * <p><b>Access:</b> Restricted to ADMIN role via SecurityConfig.</p>
 */
@RestController
@RequestMapping("/api/dev/similarity")
@Transactional(readOnly = true)
public class SimilarityTestController {

    private static final Logger logger = LoggerFactory.getLogger(SimilarityTestController.class);

    private final SimilarityDetectionService similarityDetectionService;
    private final SimilarityReportRepository similarityReportRepository;
    private final LuceneIndexService luceneIndexService;

    public SimilarityTestController(SimilarityDetectionService similarityDetectionService,
                                     SimilarityReportRepository similarityReportRepository,
                                     LuceneIndexService luceneIndexService) {
        this.similarityDetectionService = similarityDetectionService;
        this.similarityReportRepository = similarityReportRepository;
        this.luceneIndexService = luceneIndexService;
    }

    /**
     * Run a similarity check with arbitrary text against the Lucene index.
     *
     * <p>This allows testing the similarity engine without going through the
     * full project submission flow.</p>
     *
     * @param request body containing "text" and optional "projectId" (default 0 — matches all)
     * @return SimilarityResult JSON with scores, verdict, and matched projects
     */
    @PostMapping("/test")
    public ResponseEntity<SimilarityResult> testSimilarity(@RequestBody Map<String, Object> request) {
        String text = (String) request.getOrDefault("text", "");
        Long projectId = request.containsKey("projectId")
                ? Long.valueOf(request.get("projectId").toString())
                : 0L;

        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(
                    SimilarityResult.builder()
                            .maxSimilarityScore(0.0)
                            .aboveThreshold(false)
                            .verdictLabel("No text provided")
                            .build());
        }

        logger.info("Dev similarity test: text length={}, excludeProjectId={}", text.length(), projectId);
        SimilarityResult result = similarityDetectionService.checkSimilarity(text, projectId);
        return ResponseEntity.ok(result);
    }

    /**
     * Get all persisted similarity reports for a specific project.
     *
     * @param projectId the source project ID
     * @return list of similarity reports as JSON
     */
    @GetMapping("/reports/{projectId}")
    public ResponseEntity<List<Map<String, Object>>> getReports(@PathVariable Long projectId) {
        List<SimilarityReport> reports =
                similarityReportRepository.findBySourceProjectIdOrderBySimilarityScoreDesc(projectId);

        List<Map<String, Object>> result = reports.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("sourceProjectId", r.getSourceProject().getId());
            map.put("targetProjectId", r.getTargetProject().getId());
            map.put("targetProjectTitle", r.getTargetProject().getTitle());
            map.put("similarityScore", r.getSimilarityScore());
            map.put("flagged", r.isFlagged());
            map.put("generatedAt", r.getGeneratedAt());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Get Lucene index statistics — useful for verifying documents are indexed.
     *
     * @return JSON with document count and index directory path
     */
    @GetMapping("/index-stats")
    public ResponseEntity<Map<String, Object>> getIndexStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            if (luceneIndexService.getDirectory() != null) {
                IndexReader reader = DirectoryReader.open(luceneIndexService.getDirectory());
                stats.put("documentCount", reader.numDocs());
                stats.put("maxDoc", reader.maxDoc());
                stats.put("hasDeletions", reader.hasDeletions());
                reader.close();
            } else {
                stats.put("documentCount", 0);
                stats.put("error", "Lucene directory not initialized");
            }
        } catch (IOException e) {
            stats.put("documentCount", 0);
            stats.put("error", e.getMessage());
        }

        stats.put("status", "ok");
        return ResponseEntity.ok(stats);
    }
}
