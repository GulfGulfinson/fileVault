package com.filevault.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests für DatabaseManager.
 */
public class DatabaseManagerTest {

    @BeforeEach
    void setUp() {
        // Initialisiert die Testdatenbank vor jedem Test
        DatabaseManager.initDatabase(true);
    }

    @AfterEach
    void tearDown() {
        // Bereinigt die Testdatenbank nach jedem Test
        DatabaseManager.deleteTestDatabase();
    }

    @Test
    void testInitDatabaseCreatesTables() {
        try (Connection connection = DatabaseManager.getConnection()) {
            // Überprüft, ob die Tabellen existieren, indem ihre Metadaten abgefragt werden
            assertTrue(connection.getMetaData().getTables(null, null, "users", null).next());
            assertTrue(connection.getMetaData().getTables(null, null, "folders", null).next());
            assertTrue(connection.getMetaData().getTables(null, null, "files", null).next());
            assertTrue(connection.getMetaData().getTables(null, null, "settings", null).next());
        } catch (SQLException e) {
            fail("Database connection or table check failed: " + e.getMessage());
        }
    }

    @Test
    void testGetConnection() {
        try (Connection connection = DatabaseManager.getConnection()) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());
        } catch (SQLException e) {
            fail("Failed to get a valid database connection: " + e.getMessage());
        }
    }

    @Test
    void testDeleteTestDatabase() {
        Path testDbPath = Paths.get(System.getProperty("user.home"), ".filevault", "test_vault.db");
        assertTrue(Files.exists(testDbPath));

        DatabaseManager.deleteTestDatabase();

        assertFalse(Files.exists(testDbPath));
    }
}