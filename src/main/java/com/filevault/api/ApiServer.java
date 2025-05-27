package com.filevault.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.filevault.model.EncryptedFile;
import com.filevault.model.UserManager;
import com.filevault.storage.DatabaseManager;
import com.filevault.storage.FileStorage;
import com.filevault.util.LoggingUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Einfache API-Server-Klasse, die HTTP-Anfragen für die FileVault-Anwendung verarbeitet.
 * Bietet Endpunkte für Authentifizierung, Ordner- und Dateiverwaltung sowie ein Web-Interface.
 * Unterstützt GET, POST, PUT und DELETE Anfragen und verwendet einen einfachen Token-basierten
 * Authentifizierungsmechanismus.
 */
public class ApiServer {

    /** Der HTTP-Server, der die API-Anfragen verarbeitet */
    private HttpServer server;
    
    /** Liste von Listenern, die bei API-Änderungen informiert werden */
    private static final List<Consumer<String>> changeListeners = new CopyOnWriteArrayList<>();

    /**
     * Hilfsklasse für JSON-Parsing
     */
    private static class JsonUtils {
        public static String parseJson(String json, String key) {
            // Simple JSON parsing logic (replace with a proper library like Jackson or Gson in production)
            String searchKey = "\"" + key + "\":";
            int startIndex = json.indexOf(searchKey) + searchKey.length();
            int endIndex = json.indexOf(",", startIndex);
            if (endIndex == -1) {
                endIndex = json.indexOf("}", startIndex);
            }
            return json.substring(startIndex, endIndex).replaceAll("\"", "").trim();
        }
    }

    /**
     * Fügt einen Listener hinzu, der bei Änderungen über die API informiert wird
     * @param listener Der Listener-Callback
     */
    public static void addChangeListener(Consumer<String> listener) {
        if (listener != null) {
            changeListeners.add(listener);
            LoggingUtil.logInfo("ApiServer", "Change listener registered");
        }
    }
    
    /**
     * Entfernt einen vorher registrierten Change-Listener
     * @param listener Der zu entfernende Listener
     */
    public static void removeChangeListener(Consumer<String> listener) {
        if (listener != null) {
            changeListeners.remove(listener);
            LoggingUtil.logInfo("ApiServer", "Change listener unregistered");
        }
    }
    
    /**
     * Benachrichtigt alle registrierten Listener über eine Änderung
     * @param action Die Art der Änderung (z.B. "create_folder", "delete_file")
     */
    private static void notifyChangeListeners(String action) {
        LoggingUtil.logInfo("ApiServer", "Notifying listeners about action: " + action);
        
        // Ensure we have a consistent list even if listeners are changed during iteration
        List<Consumer<String>> listenersCopy = new ArrayList<>(changeListeners);
        
        for (Consumer<String> listener : listenersCopy) {
            try {
                listener.accept(action);
                LoggingUtil.logInfo("ApiServer", "Listener notified about action: " + action);
            } catch (Exception e) {
                LoggingUtil.logError("ApiServer", "Error notifying listener: " + e.getMessage());
            }
        }
    }

    /**
     * Startet den API-Server auf dem angegebenen Port.
     *
     * @param port Der Port, auf dem der Server gestartet wird.
     * @throws IOException Wenn ein Fehler beim Starten des Servers auftritt.
     */
    public void start(int port) throws IOException {
        LoggingUtil.logInfo("ApiServer", "Starting API server on port " + port);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/auth", new AuthHandler());
            LoggingUtil.logInfo("ApiServer", "Kontext /api/auth registriert.");

            server.createContext("/api/folders", new AuthMiddleware(new FoldersHandler()));
            LoggingUtil.logInfo("ApiServer", "Kontext /api/folders mit Authentifizierung registriert.");

            server.createContext("/api/files", new AuthMiddleware(new FileHandler()));
            LoggingUtil.logInfo("ApiServer", "Kontext /api/files mit Authentifizierung registriert.");

            server.createContext("/", new WebInterfaceHandler());
            LoggingUtil.logInfo("ApiServer", "Kontext / für Web-Interface registriert.");

            server.setExecutor(null); // Standard-Executor
            server.start();
            LoggingUtil.logInfo("ApiServer", "API-Server gestartet auf Port " + port);
            LoggingUtil.logInfo("ApiServer", "API server started successfully.");
        } catch (IOException | IllegalArgumentException e) {
            LoggingUtil.logSevere("ApiServer", "Failed to start API server: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Stoppt den API-Server.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * Ein einfacher In-Memory-Token-Manager zur Verwaltung von Authentifizierungs-Token.
     */
    static class TokenManager {
        private static final Map<String, String> tokenStore = new HashMap<>();

        public static String generateToken(String username) {
            String token = UUID.randomUUID().toString();
            tokenStore.put(token, username);
            return token;
        }

        public static boolean isValidToken(String token) {
            return tokenStore.containsKey(token);
        }

        public static void invalidateToken(String token) {
            tokenStore.remove(token);
        }
    }

    /**
     * Handler für Authentifizierungsanfragen.
     */
    static class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            LoggingUtil.logInfo("ApiServer", "Verarbeite Authentifizierungsanfrage...");

            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                LoggingUtil.logInfo("ApiServer", "Anfrageinhalt: " + requestBody);

                // Extrahiere das Passwort aus dem JSON
                String password = JsonUtils.parseJson(requestBody, "password");
                
                // Überprüfe das Passwort mit dem UserManager
                if (UserManager.getInstance().authenticate(password)) {
                    String token = TokenManager.generateToken("user");
                    String response = String.format("{\"token\":\"%s\"}", token);
                    LoggingUtil.logInfo("ApiServer", "Token generiert: " + token);

                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                } else {
                    String response = "{\"error\":\"Ungültiges Passwort. Zugriff verweigert.\"}";
                    LoggingUtil.logWarning("ApiServer", "Authentifizierung fehlgeschlagen: Ungültiges Passwort.");
                    exchange.sendResponseHeaders(401, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }
            } else {
                LoggingUtil.logWarning("ApiServer", "HTTP-Methode nicht erlaubt: " + exchange.getRequestMethod());
                exchange.sendResponseHeaders(405, -1); // Methode nicht erlaubt
            }
        }
    }

    /**
     * Middleware zur Überprüfung von gültigen Authentifizierungs-Token.
     */
    static class AuthMiddleware implements HttpHandler {
        private final HttpHandler next;

        public AuthMiddleware(HttpHandler next) {
            this.next = next;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            LoggingUtil.logInfo("ApiServer", "Überprüfe Authentifizierungs-Token...");

            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader != null && TokenManager.isValidToken(authHeader)) {
                LoggingUtil.logInfo("ApiServer", "Token gültig. Anfrage wird weitergeleitet.");
                next.handle(exchange);
            } else {
                LoggingUtil.logWarning("ApiServer", "Ungültiges oder fehlendes Token.");
                String errorResponse = "{\"error\":\"Unauthorized: Invalid or missing authentication token.\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, errorResponse.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes());
                }
            }
        }
    }

    /**
     * Handler für die Verarbeitung von Anfragen an /api/folders.
     */
    static class FoldersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            LoggingUtil.logInfo("ApiServer", "Verarbeite Anfrage an /api/folders mit Methode: " + method);

            String response;

            switch (method) {
                case "GET" -> {
                    response = listFolders();
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                }
                case "POST" -> {
                    try {
                        response = createFolder(exchange);
                        exchange.sendResponseHeaders(201, response.getBytes().length);
                    } finally {
                        // Benachrichtige über Änderung, selbst wenn ein Fehler auftritt
                        LoggingUtil.logInfo("ApiServer", "POST Anfrage für Ordner abgeschlossen - sende Änderungsbenachrichtigung");
                        notifyChangeListeners("create_folder");
                    }
                }
                case "PUT" -> {
                    try {
                        response = updateFolder(exchange);
                        exchange.sendResponseHeaders(200, response.getBytes().length);
                    } finally {
                        // Benachrichtige über Änderung, selbst wenn ein Fehler auftritt
                        LoggingUtil.logInfo("ApiServer", "PUT Anfrage für Ordner abgeschlossen - sende Änderungsbenachrichtigung");
                        notifyChangeListeners("update_folder");
                    }
                }
                case "DELETE" -> {
                    try {
                        response = deleteFolder(exchange);
                        exchange.sendResponseHeaders(200, response.getBytes().length);
                    } finally {
                        // Benachrichtige über Änderung, selbst wenn ein Fehler auftritt
                        LoggingUtil.logInfo("ApiServer", "DELETE Anfrage für Ordner abgeschlossen - sende Änderungsbenachrichtigung");
                        notifyChangeListeners("delete_folder");
                    }
                }
                default -> {
                    response = "Methode nicht erlaubt.";
                    LoggingUtil.logWarning("ApiServer", "Methode nicht erlaubt: " + method);
                    exchange.sendResponseHeaders(405, response.getBytes().length);
                }
            }

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }

        private String listFolders() {
            LoggingUtil.logInfo("ApiServer", "Liste alle Ordner auf...");
            StringBuilder response = new StringBuilder("[");
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT id, name, COALESCE(parent_id, 0) AS parent_id FROM folders");
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    if (response.length() > 1) {
                        response.append(",");
                    }
                    response.append(String.format("{\"id\":%d,\"name\":\"%s\",\"parentFolderId\":%d}",
                            rs.getInt("id"), rs.getString("name"), rs.getInt("parent_id")));
                }
            } catch (SQLException e) {
                LoggingUtil.logError("ApiServer", "Datenbankfehler: " + e.getMessage());
                return "Datenbankfehler: " + e.getMessage();
            }
            response.append("]");
            LoggingUtil.logInfo("ApiServer", "Ordnerliste erstellt: " + response);
            return response.toString();
        }

        private String createFolder(HttpExchange exchange) {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                LoggingUtil.logInfo("ApiServer", "Empfangene Anfrage zum Erstellen eines Ordners: " + requestBody);

                // Extrahiere den Ordner-Namen und die übergeordnete Ordner-ID aus der Anfrage
                String folderName = JsonUtils.parseJson(requestBody, "name");
                int parentFolderId = requestBody.contains("parentFolderId")
                        ? Integer.parseInt(JsonUtils.parseJson(requestBody, "parentFolderId"))
                        : 0; // Default to root folder

                // Überprüfe die übergeordnete Ordner-ID
                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement validateStmt = conn.prepareStatement("SELECT COUNT(*) FROM folders WHERE id = ?")) {

                    validateStmt.setInt(1, parentFolderId);
                    try (ResultSet rs = validateStmt.executeQuery()) {
                        if (parentFolderId != 0 && (!rs.next() || rs.getInt(1) == 0)) {
                            return "Ungültige parentFolderId: Der übergeordnete Ordner existiert nicht.";
                        }
                    }
                }

                // Führe das Erstellen des Ordners aus
                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                             "INSERT INTO folders (name, parent_id) VALUES (?, ?)",
                             PreparedStatement.RETURN_GENERATED_KEYS)) {

                    stmt.setString(1, folderName);
                    stmt.setInt(2, parentFolderId);
                    stmt.executeUpdate();

                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            int folderId = generatedKeys.getInt(1);
                            LoggingUtil.logInfo("ApiServer", "Ordner erstellt mit ID: " + folderId);
                            return String.format("{\"id\":%d,\"name\":\"%s\",\"parentFolderId\":%d}", folderId, folderName, parentFolderId);
                        } else {
                            throw new SQLException("Erstellen des Ordners fehlgeschlagen, keine ID erhalten.");
                        }
                    }
                }
            } catch (IOException | SQLException e) {
                LoggingUtil.logError("ApiServer", "Fehler beim Erstellen des Ordners: " + e.getMessage());
                return "Fehler beim Erstellen des Ordners: " + e.getMessage();
            } catch (NumberFormatException e) {
                LoggingUtil.logError("ApiServer", "Ungültiges Format in der Anfrage: " + e.getMessage());
                return "Ungültiges Format in der Anfrage: " + e.getMessage();
            }
        }

        private String updateFolder(HttpExchange exchange) {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                LoggingUtil.logInfo("ApiServer", "Empfangene Anfrage zum Aktualisieren eines Ordners: " + requestBody);

                // Extrahiere die Ordner-ID und den neuen Namen aus der Anfrage 
                int folderId = Integer.parseInt(JsonUtils.parseJson(requestBody, "id"));
                String folderName = JsonUtils.parseJson(requestBody, "name");

                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement("UPDATE folders SET name = ? WHERE id = ?")) {

                    stmt.setString(1, folderName);
                    stmt.setInt(2, folderId);

                    int rowsUpdated = stmt.executeUpdate();
                    if (rowsUpdated > 0) {
                        LoggingUtil.logInfo("ApiServer", "Ordner erfolgreich aktualisiert: ID=" + folderId);
                        return String.format("{\"id\":%d,\"name\":\"%s\"}", folderId, folderName);
                    } else {
                        return "Ordner nicht gefunden.";
                    }
                }
            } catch (SQLException e) {
                LoggingUtil.logError("ApiServer", "Fehler beim Aktualisieren des Ordners: " + e.getMessage());
                return "Fehler beim Aktualisieren des Ordners: " + e.getMessage();
            } catch (NumberFormatException e) {
                LoggingUtil.logError("ApiServer", "Ungültiges Format in der Anfrage: " + e.getMessage());
                return "Ungültiges Format in der Anfrage: " + e.getMessage();
            } catch (IOException e) {
                LoggingUtil.logError("ApiServer", "Fehler beim Lesen der Anfrage: " + e.getMessage());
                return "Fehler beim Lesen der Anfrage: " + e.getMessage();
            }
        }

        private String deleteFolder(HttpExchange exchange) {
            try {
                String query = exchange.getRequestURI().getQuery();
                int folderId = Integer.parseInt(query.split("=")[1]);
                LoggingUtil.logInfo("ApiServer", "Empfangene Anfrage zum Löschen des Ordners mit ID: " + folderId);
                
                // Prüfe zuerst, ob der Ordner existiert
                boolean folderExists = false;
                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement checkStmt = conn.prepareStatement("SELECT COUNT(*) FROM folders WHERE id = ?")) {
                    
                    checkStmt.setInt(1, folderId);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            folderExists = rs.getInt(1) > 0;
                        }
                    }
                }
                
                if (!folderExists) {
                    return "Ordner nicht gefunden.";
                }
                
                // Prüfe, ob der Ordner Unterordner hat
                boolean hasSubfolders = false;
                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement subfolderStmt = conn.prepareStatement("SELECT COUNT(*) FROM folders WHERE parent_id = ?")) {
                    
                    subfolderStmt.setInt(1, folderId);
                    try (ResultSet rs = subfolderStmt.executeQuery()) {
                        if (rs.next()) {
                            hasSubfolders = rs.getInt(1) > 0;
                        }
                    }
                }
                
                // Prüfe, ob der Ordner Dateien enthält
                boolean hasFiles = false;
                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement filesStmt = conn.prepareStatement("SELECT COUNT(*) FROM files WHERE folder_id = ?")) {
                    
                    filesStmt.setInt(1, folderId);
                    try (ResultSet rs = filesStmt.executeQuery()) {
                        if (rs.next()) {
                            hasFiles = rs.getInt(1) > 0;
                        }
                    }
                }
                
                // Wenn der Ordner Unterordner oder Dateien hat, verhindere das Löschen über die API
                if (hasSubfolders || hasFiles) {
                    LoggingUtil.logWarning("ApiServer", "Versuch, einen Ordner mit Inhalt über die API zu löschen: ID=" + folderId);
                    return "Ordner mit Inhalt koennen nicht über die API geloescht werden. Bitte verwende die grafische Benutzeroberflaeche (GUI), um Ordner mit Unterordnern oder Dateien zu loeschen.";
                }

                // Ansonsten führe das Löschen durch
                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement("DELETE FROM folders WHERE id = ?")) {

                    stmt.setInt(1, folderId);

                    int rowsDeleted = stmt.executeUpdate();
                    if (rowsDeleted > 0) {
                        LoggingUtil.logInfo("ApiServer", "Ordner erfolgreich gelöscht: ID=" + folderId);
                        return "Ordner erfolgreich geloescht.";
                    } else {
                        return "Ordner konnte nicht geloescht werden.";
                    }
                }
            } catch (SQLException e) {
                LoggingUtil.logError("ApiServer", "Datenbankfehler beim Löschen des Ordners: " + e.getMessage());
                
                if (e.getMessage().contains("foreign key constraint")) {
                    return "Ordner mit Inhalt koennen nicht über die API geloescht werden. Bitte verwende die grafische Benutzeroberfläche (GUI), um Ordner mit Unterordnern oder Dateien zu loeschen.";
                }
                
                return "Datenbankfehler beim Loeschen des Ordners: " + e.getMessage();
            } catch (NumberFormatException e) {
                LoggingUtil.logError("ApiServer", "Ungültige Ordner-ID: " + e.getMessage());
                return "Ungültige Ordner-ID: " + e.getMessage();
            }
        }
    }

    /**
     * Handler für Dateioperationen.
     */
    static class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            LoggingUtil.logInfo("FileHandler", "Verarbeite Anfrage an /api/files mit Methode: " + method);

            String response;
            
            switch (method) {
                case "GET" -> {
                    response = listFiles();
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                }
                case "POST" -> {
                    try {
                        response = uploadFile(exchange);
                        exchange.sendResponseHeaders(201, response.getBytes().length);
                    } finally {
                        // Benachrichtige über Änderung, selbst wenn ein Fehler auftritt
                        LoggingUtil.logInfo("FileHandler", "POST Anfrage für Datei abgeschlossen - sende Änderungsbenachrichtigung");
                        notifyChangeListeners("upload_file");
                    }
                }
                case "DELETE" -> {
                    try {
                        response = deleteFile(exchange);
                        exchange.sendResponseHeaders(200, response.getBytes().length);
                    } finally {
                        // Benachrichtige über Änderung, selbst wenn ein Fehler auftritt
                        LoggingUtil.logInfo("FileHandler", "DELETE Anfrage für Datei abgeschlossen - sende Änderungsbenachrichtigung");
                        notifyChangeListeners("delete_file");
                    }
                }
                default -> {
                    response = "Methode nicht erlaubt.";
                    LoggingUtil.logWarning("FileHandler", "Methode nicht erlaubt: " + method);
                    exchange.sendResponseHeaders(405, response.getBytes().length);
                }
            }

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }

        private String listFiles() {
            try {
                FileStorage fileStorage = FileStorage.getInstance();
                LoggingUtil.logInfo("FileHandler", "Rufe alle Dateien aus allen Ordnern ab...");

                List<EncryptedFile> files = fileStorage.getAllFiles();

                if (files.isEmpty()) {
                    LoggingUtil.logInfo("FileHandler", "Keine Dateien gefunden.");
                    return "[]";
                }

                LoggingUtil.logInfo("FileHandler", "Anzahl der gefundenen Dateien: " + files.size());
                StringBuilder response = new StringBuilder("[");

                for (EncryptedFile file : files) {
                    LoggingUtil.logInfo("FileHandler", "Datei gefunden: ID=" + file.getId() + ", Name=" + file.getOriginalName());
                    if (response.length() > 1) {
                        response.append(",");
                    }
                    response.append(String.format("{\"id\":%d,\"name\":\"%s\",\"folderId\":%d}",
                            file.getId(), file.getOriginalName(), file.getFolderId()));
                }

                response.append("]");
                return response.toString();
            } catch (Exception e) {
                LoggingUtil.logError("FileHandler", "Fehler beim Abrufen aller Dateien: " + e.getMessage());
                return "Fehler beim Abrufen aller Dateien: " + e.getMessage();
            }
        }

        private String uploadFile(HttpExchange exchange) throws IOException {
            // Implementierung für das Hochladen von Dateien
            LoggingUtil.logInfo("ApiServer", "Implementierung für uploadFile fehlt");
            return "{\"success\": false, \"message\": \"Not implemented\"}";
        }
        
        private String deleteFile(HttpExchange exchange) throws IOException {
            // Implementierung für das Löschen von Dateien
            LoggingUtil.logInfo("ApiServer", "Implementierung für deleteFile fehlt");
            return "{\"success\": false, \"message\": \"Not implemented\"}";
        }
    }

    /**
     * Handler für das Web-Interface.
     */
    static class WebInterfaceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String htmlResponse = """
                    <!DOCTYPE html>
                    <html lang=\"en\">
                    <head>
                        <meta charset=\"UTF-8\">
                        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
                        <title>FileVault Web Interface</title>
                        <style>
                            body {
                                font-family: Arial, sans-serif;
                                margin: 0;
                                padding: 0;
                                background-color: #f4f4f9;
                                color: #333;
                            }
                            header {
                                background-color: #6200ea;
                                color: white;
                                padding: 1rem;
                                text-align: center;
                            }
                            main {
                                padding: 2rem;
                                max-width: 800px;
                                margin: auto;
                            }
                            .card {
                                background: white;
                                border-radius: 8px;
                                box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
                                margin-bottom: 1rem;
                                padding: 1rem;
                            }
                            button {
                                background-color: #6200ea;
                                color: white;
                                border: none;
                                padding: 0.5rem 1rem;
                                border-radius: 4px;
                                cursor: pointer;
                            }
                            button:hover {
                                background-color: #3700b3;
                            }
                            input {
                                padding: 0.5rem;
                                margin-right: 0.5rem;
                                border: 1px solid #ccc;
                                border-radius: 4px;
                            }
                            select {
                                padding: 0.5rem;
                                margin-right: 0.5rem;
                                border: 1px solid #ccc;
                                border-radius: 4px;
                                background-color: white;
                                min-width: 150px;
                            }
                            .path-text {
                                font-size: 0.8em;
                                color: #666;
                                font-style: italic;
                                margin-left: 5px;
                            }
                            .input-group {
                                margin-bottom: 1rem;
                                display: flex;
                                align-items: center;
                                flex-wrap: wrap;
                                gap: 0.5rem;
                            }
                        </style>
                        <script>
                            let token = null;

                            // Check if token exists in localStorage when page loads
                            document.addEventListener('DOMContentLoaded', () => {
                                const savedToken = localStorage.getItem('authToken');
                                if (savedToken) {
                                    token = savedToken;
                                    loadFolderOptions();
                                }
                            });

                            async function authenticate() {
                                const password = document.getElementById('password').value;
                                const response = await fetch('/api/auth', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({ password })
                                });

                                if (response.ok) {
                                    const data = await response.json();
                                    token = data.token;
                                    // Save token to localStorage to maintain authentication after refresh
                                    localStorage.setItem('authToken', token);
                                    alert('Authentication successful!');
                                    // Load folder options after successful authentication
                                    loadFolderOptions();
                                } else {
                                    alert('Authentication failed!');
                                }
                            }

                            async function callEndpoint(endpoint, method, body = null) {
                                if (!token) {
                                    alert('Please authenticate first!');
                                    return;
                                }

                                const options = {
                                    method,
                                    headers: { 'Authorization': token, 'Content-Type': 'application/json' },
                                };

                                if (body) {
                                    options.body = JSON.stringify(body);
                                }

                                try {
                                    const response = await fetch(endpoint, options);

                                    if (response.ok) {
                                        const data = await response.text();
                                        alert(`Response from ${endpoint}:\n${data}`);
                                        // Refresh the page after successful API call
                                        location.reload();
                                    } else if (response.status === 401) {
                                        // Clear invalid token
                                        token = null;
                                        localStorage.removeItem('authToken');
                                        alert('Authentication error: Invalid or missing token. Please login again.');
                                    } else {
                                        alert(`Error (${response.status}): Failed to access ${endpoint}`);
                                    }
                                } catch (error) {
                                    alert(`Network error: ${error.message}`);
                                }
                            }

                            function getInputValue(id) {
                                return document.getElementById(id).value;
                            }

                            async function listFolders() {
                                if (!token) {
                                    alert('Please authenticate first!');
                                    return;
                                }
                                
                                try {
                                    const response = await fetch('/api/folders', {
                                        method: 'GET',
                                        headers: { 'Authorization': token }
                                    });

                                    if (response.ok) {
                                        const folders = await response.json();
                                        alert(`Folders: ${JSON.stringify(folders)}`);
                                        // Refresh the page
                                        location.reload();
                                    } else if (response.status === 401) {
                                        // Clear invalid token
                                        token = null;
                                        localStorage.removeItem('authToken');
                                        alert('Authentication error: Invalid or missing token. Please login again.');
                                    } else {
                                        alert(`Error (${response.status}): Failed to list folders.`);
                                    }
                                } catch (error) {
                                    alert(`Network error: ${error.message}`);
                                }
                            }

                            async function loadFolderOptions() {
                                if (!token) {
                                    // Don't show alert here to avoid duplicate alerts
                                    return;
                                }
                                
                                const parentFolderSelect = document.getElementById('parentFolderId');
                                parentFolderSelect.innerHTML = '<option value="0">Root (No Parent)</option>';
                                
                                try {
                                    const response = await fetch('/api/folders', {
                                        method: 'GET',
                                        headers: { 'Authorization': token }
                                    });

                                    if (response.ok) {
                                        const folders = await response.json();
                                        
                                        // First build folder structure to calculate paths
                                        const folderMap = {};
                                        folders.forEach(folder => {
                                            folderMap[folder.id] = { 
                                                ...folder, 
                                                children: [] 
                                            };
                                        });
                                        
                                        // Build the folder tree
                                        folders.forEach(folder => {
                                            if (folder.parentFolderId && folder.parentFolderId !== 0 && folderMap[folder.parentFolderId]) {
                                                folderMap[folder.parentFolderId].children.push(folderMap[folder.id]);
                                            }
                                        });
                                        
                                        // Function to get folder path
                                        const getFolderPath = (folderId) => {
                                            let path = [];
                                            let currentId = folderId;
                                            
                                            while (currentId && currentId !== 0) {
                                                const folder = folderMap[currentId];
                                                if (!folder) break;
                                                
                                                path.unshift(folder.name);
                                                currentId = folder.parentFolderId;
                                            }
                                            
                                            return path.join(' / ');
                                        };
                                        
                                        // Add options with paths
                                        folders.forEach(folder => {
                                            const option = document.createElement('option');
                                            option.value = folder.id;
                                            
                                            const path = getFolderPath(folder.parentFolderId);
                                            
                                            // Include the path in the option text if it exists
                                            if (path) {
                                                option.textContent = `${folder.name} (${path})`;
                                            } else {
                                                option.textContent = folder.name;
                                            }
                                            
                                            parentFolderSelect.appendChild(option);
                                        });
                                    } else if (response.status === 401) {
                                        // Clear invalid token
                                        token = null;
                                        localStorage.removeItem('authToken');
                                        // Don't show alert here to avoid duplicate alerts when page loads
                                    } else {
                                        console.error(`Error loading folders: ${response.status}`);
                                    }
                                } catch (error) {
                                    console.error(`Network error loading folders: ${error.message}`);
                                }
                            }

                            async function createFolder() {
                                if (!token) {
                                    alert('Please authenticate first!');
                                    return;
                                }
                                
                                const folderName = getInputValue('folderName');
                                const parentFolderId = getInputValue('parentFolderId');
                                
                                const requestBody = { 
                                    name: folderName 
                                };
                                
                                if (parentFolderId && parentFolderId !== "0") {
                                    requestBody.parentFolderId = parseInt(parentFolderId);
                                }
                                
                                try {
                                    const response = await fetch('/api/folders', {
                                        method: 'POST',
                                        headers: { 'Authorization': token, 'Content-Type': 'application/json' },
                                        body: JSON.stringify(requestBody)
                                    });

                                    if (response.ok) {
                                        alert('Folder created successfully!');
                                        // Refresh the page after successful folder creation
                                        location.reload();
                                    } else if (response.status === 401) {
                                        // Clear invalid token
                                        token = null;
                                        localStorage.removeItem('authToken');
                                        alert('Authentication error: Invalid or missing token. Please login again.');
                                    } else {
                                        const errorText = await response.text();
                                        alert(`Failed to create folder: ${errorText}`);
                                    }
                                } catch (error) {
                                    alert(`Network error: ${error.message}`);
                                }
                            }

                            async function deleteFolder() {
                                if (!token) {
                                    alert('Please authenticate first!');
                                    return;
                                }
                                
                                const folderId = getInputValue('folderIdToDelete');
                                
                                try {
                                    const response = await fetch(`/api/folders?id=${folderId}`, {
                                        method: 'DELETE',
                                        headers: { 'Authorization': token }
                                    });

                                    if (response.ok) {
                                        alert('Folder deleted successfully!');
                                        location.reload();
                                    } else if (response.status === 401) {
                                        // Clear invalid token
                                        token = null;
                                        localStorage.removeItem('authToken');
                                        alert('Authentication error: Invalid or missing token. Please login again.');
                                    } else {
                                        const errorText = await response.text();
                                        alert(`Failed to delete folder: ${errorText}`);
                                    }
                                } catch (error) {
                                    alert(`Network error: ${error.message}`);
                                }
                            }
                        </script>
                    </head>
                    <body>
                        <header>
                            <h1>FileVault Web Interface</h1>
                        </header>
                        <main>
                            <div class="card">
                                <h2>Authenticate</h2>
                                <input type="password" id="password" placeholder="Enter password" />
                                <button onclick="authenticate()">Authenticate</button>
                            </div>
                            <div class="card">
                                <h2>Files</h2>
                                <div class="input-group">
                                    <button onclick="callEndpoint('/api/files', 'GET')">List Files</button>
                                </div>
                            </div>
                            <div class="card">
                                <h2>Folders</h2>
                                <div class="input-group">
                                    <button onclick="listFolders()">List Folders</button>
                                </div>
                                <div class="input-group">
                                    <input type="text" id="folderName" placeholder="Folder Name" />
                                    <label for="parentFolderId">Parent Folder:</label>
                                    <select id="parentFolderId">
                                        <option value="0">Root (No Parent)</option>
                                    </select>
                                    <button onclick="createFolder()">Create Folder</button>
                                </div>
                                <div class="input-group">
                                    <input type="number" id="folderId" placeholder="Folder ID" />
                                    <input type="text" id="updatedFolderName" placeholder="New Folder Name" />
                                    <button onclick="callEndpoint('/api/folders', 'PUT', { id: getInputValue('folderId'), name: getInputValue('updatedFolderName') })">Update Folder</button>
                                </div>
                                <div class="input-group">
                                    <input type="number" id="folderIdToDelete" placeholder="Folder ID" />
                                    <button onclick="callEndpoint(`/api/folders?id=${getInputValue('folderIdToDelete')}`, 'DELETE')">Delete Folder</button>
                                </div>
                            </div>
                        </main>
                    </body>
                    </html>
                """;

                exchange.sendResponseHeaders(200, htmlResponse.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(htmlResponse.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }
}