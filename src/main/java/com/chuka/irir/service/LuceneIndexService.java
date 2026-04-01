package com.chuka.irir.service;

import com.chuka.irir.model.Project;
import com.chuka.irir.model.ProjectStatus;
import com.chuka.irir.repository.ProjectRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Service responsible for managing the Apache Lucene full-text index of research projects.
 *
 * <p>This service maintains an on-disk Lucene index (via {@link FSDirectory}) that mirrors
 * the extracted text content of all approved/submitted projects in the database.
 * The index is used by {@link SimilarityDetectionService} to find semantically similar
 * documents using TF-IDF vectorization and cosine similarity.</p>
 *
 * <h3>Indexed Fields per Document</h3>
 * <ul>
 *   <li><b>projectId</b>   — stored, not analyzed (used for lookup and deletion)</li>
 *   <li><b>title</b>       — analyzed with StandardAnalyzer (full-text searchable)</li>
 *   <li><b>abstractText</b>— analyzed with StandardAnalyzer</li>
 *   <li><b>keywords</b>    — analyzed with StandardAnalyzer</li>
 *   <li><b>extractedText</b>— analyzed with StandardAnalyzer (Tika-extracted content)</li>
 *   <li><b>fullContent</b> — analyzed combined field (title + abstract + keywords + text)
 *                            used as the primary field for MoreLikeThis queries</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@code @PostConstruct}: Rebuilds the entire index from the database on startup</li>
 *   <li>{@code indexDocument()}: Called when a project is submitted or updated</li>
 *   <li>{@code removeFromIndex()}: Called when a project is deleted</li>
 *   <li>{@code @PreDestroy}: Closes the IndexWriter and Directory cleanly</li>
 * </ol>
 *
 * @see SimilarityDetectionService
 */
@Service
public class LuceneIndexService {

    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexService.class);

    /** Lucene field name constants — used by both this service and SimilarityDetectionService. */
    public static final String FIELD_PROJECT_ID   = "projectId";
    public static final String FIELD_TITLE        = "title";
    public static final String FIELD_ABSTRACT     = "abstractText";
    public static final String FIELD_KEYWORDS     = "keywords";
    public static final String FIELD_EXTRACTED     = "extractedText";
    public static final String FIELD_FULL_CONTENT = "fullContent";

    private final ProjectRepository projectRepository;
    private final StandardAnalyzer analyzer;
    private Directory directory;
    private IndexWriter indexWriter;

    /** Path to the on-disk Lucene index directory, injected from application.properties. */
    @Value("${app.lucene.index-dir:lucene-index}")
    private String indexDir;

    public LuceneIndexService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
        this.analyzer = new StandardAnalyzer();
    }

    // ==================== Lifecycle ====================

    /**
     * Rebuilds the Lucene index on application startup by reading all non-DRAFT projects
     * that have extracted text from the database.
     *
     * <p>The index is created fresh (OpenMode.CREATE) to ensure consistency with the DB state.
     * This operation is idempotent — safe to run on every startup.</p>
     */
    @PostConstruct
    public void indexAllExistingProjects() {
        try {
            Path indexPath = Paths.get(indexDir);
            Files.createDirectories(indexPath);

            // Remove stale lock file from previous unclean shutdown.
            // NOTE: This is safe for single-instance deployments because we rebuild the index
            // with OpenMode.CREATE on startup. For multi-instance deployments, remove this
            // and use a shared index or external lock coordination instead.
            Path lockFile = indexPath.resolve("write.lock");
            if (Files.exists(lockFile)) {
                Files.delete(lockFile);
                logger.info("Removed stale Lucene lock file: {}", lockFile);
            }

            directory = FSDirectory.open(indexPath);

            // Create a fresh index on startup to ensure consistency with the database
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            indexWriter = new IndexWriter(directory, config);

            // Fetch all non-DRAFT projects with extracted text
            List<Project> projects = projectRepository.findAll().stream()
                    .filter(p -> p.getStatus() != ProjectStatus.DRAFT)
                    .filter(p -> p.getExtractedText() != null && !p.getExtractedText().isBlank())
                    .toList();

            int indexed = 0;
            for (Project project : projects) {
                Document doc = buildLuceneDocument(project, project.getExtractedText());
                indexWriter.addDocument(doc);
                indexed++;
            }

            indexWriter.commit();
            logger.info("Lucene index built successfully at [{}]: {} documents indexed", indexDir, indexed);

            // Re-open the writer in CREATE_OR_APPEND mode for subsequent operations
            indexWriter.close();
            IndexWriterConfig appendConfig = new IndexWriterConfig(analyzer);
            appendConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            indexWriter = new IndexWriter(directory, appendConfig);

        } catch (IOException e) {
            logger.error("Failed to build Lucene index on startup: {}", e.getMessage(), e);
            throw new RuntimeException("Lucene index initialization failed", e);
        }
    }

    /**
     * Closes the IndexWriter and FSDirectory cleanly on application shutdown.
     */
    @PreDestroy
    public void shutdown() {
        try {
            if (indexWriter != null && indexWriter.isOpen()) {
                indexWriter.close();
            }
            if (directory != null) {
                directory.close();
            }
            logger.info("Lucene index closed successfully");
        } catch (IOException e) {
            logger.error("Error closing Lucene index: {}", e.getMessage(), e);
        }
    }

    // ==================== Index Operations ====================

    /**
     * Adds or updates a project document in the Lucene index.
     *
     * <p>Uses an upsert pattern: first deletes any existing document with the same
     * projectId, then adds the new document. This ensures the index stays in sync
     * with the database even if the project's text content has changed.</p>
     *
     * @param project       the project entity to index
     * @param extractedText the Tika-extracted full text content of the project's files
     * @throws RuntimeException if the index write operation fails
     */
    public void indexDocument(Project project, String extractedText) {
        try {
            // Delete existing document with the same projectId (upsert pattern)
            indexWriter.deleteDocuments(new Term(FIELD_PROJECT_ID, String.valueOf(project.getId())));

            // Build and add the new document
            Document doc = buildLuceneDocument(project, extractedText);
            indexWriter.addDocument(doc);
            indexWriter.commit();

            logger.debug("Indexed project [id={}, title='{}'] in Lucene", project.getId(), project.getTitle());
        } catch (IOException e) {
            logger.error("Failed to index project [id={}]: {}", project.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to index document in Lucene", e);
        }
    }

    /**
     * Removes a project document from the Lucene index.
     *
     * <p>Called when a project is deleted from the system to keep the index consistent.</p>
     *
     * @param projectId the database ID of the project to remove from the index
     * @throws RuntimeException if the index delete operation fails
     */
    public void removeFromIndex(Long projectId) {
        try {
            indexWriter.deleteDocuments(new Term(FIELD_PROJECT_ID, String.valueOf(projectId)));
            indexWriter.commit();
            logger.debug("Removed project [id={}] from Lucene index", projectId);
        } catch (IOException e) {
            logger.error("Failed to remove project [id={}] from index: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to remove document from Lucene index", e);
        }
    }

    // ==================== Accessors ====================

    /**
     * Returns the Lucene FSDirectory used by this index.
     * Used by {@link SimilarityDetectionService} to open a DirectoryReader for search.
     *
     * @return the Lucene Directory backing this index
     */
    public Directory getDirectory() {
        return directory;
    }

    /**
     * Returns the StandardAnalyzer used for this index.
     * Shared with {@link SimilarityDetectionService} to ensure consistent tokenization.
     *
     * @return the StandardAnalyzer instance
     */
    public StandardAnalyzer getAnalyzer() {
        return analyzer;
    }

    // ==================== Internal ====================

    /**
     * Builds a Lucene {@link Document} from a project entity and its extracted text.
     *
     * <p>The document contains both stored fields (for retrieval) and analyzed fields
     * (for full-text search and TF-IDF vectorization).</p>
     *
     * @param project       the project entity
     * @param extractedText the Tika-extracted text content
     * @return a Lucene Document ready for indexing
     */
    private Document buildLuceneDocument(Project project, String extractedText) {
        Document doc = new Document();

        // Stored, non-analyzed field — used for lookup and deletion
        doc.add(new StringField(FIELD_PROJECT_ID, String.valueOf(project.getId()), Field.Store.YES));

        // Analyzed fields — used for full-text search
        String title = project.getTitle() != null ? project.getTitle() : "";
        String abstractText = project.getAbstractText() != null ? project.getAbstractText() : "";
        String keywords = project.getKeywords() != null ? String.join(" ", project.getKeywords()) : "";
        String text = extractedText != null ? extractedText : "";

        doc.add(new TextField(FIELD_TITLE, title, Field.Store.YES));
        doc.add(new TextField(FIELD_ABSTRACT, abstractText, Field.Store.YES));
        doc.add(new TextField(FIELD_KEYWORDS, keywords, Field.Store.YES));
        doc.add(new TextField(FIELD_EXTRACTED, text, Field.Store.NO));

        // Combined field — used as the primary field for MoreLikeThis similarity queries.
        // Concatenating all textual content ensures TF-IDF calculations consider the full context.
        String fullContent = String.join(" ", title, abstractText, keywords, text);
        doc.add(new TextField(FIELD_FULL_CONTENT, fullContent, Field.Store.YES));

        return doc;
    }
}
