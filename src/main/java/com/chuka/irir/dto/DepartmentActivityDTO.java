package com.chuka.irir.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DepartmentActivityDTO {
    private String department;
    private long projectCount;
    private long approvedCount;
    private long flaggedCount;
}
