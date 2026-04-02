package com.irir.admin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

/**
 * BackupConfigurationAdvice
 *
 * Demonstrates how to configure an automated MySQL database dump using a scheduled task.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * SPRING BOOT INTEGRATION (how it would look in a real Spring Boot project):
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *   @Configuration
 *   @EnableScheduling               ← Enable in main class or config
 *   public class BackupConfiguration {
 *
 *       @Scheduled(cron = "0 0 2 * * ?")   ← Every day at 02:00 AM
 *       public void performAutomatedDbBackup() { ... }
 *   }
 *
 *   application.properties:
 *     spring.task.scheduling.pool.size=1
 *
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Removed Spring annotations to allow standalone compilation without Spring on classpath.
 * The logic below is fully functional as plain Java.
 */
public class BackupConfigurationAdvice {

    // ─── Configuration ────────────────────────────────────────────────────────
    private static final String DB_USERNAME  = "root";
    private static final String DB_PASSWORD  = "password";
    private static final String DB_NAME      = "irir_db";
    private static final String BACKUP_DIR   = System.getProperty("user.home") + "/backups/irir";
    private static final int    MAX_BACKUPS  = 7;

    /**
     * Main entry point for standalone execution / demo.
     * In Spring Boot this method would NOT exist — the @Scheduled annotation
     * would cause Spring to call performAutomatedDbBackup() on the cron schedule.
     */
    public static void runDemo() {
        BackupConfigurationAdvice advice = new BackupConfigurationAdvice();

        System.out.println("[BACKUP] Starting backup configuration demo...");
        System.out.println("[BACKUP] Backup directory: " + BACKUP_DIR);
        System.out.println("[BACKUP] Retention policy: keep last " + MAX_BACKUPS + " files");
        System.out.println("[BACKUP] Cron schedule (Spring): 0 0 2 * * ? (daily at 2:00 AM)\n");

        advice.performAutomatedDbBackup();
    }

    /**
     * Scheduled backup method.
     *
     * In Spring Boot this would be annotated:
     *   @Scheduled(cron = "0 0 2 * * ?")
     *
     * Runs mysqldump via Runtime.exec() and saves the output to the /backups directory.
     * After each backup, rotation logic trims files to keep only the newest MAX_BACKUPS files.
     */
    public void performAutomatedDbBackup() {
        System.out.println("[BACKUP] Performing automated database backup...");

        // Ensure backup directory exists
        File dir = new File(BACKUP_DIR);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            System.out.println("[BACKUP] Created backup directory: " + BACKUP_DIR + " → " + created);
        }

        // Generate a timestamped filename
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName  = "backup_irir_" + timestamp + ".sql";
        String fullPath  = BACKUP_DIR + File.separator + fileName;

        // Build the mysqldump command
        // On Linux/macOS:  "mysqldump -u<user> -p<pass> --databases <dbname> -r <file>"
        // On Windows:      Same command, provided mysqldump.exe is on the PATH
        String command = "mysqldump -u" + DB_USERNAME
                + " -p" + DB_PASSWORD
                + " --add-drop-table --databases " + DB_NAME
                + " -r " + fullPath;

        System.out.println("[BACKUP] Running: " + command.replace(DB_PASSWORD, "****"));

        try {
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("[BACKUP] ✓ Backup created: " + fullPath);
                rotateBackups(dir);
            } else {
                System.err.println("[BACKUP] ✗ Backup failed. Exit code: " + exitCode);
                System.err.println("         → Ensure mysqldump is installed and on the system PATH.");
                System.err.println("         → Verify DB credentials in BackupConfigurationAdvice.");
            }

        } catch (IOException e) {
            System.err.println("[BACKUP] IOException: Could not start mysqldump.");
            System.err.println("         → " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[BACKUP] Backup interrupted: " + e.getMessage());
        }
    }

    /**
     * Rotation logic: keep the latest MAX_BACKUPS files, delete the oldest.
     *
     * @param dir The backup directory containing .sql files
     */
    private void rotateBackups(File dir) {
        File[] files = dir.listFiles(
                (d, name) -> name.startsWith("backup_irir_") && name.endsWith(".sql")
        );

        if (files == null || files.length <= MAX_BACKUPS) {
            System.out.println("[BACKUP] Rotation: " + (files == null ? 0 : files.length)
                    + " file(s) present — no rotation needed.");
            return;
        }

        // Sort ascending by last-modified (oldest first)
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        int deleteCount = files.length - MAX_BACKUPS;
        System.out.println("[BACKUP] Rotation: deleting " + deleteCount + " old backup(s)...");

        for (int i = 0; i < deleteCount; i++) {
            File old = files[i];
            if (old.delete()) {
                System.out.println("[BACKUP]   Deleted → " + old.getName());
            } else {
                System.err.println("[BACKUP]   Failed to delete → " + old.getName());
            }
        }
    }
}
