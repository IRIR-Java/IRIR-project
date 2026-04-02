package com.chuka.irir.service;

import java.util.List;

public record AiInsightPayload(
        String summary,
        List<String> strengths,
        List<String> gaps,
        List<String> suggestedKeywords,
        List<String> nextSteps,
        List<String> riskFlags,
        Double noveltyScore
) {
}
