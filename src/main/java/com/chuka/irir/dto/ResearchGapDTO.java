package com.chuka.irir.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResearchGapDTO {
    private String topic;
    private int existingProjectCount;
    private String opportunityReason;
}
