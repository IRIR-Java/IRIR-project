# IRIR Project Report

**Project:** Intelligent Research & Innovation Repository (IRIR)  
**Type:** Spring Boot web application (server-rendered with Thymeleaf)  
**Date:** 2026-04-07

---

## Executive Summary
IRIR is a university-focused research project management platform that supports end-to-end submission, review, and approval workflows. It enforces role-based access control, provides similarity detection for plagiarism/duplication checks, and offers dashboards for students, supervisors, directorate, and administrators.

---

## Project Scope
**In scope:**
- Student project submission and lifecycle management
- Role-based review workflows (Supervisor and Directorate)
- Similarity detection and reporting on submission
- Search/indexing of project content
- Collaboration requests and recommendations
- Audit logging and administrative oversight
- Notifications and basic analytics dashboards
- File upload, parsing, and storage
- Public gallery of approved projects

**Out of scope (not implemented here):**
- Real-time chat between users
- External plagiarism APIs (uses internal Lucene-based similarity detection)
- Mobile-native applications
- Multi-tenant deployment across multiple institutions
- Advanced ML model training and custom LLM hosting

---

## Users and Roles
- **STUDENT:** submits projects, edits drafts, views feedback and similarity results
- **SUPERVISOR:** reviews submissions, approves/rejects, requests revisions
- **DIRECTORATE:** performs secondary reviews, analytics, incubation flagging
- **ADMIN:** user management, system oversight, logs, backups

---

## Core Functional Requirements
- Authentication and registration for students
- Role-based dashboards
- Project submission with file uploads
- Review workflow with decisions (approve/reject/revision)
- Similarity detection and report generation on submission
- Search and discovery of indexed content
- Audit logging of key actions
- Export reporting (PDF/Excel)

---

## Similarity Detection Specs
- Triggered only when a student submits for lecturer review
- Uses Apache Tika for text extraction and Apache Lucene for indexing
- Similarity engine uses Lucene `MoreLikeThis` (TF-IDF + cosine similarity)
- Verdict thresholds (from configuration):
  - **>= 0.70:** Potential Duplicate (project flagged)
  - **0.40 – 0.69:** Similar Work Detected (warning)
  - **< 0.40:** Original Work
- Maximum results returned per comparison: `10`

---

## Non-Functional Requirements
- Java 17 runtime
- Maven-based build and packaging
- MySQL as default database; H2 for development
- File upload limit: 50 MB per file/request
- Server default port: 8080
- Configurable through environment variables

---

## Tech Stack
- **Backend:** Spring Boot 3.2.3, Spring MVC, Spring Security
- **Templating:** Thymeleaf
- **Persistence:** Spring Data JPA + Hibernate
- **DB:** MySQL (default), H2 (dev)
- **Search/Index:** Apache Lucene
- **Parsing:** Apache Tika
- **NLP:** Stanford CoreNLP
- **Reporting:** Apache POI (Excel)
- **Frontend assets:** Bootstrap (WebJars)

---

## Configuration Highlights
- Database configuration via env vars (MYSQLHOST, MYSQLUSER, etc.)
- Upload dir: `uploads` (overridable by `UPLOAD_DIR`)
- Lucene index dir: `lucene-index` (overridable by `LUCENE_INDEX_DIR`)
- Similarity thresholds configurable in `application.properties`
- Optional Gemini AI integration via `GEMINI_API_KEY`

---

## Deployment
- Docker-based build and runtime
- Railway deployment supported (`railway.toml`)
- Health check path: `/login`

---

## Project Structure (Key Paths)
- `src/main/java/com/chuka/irir/config`
- `src/main/java/com/chuka/irir/controller`
- `src/main/java/com/chuka/irir/model`
- `src/main/java/com/chuka/irir/service`
- `src/main/java/com/chuka/irir/repository`
- `src/main/resources/templates`
- `src/main/resources/application.properties`
- `src/main/resources/application-h2.properties`

---

## Screenshot
![IRIR Dashboard](screenshots/dashboard.png)
