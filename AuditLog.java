package com.irir.admin;

import java.time.LocalDateTime;

/**
 * AuditLog Entity
 *
 * In a full Spring Boot + JPA project, this class would use:
 *   @Entity
 *   @Table(name = "audit_logs")
 * and the fields would map to database columns.
 *
 * Removed JPA annotations to allow standalone compilation without Spring/Hibernate on classpath.
 */
public class AuditLog {

    // @Id
    // @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;         // Primary Key

    private String action;      // e.g. "USER_LOGIN", "ROLE_CHANGED", "USER_SUSPENDED"

    private Long userId;        // Foreign key → User table

    private String detail;      // Free-text description of the event

    private LocalDateTime timestamp; // When the action occurred

    private String ipAddress;   // IP address of the request origin (max 45 chars for IPv6)

    // ─── Constructors ────────────────────────────────────────────────────

    public AuditLog() {
    }

    public AuditLog(String action, Long userId, String detail, LocalDateTime timestamp, String ipAddress) {
        this.action = action;
        this.userId = userId;
        this.detail = detail;
        this.timestamp = timestamp;
        this.ipAddress = ipAddress;
    }

    // ─── Getters and Setters ─────────────────────────────────────────────

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @Override
    public String toString() {
        return "AuditLog{" +
                "logId=" + logId +
                ", action='" + action + '\'' +
                ", userId=" + userId +
                ", detail='" + detail + '\'' +
                ", timestamp=" + timestamp +
                ", ipAddress='" + ipAddress + '\'' +
                '}';
    }
}
