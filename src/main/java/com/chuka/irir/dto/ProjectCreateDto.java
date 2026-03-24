package com.chuka.irir.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * DTO for creating a new student project submission.
 */
public class ProjectCreateDto {

    @NotBlank(message = "Project title is required")
    @Size(min = 5, max = 255, message = "Title must be between 5 and 255 characters")
    private String title;

    @Size(max = 5000, message = "Abstract must be 5000 characters or less")
    private String abstractText;

    /**
     * Comma-separated keywords entered by the student.
     * Example: "AI, healthcare, machine learning"
     */
    private String keywords;

    /**
     * Academic year (optional). If not provided, the service will default to the current year.
     */
    private Integer academicYear;

    /**
     * Uploaded files (PDF, DOCX, ZIP).
     */
    private MultipartFile[] files;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public Integer getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(Integer academicYear) {
        this.academicYear = academicYear;
    }

    public MultipartFile[] getFiles() {
        return files;
    }

    public void setFiles(MultipartFile[] files) {
        this.files = files;
    }
}

