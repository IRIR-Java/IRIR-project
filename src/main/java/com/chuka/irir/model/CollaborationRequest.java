package com.chuka.irir.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing a collaboration request between two students.
 *
 * <p>Created when a student sends a collaboration request to another student
 * whose research interests overlap (UC-04). The receiver can ACCEPT or DECLINE.</p>
 */
@Entity
@Table(name = "collaboration_requests",
       uniqueConstraints = @UniqueConstraint(
               columnNames = {"sender_id", "receiver_id"},
               name = "uk_collab_sender_receiver"))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollaborationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The student who initiated the collaboration request. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /** The student who receives the collaboration request. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    /** Current status of the request. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CollaborationStatus status = CollaborationStatus.PENDING;

    /** Optional message from the sender. */
    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
