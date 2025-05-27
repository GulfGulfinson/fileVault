package com.filevault.api;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testklasse für die AuthMiddleware-Funktionalität mit echten HTTP-Anfragen.
 * Diese Tests überprüfen die tokenbasierte Authentifizierung für geschützte Endpunkte.
 */
class AuthMiddlewareTest {

    /** Die zu testende ApiServer-Instanz */
    private ApiServer apiServer;
    
    /** Der Port für den Test-Server (unterschiedlich zu ApiServerTest) */
    private final int TEST_PORT = 8766;
    
    /** ExecutorService für das Ausführen des Servers in einem separaten Thread */
    private ExecutorService executorService;
    
    /** Ein gültiges Token für Testzwecke */
    private String validToken;

    /**
     * Initialisiert die Testumgebung vor jedem Test.
     * Erstellt eine neue ApiServer-Instanz und generiert ein gültiges Token.
     */
    @BeforeEach
    void setUp() throws Exception {
        apiServer = new ApiServer();
        executorService = Executors.newSingleThreadExecutor();
        
        // Generiere ein gültiges Token für Tests
        validToken = ApiServer.TokenManager.generateToken("testuser");
    }

    /**
     * Bereinigt die Testumgebung nach jedem Test.
     * Stoppt den Server, beendet den ExecutorService und invalidiert das Testtoken.
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
    }

    /**
     * Testet die Funktionen des TokenManagers.
     * Überprüft die Generierung und Invalidierung von Tokens.
     */
    @Test
    void testTokenManagerFunctions() {
        // Teste die Token-Generierung
        String token = ApiServer.TokenManager.generateToken("newuser");
        assertNotNull(token);
        assertTrue(ApiServer.TokenManager.isValidToken(token));
        
        // Teste die Token-Invalidierung
        ApiServer.TokenManager.invalidateToken(token);
        assertFalse(ApiServer.TokenManager.isValidToken(token));
    }
    
    /**
     * Testet einen geschützten Endpunkt mit einem gültigen Token.
     * Überprüft, ob der Zugriff mit einem gültigen Token erlaubt wird.
     */
    @Test
    void testProtectedEndpointWithValidToken() throws IOException {
        // Starte den Server
        startServer();
        
        // Erstelle eine Anfrage an einen geschützten Endpunkt
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/folders");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        // Füge das gültige Token hinzu
        connection.setRequestProperty("Authorization", validToken);
        
        // Sende die Anfrage
        int responseCode = connection.getResponseCode();
        
        // Wir erwarten eine Antwort, möglicherweise 200 OK, wenn der Endpunkt funktioniert
        // oder möglicherweise einen anderen Code, wenn der Endpunkt andere Anforderungen hat,
        // aber auf keinen Fall 401 Unauthorized
        assertNotEquals(401, responseCode, "Should not get 401 Unauthorized with valid token");
    }
    
    /**
     * Testet einen geschützten Endpunkt mit einem ungültigen Token.
     * Überprüft, ob der Zugriff mit einem ungültigen Token verweigert wird.
     */
    @Test
    void testProtectedEndpointWithInvalidToken() throws IOException {
        // Starte den Server
        startServer();
        
        // Erstelle eine Anfrage an einen geschützten Endpunkt
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/folders");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        // Füge ein ungültiges Token hinzu
        connection.setRequestProperty("Authorization", "invalid-token");
        
        // Sende die Anfrage
        int responseCode = connection.getResponseCode();
        
        // Wir erwarten eine 401 Unauthorized-Antwort
        assertEquals(401, responseCode, "Should get 401 Unauthorized with invalid token");
    }
    
    /**
     * Testet einen geschützten Endpunkt ohne Token.
     * Überprüft, ob der Zugriff ohne Token verweigert wird.
     */
    @Test
    void testProtectedEndpointWithNoToken() throws IOException {
        // Starte den Server
        startServer();
        
        // Erstelle eine Anfrage an einen geschützten Endpunkt
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/folders");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        // Füge kein Token hinzu
        
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