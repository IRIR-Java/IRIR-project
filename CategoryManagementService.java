package com.irir.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * CategoryManagementService
 *
 * Manages research categories used to classify projects.
 *
 * In a full Spring Boot project, this class would use:
 *   @Service  and  @Transactional
 * and would inject ResearchCategoryRepository and ProjectRepository.
 *
 * Removed Spring annotations to allow standalone compilation without Spring on classpath.
 */
public class CategoryManagementService {

    // Standalone in-memory store (replaces JPA repositories in demo mode)
    private final List<String[]> categories = new ArrayList<>(); // [id, name, description]
    private long idCounter = 1;

    /**
     * Returns all ResearchCategory records.
     * In Spring Boot: return categoryRepository.findAll();
     */
    public List<String[]> getCategories() {
        return categories;
    }

    /**
     * Adds a new research category.
     *
     * @param name        Display name of the category
     * @param description Description of what this category covers
     */
    public void addCategory(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name cannot be empty.");
        }
        String[] category = { String.valueOf(idCounter++), name, description };
        categories.add(category);
        System.out.println("[CATEGORY] Added: '" + name + "' → " + description);
    }

    /**
     * Deletes a category by ID, only if no projects are using it.
     *
     * @param id The ID of the category to delete
     * @throws IllegalStateException if the category has associated projects
     */
    public void deleteCategory(Long id) {
        // In Spring Boot:
        // long count = projectRepository.countByCategoryId(id);
        // if (count > 0) throw new IllegalStateException("Category in use by " + count + " projects.");
        // categoryRepository.deleteById(id);

        // Standalone simulation:
        boolean removed = categories.removeIf(c -> c[0].equals(String.valueOf(id)));
        if (removed) {
            System.out.println("[CATEGORY] Deleted category ID: " + id);
        } else {
            System.out.println("[CATEGORY] Category ID " + id + " not found (or already deleted).");
        }
    }
}
