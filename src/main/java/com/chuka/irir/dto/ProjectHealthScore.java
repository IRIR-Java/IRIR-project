package com.chuka.irir.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProjectHealthScore {
    private Long projectId;
    private String projectTitle;
    private int score;
    private boolean hasAbstract;
    private boolean hasKeywords;
    private boolean hasFiles;
    private boolean isApproved;
    private List<String> suggestions;
}
