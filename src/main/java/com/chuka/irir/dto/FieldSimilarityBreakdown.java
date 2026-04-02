package com.chuka.irir.dto;

public record FieldSimilarityBreakdown(
        double titleScore,
        double abstractScore,
        double keywordsScore,
        double weightedComposite) {
}
