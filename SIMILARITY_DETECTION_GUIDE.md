# Similarity Detection Engine — IRIR

The Similarity Detection feature automatically checks submitted projects against all existing projects in the IRIR database to detect plagiarism or duplicated work.

## How It Works

1. **Extraction & Indexing**
   When a student uploads a project (text, PDF, DOCX), Apache Tika extracts the full text of the document. This text, along with the abstract and keywords, is indexed into **Apache Lucene** in real-time.

2. **Triggering the Check**
   The similarity check runs **only when a student clicks "Submit to Lecturer"**. Draft projects are bypassed to allow students to work efficiently without spamming the detection engine.

3. **Comparison (TF-IDF & Cosine Similarity)**
   The engine uses Lucene's `MoreLikeThis` queries. It analyzes the mathematical frequency of terms (TF-IDF) in the newly submitted project against all previously submitted projects. It calculates the **Cosine Similarity**, returning a percentage metric representing the degree of identical textual concepts.

4. **Scoring & Verdicts**
   The highest matched score determines the project's verdict based on configured thresholds (editable in `application.properties`):
   - **Original Work (< 40%)**: The project is safely passed to the supervisor.
   - **Similar Work Detected (40% - 69%)**: The system allows the submission to proceed but adds a warning tag, alerting the student and supervisor.
   - **Potential Duplicate (≥ 70%)**: The project is immediately **FLAGGED**. The status shifts to `FLAGGED` and is held for mandatory supervisor/directorate review before it can be progressed.

5. **Reporting**
   Once submitted, a "Similarity Report" card appears on the Project Details page showing the verdict, the maximum similarity score, and a list of all matched projects it was compared against.

> **Note on Testing:** If there is only one submitted project in the database, the score will always be **0.0% (Original Work)** because there is no other data to match against. To test it successfully, you must have at least *two* projects submitted.
