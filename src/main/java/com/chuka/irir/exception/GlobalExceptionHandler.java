package com.chuka.irir.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Global exception handler for the IRIR application.
 *
 * Catches exceptions thrown by any controller and renders appropriate
 * error pages or redirect messages. Uses {@link ControllerAdvice} to
 * apply across all controllers.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles cases where a requested resource (User, Project, etc.) is not found.
     * Renders the custom 404 error page.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleResourceNotFound(ResourceNotFoundException ex, Model model) {
        logger.warn("Resource not found: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/404";
    }

    /**
     * Handles file upload size limit exceeded.
     * Redirects back with an error message.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxSizeException(MaxUploadSizeExceededException ex,
                                         RedirectAttributes redirectAttributes) {
        logger.warn("File upload size exceeded: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage",
                "File size exceeds the maximum allowed limit of 50MB.");
        return "redirect:/student/projects";
    }

    /**
     * Handles file storage exceptions (I/O errors during upload/read).
     */
    @ExceptionHandler(FileStorageException.class)
    public String handleFileStorageException(FileStorageException ex, Model model) {
        logger.error("File storage error: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "An error occurred while processing the file: " + ex.getMessage());
        return "error/500";
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * Logs the full stack trace and renders a generic error page.
     */
    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, Model model) {
        logger.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "An unexpected error occurred. Please try again later.");
        // Debug info — remove after diagnosing production issue
        model.addAttribute("debugErrorType", ex.getClass().getName());
        model.addAttribute("debugErrorDetail", ex.getMessage());
        Throwable root = ex;
        while (root.getCause() != null) root = root.getCause();
        model.addAttribute("debugRootCause", root.getClass().getName() + ": " + root.getMessage());
        return "error/500";
    }
}
