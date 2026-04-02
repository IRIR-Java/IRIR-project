package com.irir.admin;

import java.time.LocalDateTime;
import java.util.*;

/**
 * IRIR Admin Panel - Standalone Demo Application
 *
 * This class acts as the entry point for testing the admin panel components
 * without needing Spring Boot to be on the classpath.
 *
 * In a full Spring Boot project, this class would be replaced by:
 *   @SpringBootApplication
 *   public class AdminApplication {
 *       public static void main(String[] args) {
 *           SpringApplication.run(AdminApplication.class, args);
 *       }
 *   }
 */
public class AdminApplication {

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("   IRIR Admin Panel - Demo (Standalone Mode)  ");
        System.out.println("==============================================\n");

        // ─── 1. AuditLog Entity Demo ─────────────────────────────────────
        System.out.println("--- AuditLog Entity Demo ---");
        AuditLog log1 = new AuditLog();
        log1.setLogId(1L);
        log1.setAction("USER_LOGIN");
        log1.setUserId(42L);
        log1.setDetail("User John Doe logged in.");
        log1.setTimestamp(LocalDateTime.now());
        log1.setIpAddress("192.168.1.10");
        printAuditLog(log1);

        AuditLog log2 = new AuditLog();
        log2.setLogId(2L);
        log2.setAction("ROLE_CHANGED");
        log2.setUserId(7L);
        log2.setDetail("Role changed from STUDENT to SUPERVISOR.");
        log2.setTimestamp(LocalDateTime.now());
        log2.setIpAddress("192.168.1.20");
        printAuditLog(log2);

        // ─── 2. AuditLogService Demo ──────────────────────────────────────
        System.out.println("\n--- AuditLogService Demo ---");
        AuditLogService auditLogService = new AuditLogService();
        auditLogService.log("USER_SUSPENDED", 5L, "User ID 5 was suspended by Admin.");
        auditLogService.log("PROJECT_APPROVED", 12L, "Project ID 12 approved.");

        // ─── 3. CategoryManagementService Demo ───────────────────────────
        System.out.println("\n--- CategoryManagementService Demo ---");
        CategoryManagementService categoryService = new CategoryManagementService();
        categoryService.addCategory("Computer Science", "CS research topics");
        categoryService.addCategory("Biology", "Life sciences research");
        System.out.println("Categories retrieved: " + categoryService.getCategories().size());
        categoryService.deleteCategory(99L); // Safe delete attempt on non-existent ID

        // ─── 4. Admin Routes Summary ──────────────────────────────────────
        System.out.println("\n--- AdminController Route Summary ---");
        String[] routes = {
            "GET  /admin/dashboard        → Overview: users, projects, storage, activity",
            "GET  /admin/users            → Paginated user list with search/role filter",
            "POST /admin/users/{id}/suspend      → Suspend a user account",
            "POST /admin/users/{id}/delete       → Soft-delete a user account",
            "POST /admin/users/{id}/change-role  → Change user role",
            "GET  /admin/logs             → Paginated system audit log"
        };
        for (String route : routes) {
            System.out.println("  " + route);
        }

        // ─── 5. Backup Configuration Summary ─────────────────────────────
        System.out.println("\n--- BackupConfigurationAdvice Summary ---");
        System.out.println("  @Scheduled(cron = \"0 0 2 * * ?\") runs daily at 2:00 AM.");
        System.out.println("  Executes: mysqldump -u<user> -p<pass> <dbname> → /backups/");
        System.out.println("  Rotation: Keeps last 7 .sql files, deletes older ones.");

        System.out.println("\n==============================================");
        System.out.println("   All Admin components ran successfully!      ");
        System.out.println("==============================================");
    }

    private static void printAuditLog(AuditLog log) {
        System.out.printf("  [Log #%d] Action=%-20s UserID=%d  IP=%-15s  Time=%s%n",
                log.getLogId(),
                log.getAction(),
                log.getUserId(),
                log.getIpAddress(),
                log.getTimestamp().toString());
        System.out.println("           Detail: " + log.getDetail());
    }
}
