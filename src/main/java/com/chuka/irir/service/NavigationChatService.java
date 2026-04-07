package com.chuka.irir.service;

import com.chuka.irir.dto.ChatLink;
import com.chuka.irir.dto.ChatRequest;
import com.chuka.irir.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NavigationChatService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public NavigationChatService(GeminiClient geminiClient, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    public ChatResponse reply(ChatRequest request, Set<String> roles) {
        String message = request != null ? request.getMessage() : null;
        if (!StringUtils.hasText(message)) {
            return new ChatResponse(
                    "Tell me what you want to do and I will point you to the right page.",
                    defaultLinks(roles),
                    null
            );
        }

        List<NavPage> siteMap = buildSiteMap(roles);
        String prompt = buildPrompt(message, request != null ? request.getCurrentPath() : null, siteMap, roles);

        Optional<String> ai = geminiClient.generateContent(prompt);
        if (ai.isPresent()) {
            ChatResponse parsed = parseResponse(ai.get());
            if (parsed != null && StringUtils.hasText(parsed.getAnswer())) {
                if (parsed.getLinks() == null) {
                    parsed.setLinks(Collections.emptyList());
                }
                return parsed;
            }
            return new ChatResponse(ai.get().trim(), Collections.emptyList(), null);
        }

        return fallback(message, roles);
    }

    private ChatResponse fallback(String message, Set<String> roles) {
        String lower = message.toLowerCase(Locale.ENGLISH);
        List<NavPage> allowed = buildSiteMap(roles);

        List<ChatLink> matches = allowed.stream()
                .filter(p -> p.matches(lower))
                .limit(3)
                .map(p -> new ChatLink(p.label(), p.url()))
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            matches = defaultLinks(roles);
        }

        return new ChatResponse(
                "Here are the closest pages I can take you to.",
                matches,
                "If that is not it, tell me a bit more about what you want to do."
        );
    }

    private List<ChatLink> defaultLinks(Set<String> roles) {
        List<NavPage> pages = buildSiteMap(roles);
        return pages.stream()
                .filter(p -> p.isPriority())
                .limit(3)
                .map(p -> new ChatLink(p.label(), p.url()))
                .collect(Collectors.toList());
    }

    private List<NavPage> buildSiteMap(Set<String> roles) {
        List<NavPage> pages = new ArrayList<>();
        pages.add(new NavPage("Dashboard", "/dashboard", "Role-based home screen", null, true, "dashboard", "home"));
        pages.add(new NavPage("Public Research Gallery", "/gallery", "Search published projects", null, false, "gallery", "search", "projects"));
        pages.add(new NavPage("Login", "/login", "Sign in page", null, false, "login", "sign in"));
        pages.add(new NavPage("Register", "/register", "Create a new student account", null, false, "register", "signup", "sign up"));
        pages.add(new NavPage("Forgot Password", "/forgot-password", "Reset password request", null, false, "forgot", "reset", "password"));

        if (roles.contains("ROLE_STUDENT") || roles.contains("ROLE_ADMIN")) {
            pages.add(new NavPage("Student Dashboard", "/student/dashboard", "Student overview and insights", "ROLE_STUDENT", true, "student", "dashboard"));
            pages.add(new NavPage("My Projects", "/student/projects", "Manage your projects", "ROLE_STUDENT", true, "projects", "my projects"));
            pages.add(new NavPage("Upload Project", "/student/projects/new", "Submit a new project", "ROLE_STUDENT", false, "upload", "submit", "new project"));
            pages.add(new NavPage("Collaborators", "/student/collaborators", "Find collaborators", "ROLE_STUDENT", false, "collaborator", "team"));
            pages.add(new NavPage("My Profile", "/student/profile", "Update your profile", "ROLE_STUDENT", false, "profile", "account"));
        }

        if (roles.contains("ROLE_SUPERVISOR") || roles.contains("ROLE_ADMIN")) {
            pages.add(new NavPage("Supervisor Reviews", "/supervisor/reviews", "Review assigned projects", "ROLE_SUPERVISOR", true, "supervisor", "review", "reviews"));
        }

        if (roles.contains("ROLE_DIRECTORATE")) {
            pages.add(new NavPage("Directorate Analytics", "/directorate/dashboard", "Analytics dashboard", "ROLE_DIRECTORATE", true, "directorate", "analytics", "dashboard"));
            pages.add(new NavPage("Export PDF Report", "/directorate/export/pdf", "Download analytics report", "ROLE_DIRECTORATE", false, "export", "pdf", "report"));
            pages.add(new NavPage("Export Excel Report", "/directorate/export/excel", "Download project data", "ROLE_DIRECTORATE", false, "export", "excel", "spreadsheet"));
        }

        if (roles.contains("ROLE_ADMIN")) {
            pages.add(new NavPage("Admin Dashboard", "/admin/dashboard", "Admin overview", "ROLE_ADMIN", true, "admin", "dashboard"));
            pages.add(new NavPage("All Projects", "/admin/projects", "Manage all submissions", "ROLE_ADMIN", true, "admin", "projects"));
            pages.add(new NavPage("Admin Analytics", "/admin/analytics", "Administrative analytics", "ROLE_ADMIN", false, "analytics"));
            pages.add(new NavPage("User Management", "/admin/users", "Manage users", "ROLE_ADMIN", false, "users", "roles"));
            pages.add(new NavPage("Audit Logs", "/admin/logs", "System activity logs", "ROLE_ADMIN", false, "logs", "audit"));
            pages.add(new NavPage("Settings", "/admin/settings", "System settings", "ROLE_ADMIN", false, "settings"));
            pages.add(new NavPage("Backups", "/admin/backup", "Backup management", "ROLE_ADMIN", false, "backup"));
        }

        return pages;
    }

    private String buildPrompt(String message, String currentPath, List<NavPage> siteMap, Set<String> roles) {
        String roleLabel = roles.isEmpty() ? "ANONYMOUS" : String.join(", ", roles);
        StringBuilder sb = new StringBuilder();
        sb.append("You are IRIR Navigator, a concise assistant that helps users find the right page. ");
        sb.append("Use only URLs from the SITE MAP. ");
        sb.append("Reply ONLY in JSON with keys: answer, links, followUp. ");
        sb.append("links is an array of objects with label and url. ");
        sb.append("If unsure, suggest the closest page and ask a short follow-up question. ");
        sb.append("Do not include markdown or code fences.\n");
        sb.append("USER ROLE: ").append(roleLabel).append("\n");
        if (StringUtils.hasText(currentPath)) {
            sb.append("CURRENT PATH: ").append(currentPath).append("\n");
        }
        sb.append("SITE MAP:\n");
        for (NavPage page : siteMap) {
            sb.append("- ").append(page.label()).append(" | ").append(page.url())
              .append(" | ").append(page.description()).append("\n");
        }
        sb.append("USER REQUEST: ").append(message);
        return sb.toString();
    }

    private ChatResponse parseResponse(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String json = extractJson(text.trim());
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ChatResponse.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractJson(String text) {
        if (text.startsWith("{") && text.endsWith("}")) {
            return text;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private record NavPage(
            String label,
            String url,
            String description,
            String roleRequired,
            boolean priority,
            String... keywords
    ) {
        boolean matches(String input) {
            if (!StringUtils.hasText(input)) {
                return false;
            }
            String lower = input.toLowerCase(Locale.ENGLISH);
            if (lower.contains(label.toLowerCase(Locale.ENGLISH))) {
                return true;
            }
            return Arrays.stream(keywords)
                    .filter(StringUtils::hasText)
                    .map(k -> k.toLowerCase(Locale.ENGLISH))
                    .anyMatch(lower::contains);
        }

        boolean isPriority() {
            return priority;
        }
    }
}
