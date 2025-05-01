package com.filevault.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Utility-Klasse für das Logging in einem benutzerdefinierten Ringpuffer.
 */
public class LoggingUtil {

    private static final int RING_BUFFER_CAPACITY = 100; // Kapazität des Ringpuffers
    private static final ArrayBlockingQueue<String> ringBuffer = new ArrayBlockingQueue<>(RING_BUFFER_CAPACITY);
    private static final LinkedList<String> fileRingBuffer = new LinkedList<>();
    private static final String LOG_FILE_PATH = "logs/filevault_log.log";
    private static String logFilePath = LOG_FILE_PATH;
    private static boolean loggingEnabled = true;

    static {
        try {
            Path logDir = Paths.get("logs");
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Erstellen des Log-Verzeichnisses: " + e.getMessage());
        }
    }

    /**
     * Sets the log file path for testing purposes.
     *
     * @param path The new log file path.
     */
    public static void setLogFilePath(String path) {
        logFilePath = path;
    }

    /**
     * Disables logging.
     */
    public static void disableLogging() {
        loggingEnabled = false;
    }

    /**
     * Enables logging.
     */
    public static void enableLogging() {
        loggingEnabled = true;
    }

    /**
     * Fügt eine Log-Nachricht zum Ringpuffer hinzu und schreibt sie in die Logdatei.
     *
     * @param message Die zu loggende Nachricht
     */
    public static void log(String message) {
        if (!loggingEnabled) {
            return;
        }

        if (!ringBuffer.offer(message)) {
            ringBuffer.poll();
            ringBuffer.offer(message);
        }

        synchronized (fileRingBuffer) {
            fileRingBuffer.add(message);
            if (fileRingBuffer.size() > RING_BUFFER_CAPACITY) {
                fileRingBuffer.removeFirst();
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath, false))) {
                for (String logMessage : fileRingBuffer) {
                    writer.write(logMessage);
                    writer.newLine();
                }
            } catch (IOException e) {
                System.err.println("Error writing to log file: " + e.getMessage());
            }
        }
    }

    /**
     * Logs an informational message.
     *
     * @param className The name of the class where the log is generated.
     * @param message   The message to log.
     */
    public static void logInfo(String className, String message) {
        log("INFO: [" + className + "] " + message);
    }

    /**
     * Logs an error message.
     *
     * @param className The name of the class where the log is generated.
     * @param message   The message to log.
     */
    public static void logError(String className, String message) {
        log("ERROR: [" + className + "] " + message);
    }

    /**
     * Logs a severe error message.
     *
     * @param className The name of the class where the log is generated.
     * @param message   The message to log.
     */
    public static void logSevere(String className, String message) {
        log("SEVERE: [" + className + "] " + message);
    }

    /**
     * Logs a database-related message.
     *
     * @param operation The database operation (e.g., "Get", "Put").
     * @param target    The target of the operation (e.g., "File", "Folder").
     * @param message   The message to log.
     */
    public static void logDatabase(String operation, String target, String message) {
        log("DATABASE: [" + operation + " " + target + "] " + message);
    }

    /**
     * Gibt alle Log-Nachrichten im Ringpuffer zurück.
     *
     * @return Ein Array mit allen Log-Nachrichten
     */
    public static String[] getLogs() {
        return ringBuffer.toArray(String[]::new);
    }
}