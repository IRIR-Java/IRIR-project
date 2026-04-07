package com.chuka.irir.dto;

import lombok.*;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemOverviewDTO {
    private long totalUsers;
    private long totalProjects;
    private long totalStudents;
    private long pendingReviews;
    private long approvedProjects;
    private long rejectedProjects;
    private long flaggedProjects;
    private long projectsThisMonth;
    private double approvalRate;
    private Map<String, Long> projectsByStatus;
}
