package com.filevault.controller;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.filevault.FileVaultApp;
import com.filevault.model.EncryptedFile;
import com.filevault.model.UserManager;
import com.filevault.model.VirtualFolder;
import com.filevault.storage.FileStorage;
import com.filevault.util.FolderManager;
import com.filevault.util.LoggingUtil;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.Pair;

/**
 * Controller für die Hauptansicht der Anwendung.
 * Verwaltet die Interaktion mit Ordnern und Dateien im Tresor.
 */
public class MainController {

    /** TreeView für die Anzeige der Ordner */
    @FXML
    private TreeView<VirtualFolder> folderTreeView;

    /** TableView für die Anzeige der Dateien */
    @FXML
    private TableView<Object> fileTableView;

    /** Spalte für den Dateinamen */
    @FXML
    private TableColumn<Object, String> fileNameColumn;

    /** Spalte für die Dateigröße */
    @FXML
    private TableColumn<Object, String> fileSizeColumn;

    /** Spalte für das Erstellungsdatum */
    @FXML
    private TableColumn<Object, String> fileDateColumn;

    /** Label für den aktuellen Ordner */
    @FXML
    private Label currentFolderLabel;

    /** Label für Statusmeldungen */
    @FXML
    private Label statusLabel;

    /** Button für Theme-Toggle */
    @FXML
    private Button themeToggleButton;

    /** Button für Refresh */
    @FXML
    private Button refreshButton;

    /** Formatierer für Datumsangaben */
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Initialisiert den Controller und die Benutzeroberfläche.
     * Richtet die Ordnerliste und Dateitabelle ein.
     */
    @FXML
    public void initialize() {
        LoggingUtil.logInfo("MainController", "MainController initialized.");
        // Initialize folder tree
        refreshFolderTree();
        
        // Set up custom cell factory for folder tree
        folderTreeView.setCellFactory(tv -> new TreeCell<VirtualFolder>() {
            @Override
            protected void updateItem(VirtualFolder folder, boolean empty) {
                super.updateItem(folder, empty);
                if (empty || folder == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox vbox = new VBox(2);
                    Label nameLabel = new Label(folder.getName());
                    nameLabel.setStyle("-fx-font-weight: bold;");
                    Label descLabel = new Label(folder.getDescription());
                    descLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 0.9em;");
                    vbox.getChildren().addAll(nameLabel, descLabel);
                    setGraphic(vbox);
                }
            }
        });

        // Initialize button effects
        setupButtonEffects();
        
        // Set up file table columns
        fileNameColumn.setCellValueFactory(data -> {
            if (data.getValue() instanceof VirtualFolder virtualFolder) {
                return new SimpleStringProperty(virtualFolder.getName());
            } else {
                return new SimpleStringProperty(((EncryptedFile) data.getValue()).getOriginalName());
            }
        });
        
        fileSizeColumn.setCellValueFactory(data -> {
            if (data.getValue() instanceof VirtualFolder) {
                return new SimpleStringProperty("[Ordner]");
            } else {
                return new SimpleStringProperty(((EncryptedFile) data.getValue()).getFormattedSize());
            }
        });
        
        fileDateColumn.setCellValueFactory(data -> {
            if (data.getValue() instanceof VirtualFolder folder) {
                return new SimpleStringProperty(folder.getCreatedAt() != null ? 
                    folder.getCreatedAt().format(dateFormatter) : "");
            } else {
                EncryptedFile file = (EncryptedFile) data.getValue();
                if (file.getCreatedAt() != null) {
                    return new SimpleStringProperty(file.getCreatedAt().format(dateFormatter));
                } else {
                    return new SimpleStringProperty("");
                }
            }
        });

        // Add tooltips for folders in the file table and handle right-click options for folders
        fileTableView.setRowFactory(tv -> {
            TableRow<Object> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();

            MenuItem renameItem = new MenuItem("Rename");
            renameItem.setOnAction(event -> {
                Object selectedItem = row.getItem();
                if (selectedItem instanceof VirtualFolder folder) {
                    handleRenameFolder(folder);
                } else if (selectedItem instanceof EncryptedFile file) {
                    handleRenameFile(file);
                }
            });
            renameItem.getStyleClass().add("rename-context-item");

            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setOnAction(event -> handleDeleteFile());
            deleteItem.getStyleClass().add("delete-context-item");

            contextMenu.getItems().addAll(renameItem, deleteItem);

            // Show context menu only for non-empty rows
            row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(contextMenu)
            );

            // Add tooltips for folders
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem instanceof VirtualFolder folder) {
                    row.setTooltip(new Tooltip(folder.getDescription()));
                }
            });

            return row;
        });

        // Register the refresh handler for the file table
        fileTableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                handleFileSelection(event);
            }
        });
        
        // Initialisiere den Theme-Toggle-Button
        initializeThemeToggle();
        
        // Initialisiere den Refresh-Button
        initializeRefreshButton();
        
        // Registriere API-Change-Listener
        registerApiChangeListener();
        
        LoggingUtil.logInfo("MainController", "UI initialization complete.");
    }
    
    /**
     * Registriert einen Listener für API-Änderungen, um die UI automatisch zu aktualisieren
     */
    private void registerApiChangeListener() {
        try {
            // Erstelle einen Consumer, der die UI aktualisiert
            Consumer<String> apiChangeListener = action -> {
                LoggingUtil.logInfo("MainController", "API change detected: " + action);
                
                // Stelle sicher, dass wir auf dem JavaFX Application Thread sind
                if (!Platform.isFxApplicationThread()) {
                    Platform.runLater(() -> handleApiChange(action));
                } else {
                    handleApiChange(action);
                }
            };
            
            // Registriere den Listener beim API-Server
            com.filevault.api.ApiServer.addChangeListener(apiChangeListener);
            
            // Speichere den Listener als Instanzvariable, damit er später entfernt werden kann
            this.apiChangeListener = apiChangeListener;
            
            LoggingUtil.logInfo("MainController", "API change listener registered successfully");
        } catch (Exception e) {
            LoggingUtil.logError("MainController", "Error registering API change listener: " + e.getMessage());
        }
    }
    
    /**
     * Verarbeitet eine Änderung von der API
     * @param action Die Art der Änderung
     */
    private void handleApiChange(String action) {
        try {
            LoggingUtil.logInfo("MainController", "Handling API change: " + action + " - UI wird aktualisiert");
            
            // Führe ein vollständiges UI-Update durch
            refreshUI();
            
            // Aktualisiere die Statusanzeige
            if (statusLabel != null) {
                statusLabel.setText("Änderung über API erkannt: " + action);
                
                // Lass den Status kurz blinken, um die Änderung hervorzuheben
                FadeTransition fadeOut = new FadeTransition(Duration.millis(100), statusLabel);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.5);
                
                FadeTransition fadeIn = new FadeTransition(Duration.millis(100), statusLabel);
                fadeIn.setFromValue(0.5);
                fadeIn.setToValue(1.0);
                
                fadeOut.setOnFinished(e -> fadeIn.play());
                fadeOut.play();
            }
            
            LoggingUtil.logInfo("MainController", "UI refresh completed after API change: " + action);
        } catch (Exception e) {
            LoggingUtil.logError("MainController", "Error handling API change: " + e.getMessage());
            if (statusLabel != null) {
                statusLabel.setText("Fehler bei API-Änderung: " + e.getMessage());
            }
        }
    }
    
    // Listener für API-Änderungen
    private Consumer<String> apiChangeListener;
    
    /**
     * Wird aufgerufen, wenn der Controller nicht mehr benötigt wird
     */
    public void cleanup() {
        // Entferne den API-Change-Listener, wenn vorhanden
        if (apiChangeListener != null) {
            try {
                com.filevault.api.ApiServer.removeChangeListener(apiChangeListener);
                LoggingUtil.logInfo("MainController", "API change listener unregistered");
            } catch (Exception e) {
                LoggingUtil.logError("MainController", "Error removing API change listener: " + e.getMessage());
            }
        }
    }

    /**
     * Initialize button animation effects.
     */
    private void setupButtonEffects() {
        LoggingUtil.logInfo("MainController", "Setting up button effects.");
        
        // Find all buttons in the scene and add hover effects
        Platform.runLater(() -> {
            if (fileTableView == null || fileTableView.getScene() == null) {
                LoggingUtil.logError("MainController", "Scene not yet available for button effects");
                return;
            }
            
            fileTableView.getScene().getRoot().lookupAll(".button").forEach(node -> {
                if (node instanceof Button button) {
                    // Skip the refreshButton as it already has animations
                    if (button == refreshButton || button == themeToggleButton) {
                        return;
                    }
                    
                    // Create subtle scale transition for hover
                    javafx.animation.ScaleTransition scaleIn = new javafx.animation.ScaleTransition(
                            Duration.millis(200), button);
                    scaleIn.setToX(1.05);
                    scaleIn.setToY(1.05);
                    scaleIn.setInterpolator(Interpolator.EASE_OUT);
                    
                    javafx.animation.ScaleTransition scaleOut = new javafx.animation.ScaleTransition(
                            Duration.millis(150), button);
                    scaleOut.setToX(1.0);
                    scaleOut.setToY(1.0);
                    scaleOut.setInterpolator(Interpolator.EASE_IN);
                    
                    // Apply slight brightness effect on hover for better icon visibility
                    button.setOnMouseEntered(e -> {
                        scaleIn.playFromStart();
                        // Find icon label inside button
                        button.lookupAll(".icon").forEach(iconNode -> {
                            if (iconNode instanceof Label iconLabel) {
                                FadeTransition fadeIn = new FadeTransition(Duration.millis(200), iconLabel);
                                fadeIn.setToValue(1.0);
                                fadeIn.setInterpolator(Interpolator.EASE_OUT);
                                fadeIn.play();
                            }
                        });
                    });
                    
                    button.setOnMouseExited(e -> {
                        scaleOut.playFromStart();
                        // Reset icon label
                        button.lookupAll(".icon").forEach(iconNode -> {
                            if (iconNode instanceof Label iconLabel) {
                                FadeTransition fadeOut = new FadeTransition(Duration.millis(150), iconLabel);
                                fadeOut.setToValue(0.8);
                                fadeOut.setInterpolator(Interpolator.EASE_IN);
                                fadeOut.play();
                            }
                        });
                    });
                }
            });
        });
    }

    /**
     * Initialisiert den Theme-Toggle-Button
     */
    private void initializeThemeToggle() {
        LoggingUtil.logInfo("MainController", "Initializing theme toggle button");
        
        // Set initial button text based on current theme
        updateThemeToggleText(FileVaultApp.isDarkMode());
        
        // Add click event handler
        themeToggleButton.setOnAction(event -> {
            boolean newDarkMode = !FileVaultApp.isDarkMode();
            
            // Apply rotation animation to the button
            RotateTransition rotateTransition = new RotateTransition(Duration.millis(500), themeToggleButton);
            rotateTransition.setByAngle(newDarkMode ? 360 : -360);
            rotateTransition.setCycleCount(1);
            rotateTransition.setInterpolator(Interpolator.EASE_BOTH);
            
            // Toggle theme and update button text
            rotateTransition.setOnFinished(e -> updateThemeToggleText(newDarkMode));
            rotateTransition.play();
            
            // Toggle theme in app
            FileVaultApp.toggleTheme(newDarkMode);
        });
    }
    
    /**
     * Updates the theme toggle button text with appropriate emoji
     */
    private void updateThemeToggleText(boolean isDarkMode) {
        themeToggleButton.setText(isDarkMode ? "☀️" : "🌕");
        themeToggleButton.setTooltip(new Tooltip(isDarkMode ? "Light Mode" : "Dark Mode"));
    }

    /**
     * Aktualisiert die Baumansicht der Ordner.
     */
    private void refreshFolderTree() {
        LoggingUtil.logInfo("MainController", "Refreshing folder tree.");
        // Create a single root folder
        VirtualFolder rootFolder = new VirtualFolder(-1, "Root", "Root folder", null);
        TreeItem<VirtualFolder> rootItem = new TreeItem<>(rootFolder);
        folderTreeView.setRoot(rootItem);
        folderTreeView.setShowRoot(true);

        // Populate the root folder with other folders
        List<VirtualFolder> folders = FolderManager.getInstance().getFolders();
        for (VirtualFolder folder : folders) {
            if (folder.getParentId() == null) {
                TreeItem<VirtualFolder> folderItem = createTreeItem(folder, folders);
                rootItem.getChildren().add(folderItem);
            }
        }

        // Allow root folder to be selectable
        folderTreeView.getSelectionModel().select(rootItem);
        LoggingUtil.logInfo("MainController", "Folder tree refreshed.");
    }
    
    /**
     * Erstellt einen TreeItem für einen Ordner und seine Unterordner.
     * 
     * @param folder Der Ordner
     * @param allFolders Die Liste aller Ordner
     * @return Der erstellte TreeItem
     */
    private TreeItem<VirtualFolder> createTreeItem(VirtualFolder folder, List<VirtualFolder> allFolders) {
        TreeItem<VirtualFolder> item = new TreeItem<>(folder);
        
        // Add all children
        for (VirtualFolder child : folder.getChildren()) {
            TreeItem<VirtualFolder> childItem = createTreeItem(child, allFolders);
            item.getChildren().add(childItem);
        }
        
        return item;
    }

    /**
     * Verarbeitet die Auswahl eines Ordners.
     * 
     * @param event Das auslösende Mausereignis
     */
    @FXML
    public void handleFolderSelection(MouseEvent event) {
        TreeItem<VirtualFolder> selectedItem = folderTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null && selectedItem.getValue() != null) {
            VirtualFolder selectedFolder = selectedItem.getValue();
            LoggingUtil.logInfo("MainController", "Folder selected: " + selectedFolder.getName());
            
            // Check if this is the root folder
            if (selectedFolder.getId() == -1) {
                // For root folder, show the top-level folders and their files
                currentFolderLabel.setText(selectedFolder.getName());
                
                // Get only top-level folders
                List<VirtualFolder> topFolders = new ArrayList<>();
                for (VirtualFolder folder : FolderManager.getInstance().getFolders()) {
                    if (folder.getParentId() == null) {
                        topFolders.add(folder);
                    }
                }
                
                fileTableView.setItems(FXCollections.observableArrayList(topFolders));
                LoggingUtil.logInfo("MainController", "Root folder selected, showing top-level folders");
            } else {
                // Normal folder selection
                FolderManager.getInstance().setCurrentFolder(selectedFolder);
                refreshFileList();
            }
        }
    }

    /**
     * Verarbeitet die Auswahl einer Datei.
     * Bei Doppelklick wird die Datei exportiert.
     * 
     * @param event Das auslösende Mausereignis
     */
    @FXML
    public void handleFileSelection(MouseEvent event) {
        if (event != null && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            LoggingUtil.logInfo("MainController", "File double-clicked for export.");
            handleExportFile();
        }
    }

    /**
     * Öffnet einen Datei-Auswahldialog, um eine Datei zu importieren.
     */
    @FXML
    private void handleImportFile() {
        LoggingUtil.logInfo("MainController", "Import file dialog opened");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Datei importieren");
        
        // Get current window from any control in the scene
        Window currentWindow = folderTreeView.getScene().getWindow();
        File file = fileChooser.showOpenDialog(currentWindow);
        
        if (file != null) {
            importFile(file);
        }
    }

    /**
     * Öffnet einen Verzeichnis-Auswahldialog, um einen Ordner zu importieren.
     */
    @FXML
    private void handleImportFolder() {
        LoggingUtil.logInfo("MainController", "Import folder dialog opened");
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Ordner importieren");
        
        // Get current window from any control in the scene
        Window currentWindow = folderTreeView.getScene().getWindow();
        File directory = directoryChooser.showDialog(currentWindow);
        
        if (directory != null) {
            importFolder(directory);
        }
    }

    /**
     * Öffnet einen Dialog zum Exportieren einer Datei.
     */
    @FXML
    private void handleExportFile() {
        LoggingUtil.logInfo("MainController", "Export file dialog opened");
        EncryptedFile selectedFile = (EncryptedFile) fileTableView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showAlert(Alert.AlertType.WARNING, "Keine Datei ausgewählt", "Bitte wählen Sie eine Datei zum Exportieren aus.");
            return;
        }
        
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Exportieren nach");
        
        // Get current window from any control in the scene
        Window currentWindow = folderTreeView.getScene().getWindow();
        File directory = directoryChooser.showDialog(currentWindow);
        
        if (directory != null) {
            exportFile(selectedFile, directory);
        }
    }

    /**
     * Benennt eine Datei um.
     * Zeigt einen Dialog zur Eingabe des neuen Namens.
     */
    @FXML
    public void handleRenameFile(EncryptedFile file) {
        if (file == null) {
            showAlert(Alert.AlertType.WARNING, "Keine Datei ausgewählt", "Bitte wählen Sie eine Datei zum Umbenennen aus.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(file.getOriginalName());
        dialog.setTitle("Datei umbenennen");
        dialog.setHeaderText("Geben Sie einen neuen Namen für die Datei ein");
        dialog.setContentText("Neuer Name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            if (!newName.isEmpty()) {
                try {
                    boolean success = FileStorage.getInstance().renameFile(file, newName);
                    if (success) {
                        refreshFileList();
                        statusLabel.setText("Datei erfolgreich umbenannt.");
                    } else {
                        statusLabel.setText("Umbenennen der Datei fehlgeschlagen.");
                    }
                } catch (Exception e) {
                    statusLabel.setText("Fehler beim Umbenennen: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    public void handleRenameFile(ActionEvent event) {
        Object selectedItem = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedItem instanceof EncryptedFile file) {
            handleRenameFile(file);
        } else if (selectedItem instanceof VirtualFolder folder) {
            handleRenameFolder(folder);
        } else {
            showAlert(Alert.AlertType.WARNING, "Kein Element ausgewählt", "Bitte wählen Sie eine Datei oder einen Ordner zum Umbenennen aus.");
        }
    }
    
    /**
     * Löscht eine Datei aus dem Tresor.
     * Zeigt einen Bestätigungsdialog vor dem Löschen.
     */
    @FXML
    public void handleDeleteFile() {
        Object selectedItem = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Kein Element ausgewählt", "Bitte wählen Sie eine Datei oder einen Ordner zum Löschen aus.");
            return;
        }

        String itemName = selectedItem instanceof VirtualFolder ? 
            ((VirtualFolder) selectedItem).getName() : 
            ((EncryptedFile) selectedItem).getOriginalName();

        String confirmMessage;
        boolean isRecursiveDelete = false;

        if (selectedItem instanceof VirtualFolder folder) {
            // Prüfen, ob Unterordner vorhanden sind
            List<VirtualFolder> subfolders = FolderManager.getInstance().getSubfolders(folder.getId());
            isRecursiveDelete = !subfolders.isEmpty();
            
            if (isRecursiveDelete) {
                confirmMessage = "Der Ordner '" + folder.getName() + "' enthält Unterordner.\n" +
                       "Möchten Sie diesen Ordner und ALLE darin enthaltenen Unterordner und Dateien löschen?\n" +
                       "WARNUNG: Diese Aktion kann nicht rückgängig gemacht werden!";
            } else {
                confirmMessage = "Möchten Sie den Ordner '" + folder.getName() + "' wirklich löschen?";
            }
        } else {
            confirmMessage = "Möchten Sie die Datei '" + itemName + "' wirklich löschen?";
        }

        if (!showConfirmationDialog("Löschen bestätigen", confirmMessage)) {
            return;
        }

        try {
            if (selectedItem instanceof VirtualFolder folder) {
                if (isRecursiveDelete) {
                    FolderManager.getInstance().deleteFolderRecursive(folder);
                    statusLabel.setText("Ordner und alle Unterordner erfolgreich gelöscht.");
                } else {
                    FolderManager.getInstance().deleteFolder(folder);
                    statusLabel.setText("Ordner erfolgreich gelöscht.");
                }
            } else if (selectedItem instanceof EncryptedFile file) {
                FileStorage.getInstance().deleteFile(file);
                statusLabel.setText("Datei erfolgreich gelöscht.");
            } else {
                throw new IllegalStateException("Unexpected value: " + selectedItem);
            }
            refreshUI();
        } catch (RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Fehler beim Löschen", "Fehler beim Löschen: " + e.getMessage());
        }
    }
    
    /**
     * Löscht einen Ordner.
     * Zeigt einen Bestätigungsdialog vor dem Löschen.
     */
    @FXML
    public void handleDeleteFolder() {
        TreeItem<VirtualFolder> selectedItem = folderTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || selectedItem.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Kein Ordner ausgewählt", "Bitte wählen Sie einen Ordner zum Löschen aus.");
            return;
        }

        VirtualFolder folder = selectedItem.getValue();
        if (folder.getId() == -1) {  // Root folder
            showAlert(Alert.AlertType.WARNING, "Ungültige Aktion", "Der Root-Ordner kann nicht gelöscht werden.");
            return;
        }

        // Prüfen, ob Unterordner vorhanden sind
        List<VirtualFolder> subfolders = FolderManager.getInstance().getSubfolders(folder.getId());
        boolean hasSubfolders = !subfolders.isEmpty();

        String confirmMessage;
        if (hasSubfolders) {
            confirmMessage = "Der Ordner '" + folder.getName() + "' enthält Unterordner.\n" +
                   "Möchten Sie diesen Ordner und ALLE darin enthaltenen Unterordner und Dateien löschen?\n" +
                   "WARNUNG: Diese Aktion kann nicht rückgängig gemacht werden!";
        } else {
            confirmMessage = "Möchten Sie den Ordner '" + folder.getName() + "' wirklich löschen?";
        }

        if (!showConfirmationDialog("Ordner löschen", confirmMessage)) {
            return;
        }

        try {
            if (hasSubfolders) {
                FolderManager.getInstance().deleteFolderRecursive(folder);
                statusLabel.setText("Ordner und alle Unterordner erfolgreich gelöscht.");
            } else {
                FolderManager.getInstance().deleteFolder(folder);
                statusLabel.setText("Ordner erfolgreich gelöscht.");
            }
            refreshFolderTree();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Fehler beim Löschen", 
                    "Fehler beim Löschen des Ordners: " + e.getMessage());
        }
    }
    
    /**
     * Aktualisiert die Liste der Dateien im aktuellen Ordner.
     */
    private void refreshFileList() {
        LoggingUtil.logInfo("MainController", "Refreshing file list.");
        VirtualFolder currentFolder = FolderManager.getInstance().getCurrentFolder();
        if (currentFolder != null) {
            currentFolderLabel.setText(currentFolder.getName());

            List<EncryptedFile> files = FileStorage.getInstance().getFilesInFolder(currentFolder);
            List<VirtualFolder> subfolders = FolderManager.getInstance().getSubfolders(currentFolder.getId());

            List<Object> items = new ArrayList<>();
            items.addAll(subfolders);
            items.addAll(files);

            fileTableView.setItems(FXCollections.observableArrayList(items));
            LoggingUtil.logInfo("MainController", "File list refreshed for folder: " + currentFolder.getName());
        } else {
            currentFolderLabel.setText("[Kein Ordner ausgewählt]");
            fileTableView.setItems(FXCollections.observableArrayList());
            LoggingUtil.logInfo("MainController", "No folder selected. File list cleared.");
        }
    }

    /**
     * Refreshes both the folder tree and file table while preserving the folder tree's state.
     */
    private void refreshUI() {
        LoggingUtil.logInfo("MainController", "Performing full UI refresh");
        
        try {
            // Lade aktuelle Daten aus der Datenbank
            FolderManager.getInstance().reloadFromDatabase();
            FileStorage.getInstance().reloadFromDatabase();
            
            // Merke aktuell ausgewählten Ordner
            TreeItem<VirtualFolder> selectedFolder = folderTreeView.getSelectionModel().getSelectedItem();
            VirtualFolder currentFolder = selectedFolder != null ? selectedFolder.getValue() : null;
            
            // Aktualisiere Ordnerbaum
            refreshFolderTree();
            
            // Versuche den vorher ausgewählten Ordner wieder zu selektieren
            if (currentFolder != null) {
                selectFolderInTree(currentFolder);
                // Falls der Ordner nicht mehr existiert, wähle Root
                if (folderTreeView.getSelectionModel().isEmpty() && folderTreeView.getRoot() != null) {
                    folderTreeView.getSelectionModel().select(folderTreeView.getRoot());
                }
            } else if (folderTreeView.getRoot() != null) {
                // Falls kein Ordner ausgewählt war, wähle Root
                folderTreeView.getSelectionModel().select(folderTreeView.getRoot());
            }
            
            // Aktualisiere Dateiliste
            refreshFileList();
            
            LoggingUtil.logInfo("MainController", "Full UI refresh completed");
        } catch (Exception e) {
            LoggingUtil.logError("MainController", "Error during UI refresh: " + e.getMessage());
            throw new RuntimeException("Error refreshing UI", e);
        }
    }

    /**
     * Refreshes both the folder tree and file table while preserving the folder tree's state.
     */
    private void refreshUIAfterRename(VirtualFolder folder) {
        TreeItem<VirtualFolder> selectedFolder = folderTreeView.getSelectionModel().getSelectedItem();
        refreshFolderTree();
        if (folder != null) {
            selectFolderInTree(folder);
        } else if (selectedFolder != null) {
            selectFolderInTree(selectedFolder.getValue());
        }
        refreshFileList();
    }

    /**
     * Erstellt einen neuen Ordner.
     * Zeigt einen Dialog zur Eingabe der Details für den neuen Ordner.
     */
    @FXML
    public void handleNewFolder() {
        // Get the selected folder (parent)
        TreeItem<VirtualFolder> selectedItem = folderTreeView.getSelectionModel().getSelectedItem();
        final Integer parentId = (selectedItem != null && selectedItem.getValue() != null) ? 
            selectedItem.getValue().getId() : null;

        // Create a custom dialog
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Neuer Ordner");
        dialog.setHeaderText("Geben Sie die Details für den neuen Ordner ein");

        // Set the button types
        ButtonType createButtonType = new ButtonType("Erstellen", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // Create the name and description fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("Ordnername");
        TextArea descriptionField = new TextArea();
        descriptionField.setPromptText("Beschreibung");
        descriptionField.setPrefRowCount(3);

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Beschreibung:"), 0, 1);
        grid.add(descriptionField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Request focus on the name field by default
        Platform.runLater(nameField::requestFocus);

        // Convert the result to a name-description pair when the create button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                return new Pair<>(nameField.getText(), descriptionField.getText());
            }
            return null;
        });

        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        stage.setAlwaysOnTop(true);

        Optional<Pair<String, String>> result = dialog.showAndWait();
        result.ifPresent(pair -> {
            String folderName = pair.getKey();
            String description = pair.getValue();
            
            if (!folderName.isEmpty()) {
                try {
                    VirtualFolder folder = FolderManager.getInstance().createFolder(folderName, description, parentId);
                    
                    if (folder != null) {
                        refreshFolderTree();
                        // Select the new folder
                        selectFolderInTree(folder);
                        handleFolderSelection(null);
                        statusLabel.setText("Ordner erfolgreich erstellt.");
                    } else {
                        statusLabel.setText("Erstellen des Ordners fehlgeschlagen.");
                    }
                } catch (Exception e) {
                    statusLabel.setText("Fehler beim Erstellen des Ordners: " + e.getMessage());
                    showAlert(Alert.AlertType.ERROR, "Erstellungsfehler", "Fehler beim Erstellen des Ordners: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Wählt einen Ordner in der Baumansicht aus.
     * 
     * @param folder Der auszuwählende Ordner
     */
    private void selectFolderInTree(VirtualFolder folder) {
        selectFolderInTree(folderTreeView.getRoot(), folder);
    }
    
    /**
     * Rekursive Hilfsmethode zum Auswählen eines Ordners in der Baumansicht.
     * 
     * @param parent Der übergeordnete TreeItem
     * @param folder Der auszuwählende Ordner
     * @return true, wenn der Ordner gefunden und ausgewählt wurde
     */
    private boolean selectFolderInTree(TreeItem<VirtualFolder> parent, VirtualFolder folder) {
        for (TreeItem<VirtualFolder> child : parent.getChildren()) {
            if (child.getValue().equals(folder)) {
                folderTreeView.getSelectionModel().select(child);
                return true;
            }
            if (selectFolderInTree(child, folder)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Benennt einen Ordner um.
     * Zeigt einen Dialog zur Eingabe des neuen Ordnernamens.
     */
    @FXML
    public void handleRenameFolder(VirtualFolder folder) {
        if (folder == null) {
            showAlert(Alert.AlertType.WARNING, "Kein Ordner ausgewählt", "Bitte wählen Sie einen Ordner zum Umbenennen aus.");
            return;
        }

        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Ordner bearbeiten");
        dialog.setHeaderText("Bearbeiten Sie die Details des Ordners");

        ButtonType saveButtonType = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField(folder.getName());
        nameField.setPromptText("Ordnername");
        TextArea descriptionField = new TextArea(folder.getDescription());
        descriptionField.setPromptText("Beschreibung");
        descriptionField.setPrefRowCount(3);

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Beschreibung:"), 0, 1);
        grid.add(descriptionField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        Platform.runLater(nameField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return new Pair<>(nameField.getText(), descriptionField.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> result = dialog.showAndWait();
        result.ifPresent(pair -> {
            String newName = pair.getKey();
            String newDescription = pair.getValue();

            if (!newName.isEmpty()) {
                try {
                    boolean success = FolderManager.getInstance().renameFolder(folder, newName);
                    folder.setDescription(newDescription);

                    if (success) {
                        refreshFolderTree();
                        selectFolderInTree(folder);
                        refreshFileList();
                        statusLabel.setText("Ordner erfolgreich bearbeitet.");
                    } else {
                        statusLabel.setText("Bearbeiten des Ordners fehlgeschlagen.");
                    }
                } catch (Exception e) {
                    statusLabel.setText("Fehler beim Bearbeiten: " + e.getMessage());
                    showAlert(Alert.AlertType.ERROR, "Bearbeitungsfehler", "Fehler beim Bearbeiten des Ordners: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    public void handleRenameFolder(ActionEvent event) {
        Object selectedItem = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedItem instanceof VirtualFolder folder) {
            handleRenameFolder(folder);
        } else {
            showAlert(Alert.AlertType.WARNING, "Kein Ordner ausgewählt", "Bitte wählen Sie einen Ordner zum Umbenennen aus.");
        }
    }
    
    /**
     * Ändert das Benutzerpasswort.
     * Zeigt einen Dialog zur Eingabe des neuen Passworts.
     */
    @FXML
    public void handleChangePassword() {
        Platform.runLater(() -> {
            Dialog<String[]> dialog = new Dialog<>();
            dialog.setTitle("Change Master Password");
            dialog.setHeaderText("Enter your current password and a new password");
            
            // Set the button types
            ButtonType changeButtonType = new ButtonType("Change", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(changeButtonType, ButtonType.CANCEL);
            
            // Create the password fields
            PasswordField currentPasswordField = new PasswordField();
            currentPasswordField.setPromptText("Current password");
            PasswordField newPasswordField = new PasswordField();
            newPasswordField.setPromptText("New password");
            PasswordField confirmPasswordField = new PasswordField();
            confirmPasswordField.setPromptText("Confirm new password");
            
            // Enable/Disable change button depending on whether passwords are entered
            Button changeButton = (Button) dialog.getDialogPane().lookupButton(changeButtonType);
            changeButton.setDisable(true);
            
            currentPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
                changeButton.setDisable(
                        newValue.trim().isEmpty() || 
                        newPasswordField.getText().trim().isEmpty() || 
                        confirmPasswordField.getText().trim().isEmpty());
            });
            
            newPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
                changeButton.setDisable(
                        currentPasswordField.getText().trim().isEmpty() || 
                        newValue.trim().isEmpty() || 
                        confirmPasswordField.getText().trim().isEmpty());
            });
            
            confirmPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
                changeButton.setDisable(
                        currentPasswordField.getText().trim().isEmpty() || 
                        newPasswordField.getText().trim().isEmpty() || 
                        newValue.trim().isEmpty());
            });
            
            // Create and add the layout
            javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.add(new Label("Current password:"), 0, 0);
            grid.add(currentPasswordField, 1, 0);
            grid.add(new Label("New password:"), 0, 1);
            grid.add(newPasswordField, 1, 1);
            grid.add(new Label("Confirm new password:"), 0, 2);
            grid.add(confirmPasswordField, 1, 2);
            
            dialog.getDialogPane().setContent(grid);
            
            Platform.runLater(currentPasswordField::requestFocus);
            
            // Convert the result to a password when the change button is clicked
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == changeButtonType) {
                    return new String[]{
                            currentPasswordField.getText(),
                            newPasswordField.getText(),
                            confirmPasswordField.getText()
                    };
                }
                return null;
            });
            
            Optional<String[]> result = dialog.showAndWait();
            
            result.ifPresent(passwords -> {
                String currentPassword = passwords[0];
                String newPassword = passwords[1];
                String confirmPassword = passwords[2];
                
                if (!newPassword.equals(confirmPassword)) {
                    showAlert(Alert.AlertType.ERROR, "Passwort Error", "Passwörter stimmen nicht überein.");
                    return;
                }
                
                if (newPassword.length() < 8) {
                    showAlert(Alert.AlertType.ERROR, "Passwort Error", "Passwort muss mindestens 8 Zeichen lang sein.");
                    return;
                }
                
                try {
                    boolean success = UserManager.getInstance().changePassword(currentPassword, newPassword);
                    
                    if (success) {
                        statusLabel.setText("Passwort erfolgreich geändert.");
                        showAlert(Alert.AlertType.INFORMATION, "Passwort geändert", "Dein Passwort wurde erfolgreich geändert.");
                    } else {
                        String errorMessage = "Fehler beim Ändern des Passworts.";
                        if (currentPassword.equals(newPassword)) {
                            errorMessage = "Das neue Passwort darf nicht mit dem alten Passwort übereinstimmen.";
                        } else if (!UserManager.getInstance().authenticate(currentPassword)) {
                            errorMessage = "Das aktuelle Passwort ist falsch.";
                        }
                        statusLabel.setText(errorMessage);
                        showAlert(Alert.AlertType.ERROR, "Passwort Error", errorMessage);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Fehler beim Ändern des Passworts: " + e.getMessage());
                    showAlert(Alert.AlertType.ERROR, "Passwort Error", "Fehler beim Ändern des Passworts: " + e.getMessage());
                }
            });
        });
    }
    
    /**
     * Öffnet die Einstellungen.
     */
    @FXML
    public void handleSettings() {
        showAlert(Alert.AlertType.INFORMATION, "Einstellungen", "Einstellungen wurden noch nicht implementiert.");
    }
    
    /**
     * Zeigt Informationen über die Anwendung an.
     */
    @FXML
    public void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Über FileVault");
        alert.setHeaderText("FileVault");
        alert.setContentText("""
            Version 1.0
            2025 Phillip Schneider - Projekt FileVault- Java II
        """);
        alert.showAndWait();
    }
    
    /**
     * Beendet die Anwendung.
     */
    @FXML
    public void handleExit() {
        boolean confirm = showConfirmationDialog("Beenden", "Möchten Sie FileVault wirklich beenden?");
        if (confirm) {
            Platform.exit();
        }
    }
    
    /**
     * Zeigt eine Warnung oder Fehlermeldung an.
     * 
     * @param type Der Typ der Meldung (WARNUNG oder FEHLER)
     * @param title Der Titel der Meldung
     * @param message Der Inhalt der Meldung
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.setAlwaysOnTop(true);

        alert.showAndWait();
    }
    
    /**
     * Zeigt einen Bestätigungsdialog an.
     * 
     * @param title Der Titel des Dialogs
     * @param message Der Inhalt des Dialogs
     * @return true, wenn der Benutzer bestätigt hat, sonst false
     */
    private boolean showConfirmationDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.setAlwaysOnTop(true);
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Setter for folderTreeView (for testing purposes).
     */
    public void setFolderTreeView(TreeView<VirtualFolder> folderTreeView) {
        this.folderTreeView = folderTreeView;
    }

    /**
     * Setter for fileTableView (for testing purposes).
     */
    public void setFileTableView(TableView<Object> fileTableView) {
        this.fileTableView = fileTableView;
    }

    /**
     * Setter for currentFolderLabel (for testing purposes).
     */
    public void setCurrentFolderLabel(Label currentFolderLabel) {
        this.currentFolderLabel = currentFolderLabel;
    }

    /**
     * Setter for statusLabel (for testing purposes).
     */
    public void setStatusLabel(Label statusLabel) {
        this.statusLabel = statusLabel;
    }

    /**
     * Setter for fileNameColumn (for testing purposes).
     */
    public void setFileNameColumn(TableColumn<Object, String> fileNameColumn) {
        this.fileNameColumn = fileNameColumn;
    }

    /**
     * Setter for fileSizeColumn (for testing purposes).
     */
    public void setFileSizeColumn(TableColumn<Object, String> fileSizeColumn) {
        this.fileSizeColumn = fileSizeColumn;
    }

    /**
     * Setter for fileDateColumn (for testing purposes).
     */
    public void setFileDateColumn(TableColumn<Object, String> fileDateColumn) {
        this.fileDateColumn = fileDateColumn;
    }

    /**
     * Setter für den Theme-Toggle-Button (für Testzwecke).
     * 
     * @param themeToggleButton Der zu setzende Button
     */
    public void setThemeToggleButton(Button themeToggleButton) {
        this.themeToggleButton = themeToggleButton;
    }

    /**
     * Initialisiert den Refresh-Button mit Styling und Animation.
     */
    private void initializeRefreshButton() {
        if (refreshButton != null) {
            refreshButton.getStyleClass().add("refresh-button");
            
            // Set button text with refresh symbol
            refreshButton.setText("↻ Aktualisieren");
            refreshButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            refreshButton.setTooltip(new Tooltip("Ansicht aktualisieren"));
        }
    }

    /**
     * Verarbeitet das Klicken auf den Refresh-Button.
     * Aktualisiert die Ordnerliste und Dateiliste mit Animation.
     */
    @FXML
    public void handleRefresh() {
        LoggingUtil.logInfo("MainController", "Refreshing UI with animation.");
        
        try {
            if (refreshButton != null) {
                // Deaktiviere den Button während der Aktualisierung
                refreshButton.setDisable(true);
                
                // Create icon rotation animation
                javafx.scene.shape.Circle circle = new javafx.scene.shape.Circle(10, javafx.scene.paint.Color.TRANSPARENT);
                javafx.scene.text.Text rotatingIcon = new javafx.scene.text.Text("↻");
                rotatingIcon.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
                
                javafx.scene.layout.StackPane iconPane = new javafx.scene.layout.StackPane(circle, rotatingIcon);
                
                // Temporarily replace button content with the animated icon and text
                String originalText = refreshButton.getText();
                javafx.scene.layout.HBox content = new javafx.scene.layout.HBox(5, iconPane, 
                    new javafx.scene.control.Label("Aktualisiere..."));
                content.setAlignment(javafx.geometry.Pos.CENTER);
                refreshButton.setGraphic(content);
                refreshButton.setText("");
                
                // Create the rotation animation
                RotateTransition rotateTransition = 
                    new RotateTransition(Duration.seconds(1), rotatingIcon);
                rotateTransition.setFromAngle(0);
                rotateTransition.setToAngle(360);
                rotateTransition.setCycleCount(1);
                rotateTransition.setInterpolator(javafx.animation.Interpolator.LINEAR);
                
                // Start the rotation animation
                rotateTransition.play();
            }
        } catch (Exception ex) {
            // Gracefully handle any animation setup errors in tests
            LoggingUtil.logError("MainController", "Error setting up refresh animation: " + ex.getMessage());
        }
        
        // Fade out the current views
        FadeTransition fadeOutFolders = new FadeTransition(Duration.millis(200), folderTreeView);
        fadeOutFolders.setFromValue(1.0);
        fadeOutFolders.setToValue(0.5);
        
        FadeTransition fadeOutFiles = new FadeTransition(Duration.millis(200), fileTableView);
        fadeOutFiles.setFromValue(1.0);
        fadeOutFiles.setToValue(0.5);
        
        ParallelTransition fadeOut = new ParallelTransition(fadeOutFolders, fadeOutFiles);
        fadeOut.setOnFinished(event -> {
            // Refresh UI after a short delay to make animation visible
            Platform.runLater(() -> {
                try {
                    // Refresh the UI
                    refreshUI();
                    if (statusLabel != null) {
                        statusLabel.setText("Ansicht erfolgreich aktualisiert");
                    }
                    
                    // Fade in the refreshed views
                    FadeTransition fadeInFolders = new FadeTransition(Duration.millis(400), folderTreeView);
                    fadeInFolders.setFromValue(0.5);
                    fadeInFolders.setToValue(1.0);
                    
                    FadeTransition fadeInFiles = new FadeTransition(Duration.millis(400), fileTableView);
                    fadeInFiles.setFromValue(0.5);
                    fadeInFiles.setToValue(1.0);
                    
                    ParallelTransition fadeIn = new ParallelTransition(fadeInFolders, fadeInFiles);
                    fadeIn.play();
                    
                    // Restore original button text after animation completes
                    if (refreshButton != null) {
                        try {
                            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                                Duration.seconds(1.1));
                            pause.setOnFinished(e -> {
                                try {
                                    refreshButton.setGraphic(null);
                                    refreshButton.setText("↻ Aktualisieren");
                                    refreshButton.setDisable(false);
                                } catch (Exception ex) {
                                    LoggingUtil.logError("MainController", "Error resetting button after animation: " + ex.getMessage());
                                }
                            });
                            pause.play();
                        } catch (Exception ex) {
                            LoggingUtil.logError("MainController", "Error restoring button state: " + ex.getMessage());
                        }
                    }
                    
                } catch (Exception ex) {
                    LoggingUtil.logError("MainController", "Error refreshing UI: " + ex.getMessage());
                    if (statusLabel != null) {
                        statusLabel.setText("Fehler beim Aktualisieren: " + ex.getMessage());
                    }
                    
                    try {
                        showAlert(Alert.AlertType.ERROR, "Aktualisierungsfehler", 
                            "Fehler beim Aktualisieren der Ansicht: " + ex.getMessage());
                    } catch (Exception alertEx) {
                        LoggingUtil.logError("MainController", "Error showing alert: " + alertEx.getMessage());
                    }
                    
                    // Restore original button immediately on error
                    if (refreshButton != null) {
                        try {
                            refreshButton.setGraphic(null);
                            refreshButton.setText("↻ Aktualisieren");
                            refreshButton.setDisable(false);
                        } catch (Exception btnEx) {
                            LoggingUtil.logError("MainController", "Error resetting button: " + btnEx.getMessage());
                        }
                    }
                }
            });
        });
        
        // Start fade out
        fadeOut.play();
    }

    /**
     * Setter für den Refresh-Button (für Testzwecke).
     * 
     * @param refreshButton Der zu setzende Button
     */
    public void setRefreshButton(Button refreshButton) {
        this.refreshButton = refreshButton;
    }

    /**
     * Zeigt einen Dialog zum Löschen einer Datei an.
     * 
     * @param fileToDelete Die zu löschende Datei.
     * @return true, wenn der Benutzer das Löschen bestätigt hat, false sonst.
     */
    private boolean showDeleteConfirmationDialog(File fileToDelete) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Löschen bestätigen");
        alert.setHeaderText("Datei löschen");
        alert.setContentText("Möchten Sie die Datei/den Ordner " + fileToDelete.getName() + " wirklich löschen?");
        
        // Apply current theme to dialog
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/com/filevault/style.css").toExternalForm());
        if (FileVaultApp.isDarkMode()) {
            dialogPane.getStyleClass().add("dark-theme");
        }
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Zeigt einen Dialog zum Umbenennen einer Datei oder eines Ordners an.
     * 
     * @param fileToRename Die umzubenennende Datei oder der umzubenennende Ordner.
     * @return Der neue Name oder null, wenn der Dialog abgebrochen wurde.
     */
    private String showRenameDialog(File fileToRename) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Umbenennen");
        dialog.setHeaderText("Datei/Ordner umbenennen");
        
        // Apply current theme to dialog
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/com/filevault/style.css").toExternalForm());
        if (FileVaultApp.isDarkMode()) {
            dialogPane.getStyleClass().add("dark-theme");
        }
        
        ButtonType renameButtonType = new ButtonType("Umbenennen", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(renameButtonType, ButtonType.CANCEL);
        
        TextField nameField = new TextField(fileToRename.getName());
        nameField.setMinWidth(250);
        dialog.getDialogPane().setContent(nameField);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == renameButtonType) {
                return nameField.getText();
            }
            return null;
        });
        
        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }

    /**
     * Importiert eine ausgewählte Datei in den aktuellen Ordner.
     * 
     * @param file Die zu importierende Datei
     */
    private void importFile(File file) {
        LoggingUtil.logInfo("MainController", "Starting file import: " + file.getName());
        
        VirtualFolder currentFolder = FolderManager.getInstance().getCurrentFolder();
        if (currentFolder == null) {
            showAlert(Alert.AlertType.ERROR, "Importfehler", "Kein Zielordner ausgewählt. Bitte wählen Sie einen Ordner aus.");
            return;
        }

        try {
            EncryptedFile importedFile = FileStorage.getInstance().importFile(file, currentFolder);
            if (importedFile != null) {
                refreshFileList();
                statusLabel.setText("Datei erfolgreich importiert: " + file.getName());
                LoggingUtil.logInfo("MainController", "File import successful: " + file.getName());
            } else {
                statusLabel.setText("Fehler beim Importieren der Datei: " + file.getName());
                LoggingUtil.logError("MainController", "File import failed: " + file.getName());
                showAlert(Alert.AlertType.ERROR, "Importfehler", "Die Datei konnte nicht importiert werden.");
            }
        } catch (Exception e) {
            LoggingUtil.logError("MainController", "Error importing file: " + e.getMessage());
            statusLabel.setText("Fehler beim Importieren: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Importfehler", "Fehler beim Importieren der Datei: " + e.getMessage());
        }
    }
    
    /**
     * Importiert einen ausgewählten Ordner in den aktuellen Ordner.
     * 
     * @param directory Der zu importierende Ordner
     */
    private void importFolder(File directory) {
        LoggingUtil.logInfo("MainController", "Starting folder import: " + directory.getName());
        
        VirtualFolder currentFolder = FolderManager.getInstance().getCurrentFolder();
        if (currentFolder == null) {
            showAlert(Alert.AlertType.ERROR, "Importfehler", "Kein Zielordner ausgewählt. Bitte wählen Sie einen Ordner aus.");
            return;
        }

        // Erstelle einen neuen Unterordner
        try {
            VirtualFolder newFolder = FolderManager.getInstance().createFolder(directory.getName(), 
                    "Importiert aus: " + directory.getAbsolutePath(), currentFolder.getId());
            
            if (newFolder != null) {
                // Importiere alle Dateien aus dem Ordner
                File[] files = directory.listFiles();
                int successful = 0;
                
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            try {
                                EncryptedFile importedFile = FileStorage.getInstance().importFile(file, newFolder);
                                if (importedFile != null) {
                                    successful++;
                                }
                            } catch (Exception e) {
                                LoggingUtil.logError("MainController", "Error importing file " + file.getName() + ": " + e.getMessage());
                            }
                        }
                    }
                }
                
                refreshUI();
                selectFolderInTree(newFolder);
                statusLabel.setText(successful + " Dateien erfolgreich importiert aus: " + directory.getName());
                LoggingUtil.logInfo("MainController", successful + " files imported from folder: " + directory.getName());
            } else {
                statusLabel.setText("Fehler beim Erstellen des Ordners: " + directory.getName());
                LoggingUtil.logError("MainController", "Failed to create folder: " + directory.getName());
                showAlert(Alert.AlertType.ERROR, "Importfehler", "Der Ordner konnte nicht erstellt werden.");
            }
        } catch (Exception e) {
            LoggingUtil.logError("MainController", "Error importing folder: " + e.getMessage());
            statusLabel.setText("Fehler beim Importieren des Ordners: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Importfehler", "Fehler beim Importieren des Ordners: " + e.getMessage());
        }
    }
    
    /**
     * Exportiert eine ausgewählte Datei an einen bestimmten Ort.
     * 
     * @param file Die zu exportierende Datei
     * @param directory Das Zielverzeichnis
     */
    private void exportFile(EncryptedFile file, File directory) {
        LoggingUtil.logInfo("MainController", "Starting file export: " + file.getOriginalName());
        
        try {
            File targetFile = new File(directory, file.getOriginalName());
            
            // Prüfe, ob die Datei bereits existiert
            if (targetFile.exists()) {
                if (!showConfirmationDialog("Datei überschreiben", 
                        "Die Datei " + file.getOriginalName() + " existiert bereits. Möchten Sie sie überschreiben?")) {
                    return;
                }
            }
            
            boolean success = FileStorage.getInstance().exportFile(file, targetFile);
            
            if (success) {
                statusLabel.setText("Datei erfolgreich exportiert: " + file.getOriginalName());
                LoggingUtil.logInfo("MainController", "File export successful: " + file.getOriginalName());
            } else {
                statusLabel.setText("Fehler beim Exportieren der Datei: " + file.getOriginalName());
                LoggingUtil.logError("MainController", "File export failed: " + file.getOriginalName());
                showAlert(Alert.AlertType.ERROR, "Exportfehler", "Die Datei konnte nicht exportiert werden.");
            }
        } catch (Exception e) {
            LoggingUtil.logError("MainController", "Error exporting file: " + e.getMessage());
            statusLabel.setText("Fehler beim Exportieren: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Exportfehler", "Fehler beim Exportieren der Datei: " + e.getMessage());
        }
    }
}