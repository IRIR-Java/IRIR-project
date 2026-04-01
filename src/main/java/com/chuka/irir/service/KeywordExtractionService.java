package com.chuka.irir.service;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that extracts domain keywords from free text using Stanford CoreNLP.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Tokenize and POS-tag the input text with CoreNLP.</li>
 *   <li>Keep only nouns (NN, NNS, NNP, NNPS) and adjectives (JJ) — these carry
 *       semantic domain meaning.</li>
 *   <li>Lowercase and deduplicate the resulting tokens.</li>
 *   <li>Filter out stop words and very short tokens (length &lt; 3).</li>
 *   <li>Return the top-N keywords ranked by term frequency.</li>
 * </ol>
 *
 * <p>The CoreNLP pipeline is initialized once on startup ({@code @PostConstruct})
 * to avoid the heavy initialization cost on every request.</p>
 */
@Service
public class KeywordExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(KeywordExtractionService.class);

    private static final int DEFAULT_MAX_KEYWORDS = 20;
    private static final int MIN_WORD_LENGTH = 3;
    private static final int MAX_INPUT_CHARS = 50_000;

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "that", "this", "with", "from", "are", "was",
            "were", "has", "have", "had", "been", "being", "its", "use", "used",
            "using", "also", "such", "can", "may", "will", "shall", "more", "than",
            "one", "two", "three", "our", "their", "system", "paper", "study",
            "based", "approach", "method", "result", "data", "high", "new",
            "proposed", "present", "work", "research", "show", "shows", "shown",
            "first", "second", "third", "however", "therefore",
            "different", "various", "existing", "given", "due", "well", "each"
    );

    private StanfordCoreNLP pipeline;

    /**
     * Initializes the Stanford CoreNLP pipeline on application startup.
     * Only loads the annotators needed for tokenization and POS tagging
     * (much lighter than the full NLP pipeline).
     */
    @PostConstruct
    public void init() {
        try {
            Properties props = new Properties();
            // tokenize + split sentences + POS tag — skip NER/parse for speed
            props.setProperty("annotators", "tokenize,ssplit,pos");
            props.setProperty("tokenize.language", "en");
            this.pipeline = new StanfordCoreNLP(props);
            logger.info("Stanford CoreNLP pipeline initialized for keyword extraction");
        } catch (Exception e) {
            logger.error("Failed to initialize Stanford CoreNLP: {}", e.getMessage(), e);
            // Service will fall back to simple tokenization if CoreNLP init fails
        }
    }

    /**
     * Extracts the top keywords from the given text.
     *
     * @param text        raw text (extracted from PDF, abstract, title, etc.)
     * @param maxKeywords maximum number of keywords to return
     * @return ordered set of keywords (most frequent first), lowercased
     */
    public Set<String> extractKeywords(String text, int maxKeywords) {
        if (text == null || text.isBlank()) {
            return new LinkedHashSet<>();
        }

        // Truncate very long inputs to avoid OOM
        String input = text.length() > MAX_INPUT_CHARS ? text.substring(0, MAX_INPUT_CHARS) : text;

        try {
            if (pipeline != null) {
                return extractWithCoreNLP(input, maxKeywords);
            } else {
                return extractWithSimpleTokenization(input, maxKeywords);
            }
        } catch (Exception e) {
            logger.warn("CoreNLP keyword extraction failed, falling back to simple tokenization: {}",
                    e.getMessage());
            return extractWithSimpleTokenization(input, maxKeywords);
        }
    }

    /** Convenience overload using the default maximum (20 keywords). */
    public Set<String> extractKeywords(String text) {
        return extractKeywords(text, DEFAULT_MAX_KEYWORDS);
    }

    // ==================== CoreNLP Extraction ====================

    private Set<String> extractWithCoreNLP(String text, int maxKeywords) {
        CoreDocument document = new CoreDocument(text);
        pipeline.annotate(document);

        Map<String, Integer> termFrequency = new HashMap<>();

        for (CoreLabel token : document.tokens()) {
            String pos = token.get(edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation.class);
            String word = token.word().toLowerCase();

            // Keep nouns and adjectives only
            if (pos != null && (pos.startsWith("NN") || pos.equals("JJ")) &&
                    word.length() >= MIN_WORD_LENGTH &&
                    !STOP_WORDS.contains(word) &&
                    word.matches("[a-z][a-z0-9\\-]*")) {

                termFrequency.merge(word, 1, Integer::sum);
            }
        }

        return termFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(maxKeywords)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ==================== Fallback Simple Extraction ====================

    private Set<String> extractWithSimpleTokenization(String text, int maxKeywords) {
        Map<String, Integer> termFrequency = new HashMap<>();
        String[] tokens = text.toLowerCase().split("[^a-z0-9\\-]+");

        for (String token : tokens) {
            if (token.length() >= MIN_WORD_LENGTH && !STOP_WORDS.contains(token)
                    && token.matches("[a-z][a-z0-9\\-]*")) {
                termFrequency.merge(token, 1, Integer::sum);
            }
        }

        return termFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(maxKeywords)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
