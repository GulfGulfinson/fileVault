package com.filevault.storage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import com.filevault.util.LoggingUtil;

/**
 * Verwaltet die SQLite-Datenbankverbindung und das Schema.
 */
public class DatabaseManager {

    private static final String DB_PATH = System.getProperty("user.home") + File.separator + ".filevault" + File.separator + "vault.db";
    private static final String TEST_DB_PATH = System.getProperty("user.home") + File.separator + ".filevault" + File.separator + "test_vault.db";
    private static String currentDbPath = DB_PATH;
    private static final String DB_URL_PREFIX = "jdbc:sqlite:";
    
    private static Connection connection;
    
    /**
     * Initialisiert die Datenbank, erstellt Tabellen falls sie nicht existieren.
     */
    public static void initDatabase() {
        initDatabase(false);
    }
    
    /**
     * Initialisiert die Datenbank für Tests oder Produktion.
     * @param isTest true für Testdatenbank, false für Produktionsdatenbank
     */
    public static void initDatabase(boolean isTest) {
        LoggingUtil.logDatabase("Initialize", "Database", "Initializing database. Test mode: " + isTest);
        try {
            currentDbPath = isTest ? TEST_DB_PATH : DB_PATH;
            
            // Sicherstellen, dass das übergeordnete Verzeichnis existiert
            Path parentDir = Paths.get(currentDbPath).getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            
            // Stelle sicher, dass die Datenbankdatei beschreibbar ist
            File dbFile = new File(currentDbPath);
            if (dbFile.exists() && !dbFile.canWrite()) {
                throw new RuntimeException("Datenbankdatei ist schreibgeschützt: " + currentDbPath);
            }
            
            // Verbindung zur Datenbank herstellen (erstellt sie, falls sie nicht existiert)
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(DB_URL_PREFIX + currentDbPath);
            
            // Fremdschlüssel aktivieren
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
            
            // Prüfen, ob die Tabellenstruktur korrekt ist
            boolean needsRecreate = false;
            try (Statement stmt = connection.createStatement()) {
                // Prüfen, ob die parent_id Spalte existiert
                stmt.execute("SELECT parent_id FROM folders LIMIT 1");
            } catch (SQLException e) {
                needsRecreate = true;
            }
            
            if (needsRecreate) {
                // Tabellen löschen und neu erstellen
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("DROP TABLE IF EXISTS files");
                    stmt.execute("DROP TABLE IF EXISTS folders");
                    stmt.execute("DROP TABLE IF EXISTS users");
                    stmt.execute("DROP TABLE IF EXISTS settings");
                }
            }
            
            // Stelle sicher, dass die Tabellen erstellt werden
            createTables();
            LoggingUtil.logDatabase("Initialize", "Database", "Database initialized successfully.");
            
        } catch (Exception e) {
            LoggingUtil.logDatabase("Initialize", "Database", "Database initialization failed: " + e.getMessage());
            System.err.println("Fehler beim Initialisieren der Datenbank: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Erstellt die notwendigen Datenbanktabellen.
     */
    private static void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Benutzertabelle
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                         "username TEXT PRIMARY KEY, " +
                         "password_hash TEXT NOT NULL, " +
                         "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                         "last_login TIMESTAMP)");
            
            // Ordnertabelle
            stmt.execute("CREATE TABLE IF NOT EXISTS folders (" +
                         "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                         "name TEXT NOT NULL, " +
                         "description TEXT, " +
                         "parent_id INTEGER, " +
                         "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                         "FOREIGN KEY (parent_id) REFERENCES folders(id))");
            
            // Dateitabelle
            stmt.execute("CREATE TABLE IF NOT EXISTS files (" +
                         "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                         "folder_id INTEGER, " +
                         "original_name TEXT NOT NULL, " +
                         "encrypted_path TEXT NOT NULL, " +
                         "size_bytes INTEGER, " +
                         "mime_type TEXT, " +
                         "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                         "last_access TIMESTAMP, " +
                         "FOREIGN KEY (folder_id) REFERENCES folders(id))");
            
            // Einstellungstabelle
            stmt.execute("CREATE TABLE IF NOT EXISTS settings (" +
                         "key TEXT PRIMARY KEY, " +
                         "value TEXT, " +
                         "description TEXT)");
        }
    }
    
    /**
     * Gibt eine Verbindung zur Datenbank zurück.
     * @return Eine Verbindung zur Datenbank
     * @throws SQLException wenn ein Datenbankfehler auftritt
     */
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            LoggingUtil.logDatabase("Connection", "Database", "Establishing new database connection.");
            connection = DriverManager.getConnection(DB_URL_PREFIX + currentDbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
            LoggingUtil.logDatabase("Connection", "Database", "Database connection established.");
        }
        return connection;
    }
    
    /**
     * Schließt alle Datenbankverbindungen.
     */
    public static void closeConnections() {
        try {
            if (connection != null && !connection.isClosed()) {
                LoggingUtil.logDatabase("Connection", "Database", "Closing database connection.");
                connection.close();
                LoggingUtil.logDatabase("Connection", "Database", "Database connection closed.");
            }
        } catch (SQLException e) {
            LoggingUtil.logError("DatabaseManager", "Error closing database connection: " + e.getMessage());
        }
    }
    
    /**
     * Löscht die Testdatenbank.
     */
    public static void deleteTestDatabase() {
        LoggingUtil.logDatabase("Delete", "TestDatabase", "Attempting to delete test database.");
        try {
            closeConnections();
            Files.deleteIfExists(Paths.get(TEST_DB_PATH));
            LoggingUtil.logDatabase("Delete", "TestDatabase", "Test database deleted successfully.");
        } catch (Exception e) {
            LoggingUtil.logError("DatabaseManager", "Error deleting test database: " + e.getMessage());
        }
    }
}