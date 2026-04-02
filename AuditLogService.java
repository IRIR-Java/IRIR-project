package com.irir.admin;

import java.time.LocalDateTime;

/**
 * AuditLogService
 *
 * Records every sensitive action in the system.
 * Examples of actions: "USER_LOGIN", "PROJECT_APPROVED", "ROLE_CHANGED", "USER_SUSPENDED"
 *
 * In a full Spring Boot project, this class would use:
 *   @Service  and  @Transactional
 * and would inject AuditLogRepository to persist to the database.
 *
 * Removed Spring annotations to allow standalone compilation without Spring on classpath.
 */
public class AuditLogService {

    // In Spring Boot, this would be:
    // private final AuditLogRepository auditLogRepository;

    // Simple in-memory store for standalone/demo mode
    private final java.util.List<AuditLog> inMemoryLogs = new java.util.ArrayList<>();
    private long idCounter = 1;

    /**
     * Records every sensitive action.
     *
     * @param action  Action type e.g. "USER_LOGIN", "ROLE_CHANGED", "USER_SUSPENDED"
     * @param userId  The ID of the user who performed (or was subject to) the action
     * @param detail  Free-text description of what happened
     */
    public void log(String action, Long userId, String detail) {
        AuditLog auditLog = new AuditLog();
        auditLog.setLogId(idCounter++);
        auditLog.setAction(action);
        auditLog.setUserId(userId);
        auditLog.setDetail(detail);
        auditLog.setTimestamp(LocalDateTime.now());
        auditLog.setIpAddress(getCurrentRequestIp());

        // In Spring Boot: auditLogRepository.save(auditLog);
        inMemoryLogs.add(auditLog);

        System.out.println("[AUDIT] #" + auditLog.getLogId()
                + " | action=" + action
                + " | userId=" + userId
                + " | " + detail);
    }

    /**
     * Returns all stored audit logs (in-memory for standalone demo).
     * In Spring Boot: return auditLogRepository.findAll(...);
     */
    public java.util.List<AuditLog> getAllLogs() {
        return inMemoryLogs;
    }

    /**
     * Gets the IP address of the current HTTP request.
     * In Spring Boot, use RequestContextHolder + HttpServletRequest.getRemoteAddr()
     */
    private String getCurrentRequestIp() {
        // Placeholder – in production use:
        // HttpServletRequest request = ((ServletRequestAttributes)
        //     RequestContextHolder.currentRequestAttributes()).getRequest();
        // return request.getRemoteAddr();
        return "127.0.0.1";
    }
}
