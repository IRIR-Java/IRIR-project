package com.chuka.irir.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrendDataPoint {

    public enum TrendDirection {
        RISING, DECLINING, STABLE
    }

    private String keyword;
    private int currentPeriodCount;
    private int previousPeriodCount;
    private double growthRate;
    private TrendDirection direction;
}
