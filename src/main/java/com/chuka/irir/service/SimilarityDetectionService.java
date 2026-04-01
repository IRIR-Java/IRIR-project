package com.chuka.irir.service;

import com.chuka.irir.dto.SimilarityResult;
import com.chuka.irir.dto.SimilarityResult.MatchedProject;
import com.chuka.irir.model.Project;
import com.chuka.irir.model.SimilarityReport;
import com.chuka.irir.repository.ProjectRepository;
import com.chuka.irir.repository.SimilarityReportRepository;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that performs semantic similarity detection between research projects (UC-02).
 *
 * <h3>How It Works — Step by Step</h3>
 * <ol>
 *   <li><b>Candidate Retrieval</b>: Uses Lucene's {@link MoreLikeThis} to find the top N
 *       most similar documents in the index based on term frequency overlap.</li>
 *   <li><b>TF-IDF Vectorization</b>: For the new document and each candidate, builds a
 *       term-frequency (TF) vector by tokenizing the text with {@code StandardAnalyzer}.
 *       TF-IDF weighting is applied using Document Frequency from the Lucene index.</li>
 *   <li><b>Cosine Similarity</b>: Computes the cosine of the angle between the two TF-IDF
 *       vectors: {@code cos(θ) = (A · B) / (||A|| × ||B||)}</li>
 *   <li><b>Threshold Classification</b>:
 *       <ul>
 *         <li>{@code < 0.40} → "Original Work"      — auto-approve for indexing</li>
 *         <li>{@code 0.40 – 0.69} → "Similar Work Detected" — warn student, allow submission</li>
 *         <li>{@code ≥ 0.70} → "Potential Duplicate" — hold for supervisor review</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>Cosine Similarity Example</h3>
 * <pre>
 * Document A: "machine learning algorithm"  → TF vector: {machine:1, learning:1, algorithm:1}
 * Document B: "deep learning algorithm"     → TF vector: {deep:1, learning:1, algorithm:1}
 *
 * Shared terms: {learning, algorithm}
 * Dot product  = (1×1) + (1×1)         = 2
 * ||A||        = √(1² + 1² + 1²)       = √3
 * ||B||        = √(1² + 1² + 1²)       = √3
 * Cosine Sim   = 2 / (√3 × √3) = 2/3  ≈ 0.667
 * </pre>
 *
 * @see LuceneIndexService
 * @see SimilarityResult
 */
@Service
public class SimilarityDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(SimilarityDetectionService.class);

    private final LuceneIndexService luceneIndexService;
    private final ProjectRepository projectRepository;
    private final SimilarityReportRepository similarityReportRepository;

    /** Score >= this value → "Potential Duplicate" → hold for supervisor review. */
    @Value("${app.similarity.threshold:0.70}")
    private double flagThreshold;

    /** Score >= this value (and < flagThreshold) → "Similar Work Detected" → warn student. */
    @Value("${app.similarity.warning-threshold:0.40}")
    private double warningThreshold;

    /** Maximum number of candidate matches to retrieve from the Lucene MoreLikeThis query. */
    @Value("${app.similarity.max-results:10}")
    private int maxResults;

    public SimilarityDetectionService(LuceneIndexService luceneIndexService,
                                      ProjectRepository projectRepository,
                                      SimilarityReportRepository similarityReportRepository) {
        this.luceneIndexService = luceneIndexService;
        this.projectRepository = projectRepository;
        this.similarityReportRepository = similarityReportRepository;
    }

    // ==================== Main Similarity Check ====================

    /**
     * Performs a full similarity check of a new document against all indexed projects.
     *
     * <p><b>Algorithm</b>:</p>
     * <ol>
     *   <li>Open a {@link DirectoryReader} on the Lucene index</li>
     *   <li>Build a {@link MoreLikeThis} query from the new document's text</li>
     *   <li>Execute the query to retrieve the top {@code maxResults} candidates</li>
     *   <li>For each candidate, compute the actual Cosine Similarity from TF-IDF vectors</li>
     *   <li>Classify the overall score and return a {@link SimilarityResult}</li>
     * </ol>
     *
     * @param newDocumentText the full extracted text of the new document
     * @param newProjectId    the database ID of the new project (excluded from results)
     * @return a {@link SimilarityResult} containing the overall score and matched projects
     */
    public SimilarityResult checkSimilarity(String newDocumentText, Long newProjectId) {
        if (newDocumentText == null || newDocumentText.isBlank()) {
            logger.warn("Similarity check skipped for project [id={}]: no extracted text", newProjectId);
            return buildEmptyResult();
        }

        try {
            // Ensure we can read the index
            if (luceneIndexService.getDirectory() == null) {
                logger.warn("Lucene directory not initialized, skipping similarity check");
                return buildEmptyResult();
            }

            IndexReader reader;
            try {
                reader = DirectoryReader.open(luceneIndexService.getDirectory());
            } catch (IOException e) {
                logger.warn("Cannot open Lucene index (may be empty): {}", e.getMessage());
                return buildEmptyResult();
            }

            // Use try-with-resources to ensure the reader is always closed
            try (reader) {
                if (reader.numDocs() == 0) {
                    logger.info("Lucene index is empty, no similarity check needed");
                    return buildEmptyResult();
                }

                IndexSearcher searcher = new IndexSearcher(reader);
                Analyzer analyzer = luceneIndexService.getAnalyzer();

                // ---- Step 1: Use MoreLikeThis to find candidate similar documents ----
                MoreLikeThis mlt = new MoreLikeThis(reader);
                mlt.setAnalyzer(analyzer);
                mlt.setFieldNames(new String[]{LuceneIndexService.FIELD_FULL_CONTENT});
                mlt.setMinTermFreq(1);        // A term must appear at least 1 time in the source
                mlt.setMinDocFreq(1);         // A term must appear in at least 1 document
                mlt.setMinWordLen(3);         // Ignore very short terms
                mlt.setMaxQueryTerms(100);    // Use up to 100 terms for the query

                // Build the MoreLikeThis query from the new document's text
                Query mltQuery = mlt.like(LuceneIndexService.FIELD_FULL_CONTENT,
                        new StringReader(newDocumentText));

                // Execute the query — retrieve top N candidates
                TopDocs topDocs = searcher.search(mltQuery, maxResults + 5); // Fetch extras in case we filter some

                // ---- Step 2: Compute actual Cosine Similarity for each candidate ----
                // Tokenize the new document into a TF map
                Map<String, Integer> newDocTermFreqs = tokenizeToTermFrequencyMap(newDocumentText, analyzer);

                List<MatchedProject> matchedProjects = new ArrayList<>();
                double maxScore = 0.0;

                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document luceneDoc = searcher.storedFields().document(scoreDoc.doc);
                    String candidateProjectIdStr = luceneDoc.get(LuceneIndexService.FIELD_PROJECT_ID);

                    if (candidateProjectIdStr == null) {
                        continue;
                    }

                    long candidateProjectId;
                    try {
                        candidateProjectId = Long.parseLong(candidateProjectIdStr);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    // Skip the project being checked against itself
                    if (candidateProjectId == newProjectId) {
                        continue;
                    }

                    // Retrieve the full combined text stored in the index for TF-IDF comparison
                    String candidateText = luceneDoc.get(LuceneIndexService.FIELD_FULL_CONTENT);
                    if (candidateText == null || candidateText.isBlank()) {
                        // Fallback: reconstruct from individual stored fields
                        String candidateTitle = luceneDoc.get(LuceneIndexService.FIELD_TITLE);
                        String candidateAbstract = luceneDoc.get(LuceneIndexService.FIELD_ABSTRACT);
                        String candidateKeywords = luceneDoc.get(LuceneIndexService.FIELD_KEYWORDS);
                        candidateText = String.join(" ",
                                candidateTitle != null ? candidateTitle : "",
                                candidateAbstract != null ? candidateAbstract : "",
                                candidateKeywords != null ? candidateKeywords : "");
                    }

                    // Tokenize the candidate document into a TF map
                    Map<String, Integer> candidateTermFreqs = tokenizeToTermFrequencyMap(candidateText, analyzer);

                    // Compute Cosine Similarity between the TF-IDF vectors
                    double cosineSim = computeCosineSimilarity(
                            newDocTermFreqs, candidateTermFreqs, reader);

                    if (cosineSim > 0.0) {
                        // Look up the project from DB for title and author info
                        String title = "Unknown";
                        String author = "Unknown";
                        Optional<Project> candidateProject = projectRepository.findById(candidateProjectId);
                        if (candidateProject.isPresent()) {
                            Project cp = candidateProject.get();
                            title = cp.getTitle();
                            if (cp.getSubmittedBy() != null) {
                                author = cp.getSubmittedBy().getFirstName() + " " +
                                         cp.getSubmittedBy().getLastName();
                            }
                        }

                        matchedProjects.add(MatchedProject.builder()
                                .projectId(candidateProjectId)
                                .title(title)
                                .author(author)
                                .similarityScore(Math.round(cosineSim * 10000.0) / 10000.0) // 4 decimal places
                                .projectUrl("/student/projects/" + candidateProjectId)
                                .build());

                        if (cosineSim > maxScore) {
                            maxScore = cosineSim;
                        }
                    }
                }

                // Sort matched projects by score descending
                matchedProjects.sort((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()));

                // Limit to maxResults
                if (matchedProjects.size() > maxResults) {
                    matchedProjects = matchedProjects.subList(0, maxResults);
                }

                // ---- Step 3: Classify the result ----
                String verdict = classifyScore(maxScore);
                boolean aboveThreshold = maxScore >= flagThreshold;

                SimilarityResult result = SimilarityResult.builder()
                        .maxSimilarityScore(Math.round(maxScore * 10000.0) / 10000.0)
                        .aboveThreshold(aboveThreshold)
                        .verdictLabel(verdict)
                        .matchedProjects(matchedProjects)
                        .build();

                logger.info("Similarity check for project [id={}]: score={}, verdict='{}', matches={}",
                        newProjectId, result.getMaxSimilarityScore(), verdict, matchedProjects.size());

                return result;
            }

        } catch (IOException e) {
            logger.error("Similarity check failed for project [id={}]: {}",
                    newProjectId, e.getMessage(), e);
            return buildEmptyResult();
        }
    }

    // ==================== Threshold Methods ====================

    /**
     * Checks whether the given similarity score is at or above the flagging threshold.
     *
     * @param score the cosine similarity score (0.0 – 1.0)
     * @return true if the score indicates a potential duplicate (≥ threshold)
     */
    public boolean isSimilarityAboveThreshold(double score) {
        return score >= flagThreshold;
    }

    /**
     * Classifies a similarity score into a human-readable verdict label.
     *
     * @param score the cosine similarity score (0.0 – 1.0)
     * @return "Original Work", "Similar Work Detected", or "Potential Duplicate"
     */
    public String classifyScore(double score) {
        if (score >= flagThreshold) {
            return "Potential Duplicate";
        } else if (score >= warningThreshold) {
            return "Similar Work Detected";
        } else {
            return "Original Work";
        }
    }

    // ==================== Report Persistence ====================

    /**
     * Persists similarity comparison results to the {@code similarity_reports} table.
     *
     * <p>Creates a {@link SimilarityReport} entity for each matched project, storing
     * the similarity score and whether the match was flagged (score ≥ threshold).</p>
     *
     * @param result  the similarity check result containing matched projects
     * @param project the source project that was checked
     */
    @Transactional
    public void buildSimilarityReport(SimilarityResult result, Project project) {
        if (result.getMatchedProjects() == null || result.getMatchedProjects().isEmpty()) {
            logger.debug("No matched projects to save for project [id={}]", project.getId());
            return;
        }

        // Batch-load all target projects in a single query (avoids N+1)
        Set<Long> targetIds = result.getMatchedProjects().stream()
                .map(MatchedProject::getProjectId)
                .collect(Collectors.toSet());
        Map<Long, Project> targetProjectMap = projectRepository.findAllById(targetIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        for (MatchedProject match : result.getMatchedProjects()) {
            Project targetProject = targetProjectMap.get(match.getProjectId());
            if (targetProject == null) {
                logger.warn("Matched project [id={}] not found in DB, skipping report", match.getProjectId());
                continue;
            }

            SimilarityReport report = SimilarityReport.builder()
                    .sourceProject(project)
                    .targetProject(targetProject)
                    .similarityScore(match.getSimilarityScore())
                    .flagged(isSimilarityAboveThreshold(match.getSimilarityScore()))
                    .build();

            similarityReportRepository.save(report);
            logger.debug("Saved similarity report: project [{}] vs [{}] = {}",
                    project.getId(), match.getProjectId(), match.getSimilarityScore());
        }

        logger.info("Persisted {} similarity reports for project [id={}]",
                result.getMatchedProjects().size(), project.getId());
    }

    // ==================== TF-IDF & Cosine Similarity ====================

    /**
     * Computes the Cosine Similarity between two documents represented as term-frequency maps.
     *
     * <h3>Algorithm (with TF-IDF weighting)</h3>
     * <pre>
     * For each unique term across both documents:
     *   1. TF(term, doc)  = count of term in document
     *   2. IDF(term)      = log(totalDocs / (docFreq(term) + 1)) + 1
     *   3. TF-IDF(term)   = TF × IDF
     *
     * Cosine Similarity = Σ(A_i × B_i) / (√Σ(A_i²) × √Σ(B_i²))
     *
     * Where A_i and B_i are the TF-IDF weights for term i in documents A and B.
     * </pre>
     *
     * @param termFreqsA term-frequency map for document A
     * @param termFreqsB term-frequency map for document B
     * @param reader     the IndexReader for accessing document frequency stats
     * @return cosine similarity score between 0.0 (no similarity) and 1.0 (identical)
     */
    private double computeCosineSimilarity(Map<String, Integer> termFreqsA,
                                           Map<String, Integer> termFreqsB,
                                           IndexReader reader) throws IOException {
        // Collect all unique terms from both documents
        Set<String> allTerms = new HashSet<>();
        allTerms.addAll(termFreqsA.keySet());
        allTerms.addAll(termFreqsB.keySet());

        if (allTerms.isEmpty()) {
            return 0.0;
        }

        int totalDocs = reader.numDocs();
        double dotProduct = 0.0;
        double magnitudeA = 0.0;
        double magnitudeB = 0.0;

        for (String term : allTerms) {
            int tfA = termFreqsA.getOrDefault(term, 0);
            int tfB = termFreqsB.getOrDefault(term, 0);

            // Calculate IDF: log(totalDocs / (docFreq + 1)) + 1
            // Using the fullContent field to get document frequency
            org.apache.lucene.index.Term luceneTerm =
                    new org.apache.lucene.index.Term(LuceneIndexService.FIELD_FULL_CONTENT, term);
            int docFreq = reader.docFreq(luceneTerm);
            double idf = Math.log((double) totalDocs / (docFreq + 1)) + 1.0;

            // TF-IDF weights
            double weightA = tfA * idf;
            double weightB = tfB * idf;

            dotProduct += weightA * weightB;
            magnitudeA += weightA * weightA;
            magnitudeB += weightB * weightB;
        }

        // Avoid division by zero
        if (magnitudeA == 0.0 || magnitudeB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(magnitudeA) * Math.sqrt(magnitudeB));
    }

    /**
     * Tokenizes a text string into a term-frequency map using the provided Lucene Analyzer.
     *
     * <p>Each term is lowercased and counted. For example, "machine learning machine"
     * produces: {machine: 2, learning: 1}.</p>
     *
     * @param text     the raw text to tokenize
     * @param analyzer the Lucene Analyzer (StandardAnalyzer) for consistent tokenization
     * @return a map of term → frequency count
     */
    private Map<String, Integer> tokenizeToTermFrequencyMap(String text, Analyzer analyzer)
            throws IOException {
        Map<String, Integer> termFreqs = new HashMap<>();

        if (text == null || text.isBlank()) {
            return termFreqs;
        }

        try (TokenStream tokenStream = analyzer.tokenStream(
                LuceneIndexService.FIELD_FULL_CONTENT, new StringReader(text))) {

            CharTermAttribute charTermAttr = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();

            while (tokenStream.incrementToken()) {
                String term = charTermAttr.toString();
                termFreqs.merge(term, 1, Integer::sum);
            }

            tokenStream.end();
        }

        return termFreqs;
    }

    // ==================== Helpers ====================

    /**
     * Builds an empty SimilarityResult indicating no similarity was found.
     * Used when the index is empty or the document has no extractable text.
     *
     * @return a SimilarityResult with score 0.0 and "Original Work" verdict
     */
    private SimilarityResult buildEmptyResult() {
        return SimilarityResult.builder()
                .maxSimilarityScore(0.0)
                .aboveThreshold(false)
                .verdictLabel("Original Work")
                .matchedProjects(new ArrayList<>())
                .build();
    }
}
