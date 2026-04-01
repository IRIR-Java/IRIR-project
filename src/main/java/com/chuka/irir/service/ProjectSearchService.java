package com.chuka.irir.service;

import com.chuka.irir.dto.SearchDTO;
import com.chuka.irir.model.Project;
import com.chuka.irir.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectSearchService {

    private final ProjectRepository projectRepository;

    // Use an in-memory directory for the search index
    private final Directory indexDirectory = new ByteBuffersDirectory();
    private final Analyzer analyzer = new EnglishAnalyzer();

    private IndexSearcher searcher;
    private DirectoryReader reader;

    @PostConstruct
    public void initIndex() {
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
                writer.deleteAll();
                List<Project> projects = projectRepository.findAll();
                for (Project project : projects) {
                    if (project.getStatus().name().equals("APPROVED")) {
                        Document doc = new Document();
                        doc.add(new StringField("id", project.getId().toString(), Field.Store.YES));
                        doc.add(new TextField("title", project.getTitle(), Field.Store.YES));
                        
                        String absText = project.getAbstractText() != null ? project.getAbstractText() : "";
                        doc.add(new TextField("abstract", absText, Field.Store.YES));
                        
                        String author = project.getSubmittedBy() != null ? project.getSubmittedBy().getFullName() : "";
                        doc.add(new StringField("author", author, Field.Store.YES));
                        doc.add(new TextField("authorText", author, Field.Store.YES));
                        
                        String dept = project.getSubmittedBy() != null ? project.getSubmittedBy().getDepartment() : "";
                        doc.add(new StringField("department", dept, Field.Store.YES));
                        
                        if (project.getAcademicYear() != null) {
                            doc.add(new IntPoint("year", project.getAcademicYear()));
                            doc.add(new StoredField("year", project.getAcademicYear()));
                        }
                        
                        if (project.getKeywords() != null) {
                            for (String kw : project.getKeywords()) {
                                doc.add(new TextField("keywords", kw, Field.Store.YES));
                            }
                        }
                        
                        writer.addDocument(doc);
                    }
                }
                writer.commit();
            }
            reader = DirectoryReader.open(indexDirectory);
            searcher = new IndexSearcher(reader);
            log.info("Lucene index initialized with current APPROVED projects.");
        } catch (IOException e) {
            log.error("Error initializing Lucene index", e);
        }
    }

    public void refreshIndex() {
        try {
            if (reader == null) return;
            DirectoryReader newReader = DirectoryReader.openIfChanged(reader);
            if (newReader != null) {
                reader.close();
                reader = newReader;
                searcher = new IndexSearcher(reader);
            }
        } catch (IOException e) {
            log.error("Error refreshing index", e);
        }
    }

    /**
     * DTO to carry formatted search results to the frontend, preventing Entity leakage.
     */
    public record SearchResultDTO(
            Long projectId,
            String title,
            String author,
            String department,
            Integer year,
            String highlightedText,
            int viewCount,
            boolean isSimilarityFree
    ) {}

    public Page<SearchResultDTO> search(SearchDTO criteria) {
        if (searcher == null) {
            return new PageImpl<>(List.of());
        }
        refreshIndex();
        
        try {
            BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();

            Query textQuery = null;
            if (criteria.getQuery() != null && !criteria.getQuery().isBlank()) {
                MultiFieldQueryParser parser = new MultiFieldQueryParser(
                        new String[]{"title", "abstract", "keywords"},
                        analyzer
                );
                textQuery = parser.parse(criteria.getQuery());
                booleanQueryBuilder.add(textQuery, BooleanClause.Occur.MUST);
            } else {
                booleanQueryBuilder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
            }

            if (criteria.getDepartment() != null && !criteria.getDepartment().isBlank()) {
                booleanQueryBuilder.add(new TermQuery(new Term("department", criteria.getDepartment())), BooleanClause.Occur.MUST);
            }

            if (criteria.getYear() != null && criteria.getYear() > 0) {
                booleanQueryBuilder.add(IntPoint.newExactQuery("year", criteria.getYear()), BooleanClause.Occur.MUST);
            }

            if (criteria.getAuthor() != null && !criteria.getAuthor().isBlank()) {
                Query authorQuery = new MultiFieldQueryParser(new String[]{"authorText"}, analyzer).parse(criteria.getAuthor());
                booleanQueryBuilder.add(authorQuery, BooleanClause.Occur.MUST);
            }

            if (criteria.getCategory() != null && !criteria.getCategory().isBlank()) {
                Query categoryQuery = new MultiFieldQueryParser(new String[]{"keywords"}, analyzer).parse(criteria.getCategory());
                booleanQueryBuilder.add(categoryQuery, BooleanClause.Occur.MUST);
            }

            BooleanQuery finalQuery = booleanQueryBuilder.build();

            int pageSize = 20;
            int pageNumber = criteria.getPage() != null ? Math.max(0, criteria.getPage() - 1) : 0;
            int hitsPerPage = (pageNumber + 1) * pageSize;
            
            TopDocs topDocs = searcher.search(finalQuery, hitsPerPage);
            ScoreDoc[] hits = topDocs.scoreDocs;
            long totalHits = topDocs.totalHits.value;
            
            List<SearchResultDTO> results = new ArrayList<>();
            QueryScorer scorer = new QueryScorer(finalQuery);
            SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<mark class=\"bg-warning fw-bold\">", "</mark>");
            Highlighter highlighter = new Highlighter(formatter, scorer);
            highlighter.setTextFragmenter(new SimpleFragmenter(150));
            
            int start = pageNumber * pageSize;
            int end = Math.min(start + pageSize, hits.length);
            
            for (int i = start; i < end; i++) {
                Document doc = searcher.storedFields().document(hits[i].doc);
                Long id = Long.valueOf(doc.get("id"));
                String title = doc.get("title");
                String abst = doc.get("abstract");
                
                String highlightedFrag = "";
                if (textQuery != null) {
                    try {
                        String hl = highlighter.getBestFragment(analyzer, "abstract", abst);
                        if (hl != null) {
                            highlightedFrag = hl;
                        } else {
                            hl = highlighter.getBestFragment(analyzer, "title", title);
                            if (hl != null) highlightedFrag = hl;
                        }
                    } catch (InvalidTokenOffsetsException e) {
                        log.warn("Highlighter error for project {}", id, e);
                    }
                }
                
                if (highlightedFrag.isBlank() && !abst.isBlank()) {
                    highlightedFrag = abst.length() > 150 ? abst.substring(0, 150) + "..." : abst;
                }
                
                Project proj = projectRepository.findById(id).orElse(null);
                int views = proj != null ? proj.getViewCount() : 0;
                
                boolean isSimilarityFree = true;
                if (proj != null && proj.getSimilarityReports() != null) {
                    isSimilarityFree = proj.getSimilarityReports().stream().noneMatch(r -> r.getSimilarityScore() >= 0.40);
                }

                results.add(new SearchResultDTO(
                        id,
                        title, // Note: could also highlight title separately and return it
                        doc.get("author"),
                        doc.get("department"),
                        doc.getField("year") != null ? doc.getField("year").numericValue().intValue() : null,
                        highlightedFrag,
                        views,
                        isSimilarityFree
                ));
            }
            
            return new PageImpl<>(results, PageRequest.of(pageNumber, pageSize), totalHits);
            
        } catch (ParseException | IOException e) {
            log.error("Search failed parsing error", e);
            return new PageImpl<>(List.of());
        }
    }
}
