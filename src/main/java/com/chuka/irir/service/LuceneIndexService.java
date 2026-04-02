package com.chuka.irir.service;

import com.chuka.irir.model.ResearchProject;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class LuceneIndexService {
    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexService.class);

    public void indexDocument(ResearchProject project) {
        logger.info("Stub: Indexing document for project ID: {}", project.getProjectId());
        // TODO: Implement actual Lucene indexing logic
    }
}
