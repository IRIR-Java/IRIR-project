package com.chuka.irir.dto;

import lombok.*;

import java.util.List;

/**
 * Data Transfer Object for a recommended collaborator in the IRIR system.
 *
 * <p>Contains the matched student's profile information, their matching projects,
 * and the computed keyword overlap score.</p>
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollaboratorDTO {

    /** Database ID of the recommended user. */
    private Long userId;

    /** Full name of the recommended student. */
    private String fullName;

    /** University registration/student number. */
    private String regNumber;

    /** Department of the recommended student. */
    private String department;

    /** Free-text research interests from the user's profile. */
    private String researchInterests;

    /** Titles of projects that contributed to the keyword match. */
    private List<String> matchingProjects;

    /** Keyword overlap score (0.0–1.0). Higher = more overlap. */
    private Double overlapScore;

    /** Contact email of the recommended student. */
    private String contactEmail;
}
