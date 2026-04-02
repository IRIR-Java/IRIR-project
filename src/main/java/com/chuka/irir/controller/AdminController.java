package com.chuka.irir.controller;

import com.chuka.irir.model.User;
import com.chuka.irir.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    public String users(@RequestParam(name = "q", required = false) String query, Model model) {
        List<User> users = (query != null && !query.isBlank())
                ? userRepository.searchByNameOrEmail(query)
                : userRepository.findAll();
        model.addAttribute("users", users);
        model.addAttribute("query", query);
        model.addAttribute("pageTitle", "User Management");
        return "admin/users";
    }

    @GetMapping("/users/create")
    public String createUser(Model model) {
        model.addAttribute("pageTitle", "Create User");
        return "admin/users-create";
    }

    @GetMapping("/logs")
    public String logs(Model model) {
        model.addAttribute("pageTitle", "System Logs");
        return "admin/logs";
    }

    @GetMapping("/backup")
    public String backup(Model model) {
        model.addAttribute("pageTitle", "Backups");
        return "admin/backup";
    }
}
