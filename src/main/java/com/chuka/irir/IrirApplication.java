package com.chuka.irir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * IRIR — Intelligent Research & Innovation Repository
 *
 * Main entry point for the Spring Boot application.
 * Manages the lifecycle of final-year CS student projects at Chuka University,
 * including submission, similarity detection, supervisor review, and analytics.
 *
 * @author IRIR Development Team
 * @version 1.0.0
 */
@SpringBootApplication
public class IrirApplication {

    public static void main(String[] args) {
        SpringApplication.run(IrirApplication.class, args);
    }
}
