package com.filevault.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testklasse für den ApiServer.
 * Diese Klasse testet die grundlegende Funktionalität des API-Servers,
 * einschließlich Start, Stop, Change-Listener und Authentifizierungs-Endpunkt.
 */
class ApiServerTest {

    /** Die zu testende ApiServer-Instanz */
    private ApiServer apiServer;
    
    /** Der Port für den Test-Server */
    private final int TEST_PORT = 8765;
    
    /** ExecutorService für das Ausführen des Servers in einem separaten Thread */
    private ExecutorService executorService;
    
    /**
     * Initialisiert die Testumgebung vor jedem Test.
     * Erstellt eine neue ApiServer-Instanz und einen ExecutorService.
     */
    @BeforeEach
    void setUp() {
        apiServer = new ApiServer();
        executorService = Executors.newSingleThreadExecutor();
    }
    
    /**
     * Bereinigt die Testumgebung nach jedem Test.
     * Stoppt den Server und beendet den ExecutorService.
     */
    @AfterEach
    void tearDown() throws InterruptedException {
        if (apiServer != null) {
            apiServer.stop();
        }
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
    }
    
    /**
     * Testet das Starten und Stoppen des Servers.
     * Überprüft, ob der Server erfolgreich gestartet werden kann und auf Anfragen reagiert.
     */
    @Test
    void testStartAndStop() throws IOException {
        // Starte den Server in einem separaten Thread
        executorService.submit(() -> {
            try {
                apiServer.start(TEST_PORT);
            } catch (IOException e) {
                fail("Server failed to start: " + e.getMessage());
            }
        });
        
        // Gebe dem Server Zeit zum Starten
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Teste, ob der Server läuft, indem eine Anfrage gemacht wird
        try {
            URL url = new URL("http://localhost:" + TEST_PORT + "/");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            
            // Wir erwarten eine Antwort (auch wenn es ein Fehler ist)
            assertTrue(responseCode > 0);
            
        } catch (IOException e) {
            fail("Failed to connect to server: " + e.getMessage());
        }
        
        // Stoppe den Server
        apiServer.stop();
    }
    
    /**
     * Testet das Hinzufügen und Entfernen von Change-Listenern.
     * Überprüft, ob Listener korrekt registriert, benachrichtigt und entfernt werden können.
     */
    @Test
    void testAddAndRemoveChangeListener() {
        // Verwende ein CountDownLatch, um zu überprüfen, ob der Listener aufgerufen wurde
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        
        // Erstelle einen echten Listener
        Consumer<String> listener = action -> {
            listenerCalled.set(true);
            latch.countDown();
        };
        
        // Füge den Listener hinzu
        ApiServer.addChangeListener(listener);
        
        // Verwende Reflection, um die notifyChangeListeners-Methode aufzurufen
        try {
            java.lang.reflect.Method notifyMethod = ApiServer.class.getDeclaredMethod("notifyChangeListeners", String.class);
            notifyMethod.setAccessible(true);
            notifyMethod.invoke(null, "test_action");
            
            // Warte auf den Listener, um aufgerufen zu werden
            boolean called = latch.await(2, TimeUnit.SECONDS);
            assertTrue(called, "Listener sollte aufgerufen worden sein");
            assertTrue(listenerCalled.get(), "Listener sollte mit der Aktion aufgerufen worden sein");
            
            // Setze für den nächsten Test zurück
            listenerCalled.set(false);
            
            // Entferne den Listener
            ApiServer.removeChangeListener(listener);
            
            // Verwende notify erneut - diesmal sollte der Listener nicht aufgerufen werden
            notifyMethod.invoke(null, "another_action");
            
            // Warte ein wenig, um sicherzustellen, dass der Listener nicht aufgerufen wird
            Thread.sleep(500);
            assertFalse(listenerCalled.get(), "Listener should not have been called after removal");
            
        } catch (Exception e) {
            fail("Failed to test listeners: " + e.getMessage());
        }
    }
    
    /**
     * Testet den Authentifizierungs-Endpunkt.
     * Überprüft, ob der Server auf POST-Anfragen an den Auth-Endpunkt reagiert.
     */
    @Test
    void testAuthEndpoint() throws IOException {
        // Starte den Server
        executorService.submit(() -> {
            try {
                apiServer.start(TEST_PORT);
            } catch (IOException e) {
                fail("Server failed to start: " + e.getMessage());
            }
        });
        
        // Gebe dem Server Zeit zum Starten
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Erstelle eine POST-Anfrage an den Auth-Endpunkt
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/auth");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        
        // Sende ein Passwort in den Anfragetext
        // Hinweis: Dies ist ein Testpasswort, in einem echten Test müssten wir sicherstellen,
        // dass der UserManager mit diesem Passwort korrekt initialisiert ist
        String jsonInput = "{\"password\":\"test_password\"}";
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        // Lese die Antwort
        int responseCode = connection.getResponseCode();
        
        // Wir testen nur, ob der Endpunkt reagiert, nicht unbedingt mit Erfolg
        // da wir keine Möglichkeit haben, den UserManager mit einem gültigen Passwort in diesem Test zu konfigurieren
        assertTrue(responseCode == 200 || responseCode == 401, 
                "Antwortcode sollte entweder 200 (Erfolg) oder 401 (Nicht autorisiert) sein");
    }
    
    /**
     * Testet den Authentifizierungs-Endpunkt mit einer ungültigen HTTP-Methode.
     * Überprüft, ob der Server GET-Anfragen an den Auth-Endpunkt mit einem 405-Fehler ablehnt.
     */
    @Test
    void testAuthEndpointInvalidMethod() throws IOException {
        // Starte den Server
        executorService.submit(() -> {
            try {
                apiServer.start(TEST_PORT);
            } catch (IOException e) {
                fail("Server failed to start: " + e.getMessage());
            }
        });
        
        // Gebe dem Server Zeit zum Starten
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Erstelle eine GET-Anfrage an den Auth-Endpunkt (sollte als nur POST erlaubt sein)
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/auth");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        // Lese die Antwort
        int responseCode = connection.getResponseCode();
        
        // Wir erwarten eine 405 Method Not Allowed Antwort
        assertEquals(405, responseCode, "Antwortcode sollte 405 (Method Not Allowed) sein");
    }
} 