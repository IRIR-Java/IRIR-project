package com.chuka.irir.util;

import java.util.Set;

/**
 * Application-wide constants used across multiple layers of the IRIR system.
 *
 * <p>Centralising magic values here keeps services, controllers, and validators
 * consistent and easy to update without hunting through scattered literals.</p>
 *
 * @author IRIR Development Team
 */
public final class AppConstants {

    private AppConstants() {
        // Utility class — prevent instantiation
    }

    // -------------------- File Upload --------------------

    /** Allowed MIME types for project file uploads. */
    public static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/zip",
            "application/x-zip-compressed"
    );

    /** Human-readable label for the accepted file formats (used in validation messages). */
    public static final String ALLOWED_EXTENSIONS_LABEL = "PDF, DOCX, ZIP";

    // -------------------- Similarity Detection --------------------

    /** Default cosine-similarity threshold for flagging potential duplicates (≥ 0.70). */
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.70;

    /** Default cosine-similarity threshold for warning about similar work (≥ 0.40). */
    public static final double DEFAULT_WARNING_THRESHOLD = 0.40;

    /** Maximum number of similar projects returned per check. */
    public static final int MAX_SIMILARITY_RESULTS = 10;

    // -------------------- Keywords --------------------

    /** Maximum number of keywords a student can attach to a single project. */
    public static final int MAX_KEYWORDS_PER_PROJECT = 10;

    // -------------------- Pagination --------------------

    /** Default page size for paginated listings. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    // -------------------- Roles (mirrors the Role enum — kept here for SpEL / Thymeleaf use) --------------------

    public static final String ROLE_STUDENT      = "STUDENT";
    public static final String ROLE_SUPERVISOR    = "SUPERVISOR";
    public static final String ROLE_DIRECTORATE   = "DIRECTORATE";
    public static final String ROLE_ADMIN         = "ADMIN";
}
