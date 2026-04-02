package com.chuka.irir.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserInsightBundle {
    private List<String> trendingTopics;
    private List<CollaboratorDTO> suggestedCollaborators;
    private List<ResearchGapDTO> researchGaps;
    private List<ProjectHealthScore> projectHealthScores;
}
