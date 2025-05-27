package com.filevault;

import java.io.IOException;
import java.util.prefs.Preferences;

import com.filevault.api.ApiServer;
import com.filevault.storage.DatabaseManager;
import com.filevault.util.LoggingUtil;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Hauptklasse der FileVault-Anwendung
 */
public class FileVaultApp extends Application {
    
    /** Konstante für den Eintrag des Theme in den Benutzereinstellungen */
    private static final String PREF_DARK_MODE = "darkMode";
    
    /** Haupt-Stage der Anwendung */
    private static Stage primaryStage;
    
    /** Szene für die Login-Ansicht */
    private static Scene loginScene;
    
    /** Szene für die Hauptansicht */
    private static Scene mainScene;
    
    /** Benutzereinstellungen */
    private static Preferences prefs;
    
    /** Flag, das angibt, ob das Dark Mode aktiv ist */
    private static boolean isDarkMode;
    
    /** Die API-Server Instanz */
    private static ApiServer apiServer;

    @Override
    public void start(Stage stage) throws Exception {
        LoggingUtil.configureLogger();
        LoggingUtil.logInfo("FileVaultApp", "Starting FileVault application");
        
        primaryStage = stage;
        prefs = Preferences.userNodeForPackage(FileVaultApp.class);
        isDarkMode = prefs.getBoolean(PREF_DARK_MODE, false);
        
        try {
            // Lade Login-Ansicht
            Parent loginRoot = loadFXML("login");
            loginScene = new Scene(loginRoot);
            
            // Füge CSS-Datei hinzu
            String mainCssPath = getClass().getResource("style.css").toExternalForm();
            loginScene.getStylesheets().add(mainCssPath);
            
            // Add appropriate theme CSS
            if (isDarkMode) {
                String darkThemeCssPath = getClass().getResource("css/dark-theme.css").toExternalForm();
                loginScene.getStylesheets().add(darkThemeCssPath);
                loginRoot.getStyleClass().add("dark-theme");
            } else {
                String lightThemeCssPath = getClass().getResource("css/light-theme.css").toExternalForm();
                loginScene.getStylesheets().add(lightThemeCssPath);
            }
            
            // Setze Titel und Icon
            primaryStage.setTitle("FileVault");
            try {
                Image icon = new Image(getClass().getResourceAsStream("icons/icon.png"));
                primaryStage.getIcons().add(icon);
            } catch (Exception e) {
                LoggingUtil.logError("FileVaultApp", "Could not load application icon: " + e.getMessage());
            }
            
            // Konfiguriere Fenster
            primaryStage.setScene(loginScene);
            primaryStage.setMinWidth(600);
            primaryStage.setMinHeight(500);
            primaryStage.setResizable(true);
            
            // Füge Close-Handler hinzu
            primaryStage.setOnCloseRequest(event -> {
                LoggingUtil.logInfo("FileVaultApp", "Application closing");
                Platform.exit();
            });
            
            // Zeige Anwendung an
            primaryStage.show();
            
            // Apply initial fade-in animation
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), loginRoot);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
            
        } catch (IOException e) {
            LoggingUtil.logError("FileVaultApp", "Error loading FXML: " + e.getMessage());
            showError("Error loading application layout: " + e.getMessage());
        } catch (Exception e) {
            LoggingUtil.logError("FileVaultApp", "Unexpected error: " + e.getMessage());
            showError("Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Lädt eine FXML-Datei
     * 
     * @param fxml Der Name der FXML-Datei (ohne Erweiterung)
     * @return Parent-Knoten der geladenen FXML-Datei
     * @throws IOException Falls ein Fehler beim Laden der Datei auftritt
     */
    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(FileVaultApp.class.getResource(fxml + ".fxml"));
        Parent root = fxmlLoader.load();
        
        // Speichere den Controller im Root-Element für spätere Verwendung
        Object controller = fxmlLoader.getController();
        if (controller != null) {
            root.setUserData(controller);
        }
        
        return root;
    }
    
    /**
     * Zeigt die Hauptansicht der Anwendung
     * 
     * @throws IOException Falls ein Fehler beim Laden der Hauptansicht auftritt
     */
    public static void showMainView() throws IOException {
        LoggingUtil.logInfo("FileVaultApp", "Loading main view");
        
        if (primaryStage == null) {
            LoggingUtil.logError("FileVaultApp", "Primary stage is null");
            return;
        }
        
        if (loginScene != null) {
            // Animation zum Ausblenden der Login-Szene
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), loginScene.getRoot());
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(event -> {
                try {
                    loadAndShowMainView();
                } catch (IOException e) {
                    LoggingUtil.logError("FileVaultApp", "Error loading main view: " + e.getMessage());
                    showError("Error loading main view: " + e.getMessage());
                }
            });
            fadeOut.play();
        } else {
            loadAndShowMainView();
        }
    }
    
    /**
     * Hilfsmethode zum Laden und Anzeigen der Hauptansicht mit Animation
     * 
     * @throws IOException Falls ein Fehler beim Laden der Hauptansicht auftritt
     */
    private static void loadAndShowMainView() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(FileVaultApp.class.getResource("main.fxml"));
        Parent mainRoot = fxmlLoader.load();
        
        // Speichere den Controller im Root-Element für spätere Verwendung
        Object controller = fxmlLoader.getController();
        mainRoot.setUserData(controller);
        
        // Aktuellen Theme anwenden
        if (isDarkMode) {
            mainRoot.getStyleClass().add("dark-theme");
        }
        
        // Initial transparent für Animation
        mainRoot.setOpacity(0);
        
        // Neue Szene erstellen oder vorhandene aktualisieren
        if (mainScene == null) {
            mainScene = new Scene(mainRoot);
            
            // Füge Haupt-CSS hinzu
            mainScene.getStylesheets().add(FileVaultApp.class.getResource("style.css").toExternalForm());
            
            if (isDarkMode) {
                String darkThemeCssPath = FileVaultApp.class.getResource("css/dark-theme.css").toExternalForm();
                mainScene.getStylesheets().add(darkThemeCssPath);
            } else {
                String lightThemeCssPath = FileVaultApp.class.getResource("css/light-theme.css").toExternalForm();
                mainScene.getStylesheets().add(lightThemeCssPath);
            }
        } else {
            mainScene.setRoot(mainRoot);
        }
        
        // Szene wechseln
        primaryStage.setScene(mainScene);
        
        // Animation zum Einfaden
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), mainRoot);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }
    
    /**
     * Wechselt zwischen Licht- und Dunkel-Modus
     * 
     * @param darkMode true für Dunkel-Modus, false für Licht-Modus
     */
    public static void toggleTheme(boolean darkMode) {
        LoggingUtil.logInfo("FileVaultApp", "Toggling theme. Dark mode: " + darkMode);
        isDarkMode = darkMode;
        
        // Speichere Einstellung
        prefs.putBoolean(PREF_DARK_MODE, darkMode);
        
        // Aktualisiere Styling der aktiven Szene
        Scene currentScene = primaryStage.getScene();
        if (currentScene != null) {
            Parent root = currentScene.getRoot();
            
            // Erstelle Übergangseffekt für Theme-Änderung
            FadeTransition fadeOut = new FadeTransition(Duration.millis(100), root);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.9);
            
            FadeTransition fadeIn = new FadeTransition(Duration.millis(100), root);
            fadeIn.setFromValue(0.9);
            fadeIn.setToValue(1.0);
            
            fadeOut.setOnFinished(event -> {
                // Aktualisiere Styling
                if (darkMode) {
                    if (!root.getStyleClass().contains("dark-theme")) {
                        root.getStyleClass().add("dark-theme");
                    }
                    
                    // Entferne Licht-Theme und füge Dunkel-Theme hinzu
                    currentScene.getStylesheets().removeIf(css -> css.contains("light-theme.css"));
                    String darkThemeCssPath = FileVaultApp.class.getResource("css/dark-theme.css").toExternalForm();
                    if (!currentScene.getStylesheets().contains(darkThemeCssPath)) {
                        currentScene.getStylesheets().add(darkThemeCssPath);
                    }
                } else {
                    root.getStyleClass().remove("dark-theme");
                    
                    // Entferne Dunkel-Theme und füge Licht-Theme hinzu
                    currentScene.getStylesheets().removeIf(css -> css.contains("dark-theme.css"));
                    String lightThemeCssPath = FileVaultApp.class.getResource("css/light-theme.css").toExternalForm();
                    if (!currentScene.getStylesheets().contains(lightThemeCssPath)) {
                        currentScene.getStylesheets().add(lightThemeCssPath);
                    }
                }
                fadeIn.play();
            });
            
            fadeOut.play();
        }
    }
    
    /**
     * Gibt zurück, ob der Dark Mode aktiv ist
     * 
     * @return true wenn der Dark Mode aktiv ist, sonst false
     */
    public static boolean isDarkMode() {
        return isDarkMode;
    }
    
    /**
     * Zeigt einen Fehler-Dialog an
     * 
     * @param message Die anzuzeigende Fehlermeldung
     */
    private static void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("An error occurred");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Hauptmethode zum Starten der Anwendung.
     *
     * @param args Kommandozeilenparameter
     */
    public static void main(String[] args) {
        int apiPort = 9090; // Standardport für API
        
        // Überprüfe API-Port-Parameter
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--api-port") && i + 1 < args.length) {
                try {
                    apiPort = Integer.parseInt(args[i + 1]);
                    break;
                } catch (NumberFormatException e) {
                    LoggingUtil.logWarning("FileVaultApp", "Ungültiger API-Port. Der Standardport 9090 wird verwendet.");
                }
            }
        }
        
        // Initialisiere Datenbank
        DatabaseManager.initDatabase();
        
        // Starte API-Server
        startApiServer(apiPort);
        
        // Starte die JavaFX-Anwendung
        launch(args);
    }
    
    /**
     * Startet den API-Server.
     *
     * @param port Der Port, auf dem der API-Server laufen soll
     */
    private static void startApiServer(int port) {
        try {
            LoggingUtil.logInfo("FileVaultApp", "Starting API server on port " + port);
            apiServer = new ApiServer();
            apiServer.start(port);
            
            // Registriere Shutdown-Hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (apiServer != null) {
                    LoggingUtil.logInfo("FileVaultApp", "Stopping API server");
                    apiServer.stop();
                }
            }));
            
            // Wichtige Benutzerbenachrichtigung beibehalten
            System.out.println("\n============================================================");
            System.out.println("API-Server erfolgreich gestartet auf Port " + port);
            System.out.println("Änderungen über die API werden automatisch in der GUI angezeigt");
            System.out.println("============================================================\n");
            
            LoggingUtil.logInfo("FileVaultApp", "API server started successfully");
        } catch (IOException e) {
            LoggingUtil.logError("FileVaultApp", "Error starting API server: " + e.getMessage());
            final String errorMsg = "Error starting API server: " + e.getMessage();
            Platform.runLater(() -> {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("API Server Error");
                alert.setHeaderText("Failed to start API server");
                alert.setContentText(errorMsg);
                alert.showAndWait();
            });
        } catch (Exception e) {
            LoggingUtil.logError("FileVaultApp", "Unexpected error starting API server: " + e.getMessage());
            final String errorMsg = "Unexpected error starting API server: " + e.getMessage();
            Platform.runLater(() -> {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("API Server Error");
                alert.setHeaderText("Unexpected error");
                alert.setContentText(errorMsg);
                alert.showAndWait();
            });
        }
    }
    
    /**
     * Wird aufgerufen, wenn die Anwendung beendet wird.
     * Beendet den API-Server und führt Aufräumarbeiten durch.
     */
    @Override
    public void stop() {
        LoggingUtil.logInfo("FileVaultApp", "Application stopping");
        
        // Stoppe API-Server beim Beenden der Anwendung
        if (apiServer != null) {
            LoggingUtil.logInfo("FileVaultApp", "Stopping API server");
            apiServer.stop();
        }
        
        // Bereinigung für alle Controller
        try {
            if (mainScene != null && mainScene.getRoot() != null) {
                Object controller = mainScene.getRoot().getUserData();
                if (controller instanceof com.filevault.controller.MainController mainController) {
                    LoggingUtil.logInfo("FileVaultApp", "Cleaning up MainController");
                    mainController.cleanup();
                }
            }
        } catch (Exception e) {
            LoggingUtil.logError("FileVaultApp", "Error during controller cleanup: " + e.getMessage());
        }
    }
}