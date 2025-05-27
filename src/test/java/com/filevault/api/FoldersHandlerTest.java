package com.filevault.api;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.filevault.storage.DatabaseManager;
import com.filevault.util.FolderManager;

/**
 * Testklasse für die FoldersHandler-Funktionalität mit echten HTTP-Anfragen.
 * Diese Tests konzentrieren sich darauf, zu überprüfen, ob die API-Endpunkte 
 * mit den korrekten Statuscodes antworten.
 */
class FoldersHandlerTest {

    /** Die zu testende ApiServer-Instanz */
    private ApiServer apiServer;
    
    /** Der Port für den Test-Server (unterschiedlich zu anderen Tests) */
    private final int TEST_PORT = 8767;
    
    /** ExecutorService für das Ausführen des Servers in einem separaten Thread */
    private ExecutorService executorService;
    
    /** Ein gültiges Token für Testzwecke */
    private String validToken;

    /**
     * Initialisiert die Testumgebung vor jedem Test.
     * Erstellt eine Test-Datenbank, eine ApiServer-Instanz und generiert ein gültiges Token.
     */
    @BeforeEach
    void setUp() throws Exception {
        // Initialisiere die Test-Datenbank
        DatabaseManager.initDatabase(true);
        
        apiServer = new ApiServer();
        executorService = Executors.newSingleThreadExecutor();
        
        // Generiere ein gültiges Token für Tests
        validToken = ApiServer.TokenManager.generateToken("testuser");
        
        // Initialisiere den FolderManager
        FolderManager.getInstance().initialize();
    }

    /**
     * Bereinigt die Testumgebung nach jedem Test.
     * Stoppt den Server, beendet den ExecutorService, invalidiert das Testtoken 
     * und löscht die Test-Datenbank.
     */
    @AfterEach
    void tearDown() throws InterruptedException {
        if (apiServer != null) {
            apiServer.stop();
        }
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        
        // Bereinige das Token
        ApiServer.TokenManager.invalidateToken(validToken);
        
        // Bereinige die Datenbank
        DatabaseManager.closeConnections();
        DatabaseManager.deleteTestDatabase();
    }

    /**
     * Testet das Auflisten von Ordnern über den API-Endpunkt.
     * Überprüft, ob der Server mit dem korrekten Statuscode antwortet.
     */
    @Test
    void testListFolders() throws IOException {
        // Starte den Server
        startServer();
        
        // Erstelle eine GET-Anfrage an den Folders-Endpunkt
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/folders");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", validToken);
        
        // Sende die Anfrage
        int responseCode = connection.getResponseCode();
        
        // Wir erwarten eine 200 OK-Antwort
        assertEquals(200, responseCode, "Should get 200 OK when listing folders");
    }
    
    /**
     * Testet den Ordner-Endpunkt mit einem ungültigen Token.
     * Überprüft, ob der Zugriff mit einem ungültigen Token verweigert wird.
     */
    @Test
    void testFolderEndpointWithInvalidToken() throws IOException {
        // Starte den Server
        startServer();
        
        // Erstelle eine GET-Anfrage an den Folders-Endpunkt
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/folders");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "invalid-token");
        
        // Sende die Anfrage
        int responseCode = connection.getResponseCode();
        
        // Wir erwarten eine 401 Unauthorized-Antwort
        assertEquals(401, responseCode, "Should get 401 Unauthorized with invalid token");
    }
    
    /**
     * Testet den Ordner-Endpunkt ohne Token.
     * Überprüft, ob der Zugriff ohne Token verweigert wird.
     */
    @Test
    void testFolderEndpointWithNoToken() throws IOException {
        // Starte den Server
        startServer();
        
        // Erstelle eine GET-Anfrage an den Folders-Endpunkt
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/folders");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        // Sende die Anfrage
        int responseCode = connection.getResponseCode();
        
        // Wir erwarten eine 401 Unauthorized-Antwort
        assertEquals(401, responseCode, "Should get 401 Unauthorized with no token");
    }
    
    /**
     * Hilfsmethode zum Starten des Servers und Warten, bis er bereit ist.
     */
    private void startServer() {
        executorService.submit(() -> {
            try {
                apiServer.start(TEST_PORT);
            } catch (IOException e) {
                fail("Server failed to start: " + e.getMessage());
            }
        });
        
        // Gib dem Server Zeit zum Starten
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 