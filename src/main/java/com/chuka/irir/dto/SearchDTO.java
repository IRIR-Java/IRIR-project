package com.chuka.irir.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Data Transfer Object for carrying search criteria from the UI layer to the ProjectSearchService.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchDTO {
    private String query;
    private String department;
    private Integer year;
    private String author;
    private String category;
    private Integer page = 0;
}
