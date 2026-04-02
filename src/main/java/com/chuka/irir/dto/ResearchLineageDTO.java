package com.chuka.irir.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResearchLineageDTO {
    private Long projectId;
    private String title;
    private String authorName;
    private Integer academicYear;
    private double relatednessScore;
    private String linkType;
}
