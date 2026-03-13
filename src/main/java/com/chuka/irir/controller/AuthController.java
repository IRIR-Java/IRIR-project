package com.chuka.irir.controller;

import com.chuka.irir.dto.UserRegistrationDto;
import com.chuka.irir.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for authentication pages: login, registration, and logout.
 *
 * Handles:
 * <ul>
 *   <li>GET /login — renders the login form</li>
 *   <li>GET /register — renders the registration form</li>
 *   <li>POST /register — processes new student registration</li>
 * </ul>
 *
 * <p>Login POST is handled by Spring Security's form login filter
 * (configured in {@link com.chuka.irir.config.SecurityConfig}).</p>
 */
@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // ==================== Login ====================

    /**
     * Displays the login page.
     * Spring Security handles the actual authentication via POST /login.
     *
     * @return the login template name
     */
    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

    // ==================== Registration ====================

    /**
     * Displays the student registration form.
     *
     * @param model the Spring MVC model
     * @return the register template name
     */
    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new UserRegistrationDto());
        return "register";
    }

    /**
     * Processes the student registration form submission.
     *
     * Validates form data, checks for duplicate email/studentId,
     * hashes the password with BCrypt, and creates the user with STUDENT role.
     *
     * @param userDto            the form data bound to {@link UserRegistrationDto}
     * @param bindingResult      validation results
     * @param redirectAttributes flash attributes for success/error messages
     * @return redirect to login on success, or back to register form on error
     */
    @PostMapping("/register")
    public String registerStudent(@Valid @ModelAttribute("user") UserRegistrationDto userDto,
                                  BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes) {

        // Check for validation errors from @Valid annotations
        if (bindingResult.hasErrors()) {
            return "register";
        }

        // Check password confirmation match
        if (!userDto.getPassword().equals(userDto.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "error.user", "Passwords do not match");
            return "register";
        }

        try {
            userService.registerStudent(userDto);
            logger.info("New student registered successfully: {}", userDto.getEmail());

            redirectAttributes.addFlashAttribute("successMessage",
                    "Registration successful! Please log in with your credentials.");
            return "redirect:/login";

        } catch (IllegalArgumentException e) {
            logger.warn("Registration failed: {}", e.getMessage());
            bindingResult.rejectValue("email", "error.user", e.getMessage());
            return "register";
        }
    }
}
