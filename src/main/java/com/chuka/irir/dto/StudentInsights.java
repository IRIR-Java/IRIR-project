package com.chuka.irir.dto;

import java.util.List;

public class StudentInsights {
    private final List<String> trendingTopics;
    private final List<String> actionItems;

    public StudentInsights(List<String> trendingTopics, List<String> actionItems) {
        this.trendingTopics = trendingTopics;
        this.actionItems = actionItems;
    }

    public List<String> getTrendingTopics() {
        return trendingTopics;
    }

    public List<String> getActionItems() {
        return actionItems;
    }
}
