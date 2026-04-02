package com.irir.admin;

import java.util.HashMap;
import java.util.Map;

/**
 * AdminController
 *
 * Handles all admin panel HTTP routes for the IRIR system.
 *
 * In a full Spring Boot project, this class would use:
 *   @Controller
 *   @RequestMapping("/admin")
 * and all methods would return Thymeleaf view names with data in a Model.
 *
 * Removed Spring annotations to allow standalone compilation without Spring on classpath.
 * Each method instead returns a Map<String, Object> representing what would be added to the Model.
 */
public class AdminController {

    private final AuditLogService auditLogService = new AuditLogService();

    /**
     * GET /admin/dashboard
     * Returns overview data: total users, total projects, storage used, recent activity.
     */
    public Map<String, Object> dashboard() {
        Map<String, Object> model = new HashMap<>();
        // In Spring Boot, these values come from service calls:
        // model.put("totalUsers", userService.countAll());
        // model.put("totalProjects", projectService.countAll());
        // model.put("storageUsed", storageService.getTotalUsed());
        // model.put("recentActivity", auditLogService.getRecentLogs(10));

        model.put("totalUsers", 150);
        model.put("totalProjects", 45);
        model.put("storageUsed", "15 GB");
        model.put("recentActivity", "No recent activity (demo mode)");
        return model;
    }

    /**
     * GET /admin/users?search=&role=&page=0&size=10
     * Returns paginated list of all users with optional search by name/email/role.
     */
    public Map<String, Object> users(String search, String role, int page, int size) {
        Map<String, Object> model = new HashMap<>();
        // In Spring Boot:
        // Pageable pageable = PageRequest.of(page, size);
        // Page<User> userPage = userService.findAllUsers(search, role, pageable);
        // model.put("userPage", userPage);
        model.put("search", search);
        model.put("role", role);
        model.put("page", page);
        model.put("size", size);
        model.put("users", "Paginated user list would go here (connected to DB)");
        return model;
    }

    /**
     * POST /admin/users/{id}/suspend
     * Sets user status to SUSPENDED — user cannot log in.
     */
    public String suspendUser(Long id) {
        // In Spring Boot: userService.suspendUser(id);
        auditLogService.log("USER_SUSPENDED", id, "User ID " + id + " was suspended by Admin.");
        // Redirect to: /admin/users?success=UserSuspended
        return "redirect:/admin/users?success=UserSuspended";
    }

    /**
     * POST /admin/users/{id}/delete
     * Soft-deletes a user (sets deletedAt timestamp). Their projects are retained.
     */
    public String deleteUser(Long id) {
        // In Spring Boot: userService.softDeleteUser(id);
        auditLogService.log("USER_DELETED", id, "Soft-deleted user ID: " + id + ". Projects retained.");
        return "redirect:/admin/users?success=UserDeleted";
    }

    /**
     * POST /admin/users/{id}/change-role
     * Changes the user's role to one of: STUDENT, SUPERVISOR, DIRECTORATE, ADMIN.
     */
    public String changeRole(Long id, String newRole) {
        String[] validRoles = {"STUDENT", "SUPERVISOR", "DIRECTORATE", "ADMIN"};
        boolean isValid = false;
        for (String r : validRoles) {
            if (r.equals(newRole)) { isValid = true; break; }
        }
        if (!isValid) {
            return "redirect:/admin/users?error=InvalidRole";
        }
        // In Spring Boot: userService.changeUserRole(id, newRole);
        auditLogService.log("ROLE_CHANGED", id, "Role for user ID " + id + " changed to " + newRole);
        return "redirect:/admin/users?success=RoleChanged";
    }

    /**
     * GET /admin/logs?page=0&size=20
     * Returns paginated system activity/audit log.
     */
    public Map<String, Object> logs(int page, int size) {
        Map<String, Object> model = new HashMap<>();
        // In Spring Boot:
        // Pageable pageable = PageRequest.of(page, size);
        // model.put("logPage", auditLogService.findAllLogs(pageable));
        model.put("page", page);
        model.put("size", size);
        model.put("logs", auditLogService.getAllLogs());
        return model;
    }
}
