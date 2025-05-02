package com.filevault.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.filevault.model.EncryptedFile;
import com.filevault.model.VirtualFolder;
import com.filevault.security.EncryptionService;
import com.filevault.util.FolderManager;

/**
 * Verwaltet die Speicherung und den Abruf verschlüsselter Dateien.
 */
public class FileStorage {
    
    /** Die einzige Instanz des FileStorage */
    private static FileStorage instance;
    
    /**
     * Privater Konstruktor für das Singleton-Pattern.
     */
    private FileStorage() {
    }
    
    /**
     * Gibt die einzige Instanz des FileStorage zurück.
     * 
     * @return Die Singleton-Instanz des FileStorage
     */
    public static synchronized FileStorage getInstance() {
        if (instance == null) {
            instance = new FileStorage();
        }
        return instance;
    }
    
    /**
     * Importiert eine Datei in den Tresor.
     * 
     * @param sourceFile Die zu importierende Datei
     * @param folder Der Ordner, in den die Datei importiert werden soll
     * @return Die importierte Datei oder null, wenn der Import fehlgeschlagen ist
     * @throws Exception wenn ein Fehler während des Imports auftritt
     */
    public EncryptedFile importFile(File sourceFile, VirtualFolder folder) throws Exception {
        if (!sourceFile.exists() || !sourceFile.isFile() || !sourceFile.canRead()) {
            throw new IOException("Quelldatei kann nicht gelesen werden: " + sourceFile.getAbsolutePath());
        }
        
        // Generiere einen eindeutigen verschlüsselten Dateinamen
        String encryptedFileName = UUID.randomUUID().toString();
        String encryptedFilePath = Paths.get(FolderManager.getInstance().getDataDirectoryPath(), encryptedFileName).toString();
        File encryptedFile = new File(encryptedFilePath);
        
        // Verschlüssele die Datei
        EncryptionService.getInstance().encryptFile(sourceFile, encryptedFile);
        
        // Erkenne den MIME-Typ
        String mimeType = Files.probeContentType(sourceFile.toPath());
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }
        
        // Speichere die Dateimetadaten in der Datenbank
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO files (folder_id, original_name, encrypted_path, size_bytes, mime_type, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, folder.getId());
            stmt.setString(2, sourceFile.getName());
            stmt.setString(3, encryptedFilePath);
            stmt.setLong(4, sourceFile.length());
            stmt.setString(5, mimeType);
            
            int affected = stmt.executeUpdate();
            
            if (affected > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);
                        
                        // Erstelle und gib das verschlüsselte Dateiobjekt zurück
                        return new EncryptedFile(
                                id,
                                folder.getId(),
                                sourceFile.getName(),
                                encryptedFilePath,
                                sourceFile.length(),
                                mimeType,
                                LocalDateTime.now(),
                                null
                        );
                    }
                }
            }
            
            // Wenn wir hier ankommen, ist etwas mit der Datenbank schiefgelaufen
            encryptedFile.delete();
            return null;
        }
    }
    
    /**
     * Exportiert eine Datei aus dem Tresor.
     * 
     * @param encryptedFile Die zu exportierende Datei
     * @param destinationFile Die Zieldatei
     * @return true, wenn der Export erfolgreich war
     * @throws Exception wenn ein Fehler während des Exports auftritt
     */
    public boolean exportFile(EncryptedFile encryptedFile, File destinationFile) throws Exception {
        File sourceFile = new File(encryptedFile.getEncryptedPath());
        if (!sourceFile.exists() || !sourceFile.isFile() || !sourceFile.canRead()) {
            throw new IOException("Verschlüsselte Datei kann nicht gelesen werden: " + sourceFile.getAbsolutePath());
        }
        
        // Entschlüssele die Datei
        boolean success = EncryptionService.getInstance().decryptFile(sourceFile, destinationFile);
        
        if (success) {
            // Aktualisiere den Zeitstempel des letzten Zugriffs
            updateLastAccess(encryptedFile.getId());
        }
        
        return success;
    }
    
    /**
     * Löscht eine Datei aus dem Tresor.
     * 
     * @param encryptedFile Die zu löschende Datei
     * @return true, wenn das Löschen erfolgreich war
     */
    public boolean deleteFile(EncryptedFile encryptedFile) {
        try {
            // Lösche zuerst die physische Datei
            File file = new File(encryptedFile.getEncryptedPath());
            if (file.exists()) {
                file.delete();
            }
            
            // Dann lösche den Datenbankeintrag
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM files WHERE id = ?")) {
                
                stmt.setInt(1, encryptedFile.getId());
                int affected = stmt.executeUpdate();
                
                return affected > 0;
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Löschen der Datei: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Benennt eine Datei im Tresor um.
     * 
     * @param encryptedFile Die umzubenennende Datei
     * @param newName Der neue Name für die Datei
     * @return true, wenn das Umbenennen erfolgreich war
     */
    public boolean renameFile(EncryptedFile encryptedFile, String newName) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE files SET original_name = ? WHERE id = ?")) {
            
            stmt.setString(1, newName);
            stmt.setInt(2, encryptedFile.getId());
            int affected = stmt.executeUpdate();
            
            if (affected > 0) {
                encryptedFile.setOriginalName(newName);
                return true;
            }
            
            return false;
        } catch (SQLException e) {
            System.err.println("Fehler beim Umbenennen der Datei: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Verschiebt eine Datei in einen anderen Ordner.
     * 
     * @param encryptedFile Die zu verschiebende Datei
     * @param targetFolder Der Zielordner
     * @return true, wenn die Datei erfolgreich verschoben wurde
     */
    public boolean moveFile(EncryptedFile encryptedFile, VirtualFolder targetFolder) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE files SET folder_id = ? WHERE id = ?")) {
            
            stmt.setInt(1, targetFolder.getId());
            stmt.setInt(2, encryptedFile.getId());
            int affected = stmt.executeUpdate();
            
            if (affected > 0) {
                encryptedFile.setFolderId(targetFolder.getId());
                return true;
            }
            
            return false;
        } catch (SQLException e) {
            System.err.println("Fehler beim Verschieben der Datei: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gibt alle Dateien in einem Ordner zurück.
     * 
     * @param folder Der Ordner, aus dem die Dateien abgerufen werden sollen
     * @return Eine Liste der Dateien im Ordner
     */
    public List<EncryptedFile> getFilesInFolder(VirtualFolder folder) {
        List<EncryptedFile> files = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM files WHERE folder_id = ? ORDER BY original_name")) {
            
            stmt.setInt(1, folder.getId());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp createdTimestamp = rs.getTimestamp("created_at");
                    Timestamp lastAccessTimestamp = rs.getTimestamp("last_access");
                    
                    LocalDateTime createdAt = createdTimestamp != null 
                            ? createdTimestamp.toLocalDateTime() 
                            : null;
                            
                    LocalDateTime lastAccess = lastAccessTimestamp != null 
                            ? lastAccessTimestamp.toLocalDateTime() 
                            : null;
                    
                    EncryptedFile file = new EncryptedFile(
                            rs.getInt("id"),
                            rs.getInt("folder_id"),
                            rs.getString("original_name"),
                            rs.getString("encrypted_path"),
                            rs.getLong("size_bytes"),
                            rs.getString("mime_type"),
                            createdAt,
                            lastAccess
                    );
                    files.add(file);
                }
            }
        } catch (SQLException e) {
            System.err.println("Fehler beim Abrufen der Dateien: " + e.getMessage());
        }
        
        return files;
    }
    
    /**
     * Gibt eine Datei anhand ihrer ID zurück.
     * 
     * @param fileId Die ID der Datei
     * @return Die Datei oder null, wenn sie nicht gefunden wurde
     */
    public EncryptedFile getFileById(int fileId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM files WHERE id = ?")) {
            
            stmt.setInt(1, fileId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Timestamp createdTimestamp = rs.getTimestamp("created_at");
                    Timestamp lastAccessTimestamp = rs.getTimestamp("last_access");
                    
                    LocalDateTime createdAt = createdTimestamp != null 
                            ? createdTimestamp.toLocalDateTime() 
                            : null;
                            
                    LocalDateTime lastAccess = lastAccessTimestamp != null 
                            ? lastAccessTimestamp.toLocalDateTime() 
                            : null;
                    
                    return new EncryptedFile(
                            rs.getInt("id"),
                            rs.getInt("folder_id"),
                            rs.getString("original_name"),
                            rs.getString("encrypted_path"),
                            rs.getLong("size_bytes"),
                            rs.getString("mime_type"),
                            createdAt,
                            lastAccess
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Fehler beim Abrufen der Datei: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Aktualisiert den Zeitstempel des letzten Zugriffs auf eine Datei.
     * 
     * @param fileId Die ID der Datei
     */
    private void updateLastAccess(int fileId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE files SET last_access = CURRENT_TIMESTAMP WHERE id = ?")) {
            
            stmt.setInt(1, fileId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Fehler beim Aktualisieren des letzten Zugriffs: " + e.getMessage());
        }
    }

    /**
     * Retrieves all files from the database.
     *
     * @return A list of all encrypted files.
     */
    public List<EncryptedFile> getAllFiles() {
        List<EncryptedFile> files = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM files ORDER BY original_name");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Timestamp createdTimestamp = rs.getTimestamp("created_at");
                Timestamp lastAccessTimestamp = rs.getTimestamp("last_access");

                LocalDateTime createdAt = createdTimestamp != null
                        ? createdTimestamp.toLocalDateTime()
                        : null;

                LocalDateTime lastAccess = lastAccessTimestamp != null
                        ? lastAccessTimestamp.toLocalDateTime()
                        : null;

                EncryptedFile file = new EncryptedFile(
                        rs.getInt("id"),
                        rs.getInt("folder_id"),
                        rs.getString("original_name"),
                        rs.getString("encrypted_path"),
                        rs.getLong("size_bytes"),
                        rs.getString("mime_type"),
                        createdAt,
                        lastAccess
                );
                files.add(file);
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving all files: " + e.getMessage());
        }

        return files;
    }

    /**
     * Retrieves files by folder ID.
     *
     * @param folderId The ID of the folder.
     * @return A list of files in the specified folder.
     */
    public List<EncryptedFile> getFilesByFolderId(int folderId) {
        List<EncryptedFile> files = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM files WHERE folder_id = ? ORDER BY original_name")) {

            stmt.setInt(1, folderId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp createdTimestamp = rs.getTimestamp("created_at");
                    Timestamp lastAccessTimestamp = rs.getTimestamp("last_access");

                    LocalDateTime createdAt = createdTimestamp != null
                            ? createdTimestamp.toLocalDateTime()
                            : null;

                    LocalDateTime lastAccess = lastAccessTimestamp != null
                            ? lastAccessTimestamp.toLocalDateTime()
                            : null;

                    EncryptedFile file = new EncryptedFile(
                            rs.getInt("id"),
                            rs.getInt("folder_id"),
                            rs.getString("original_name"),
                            rs.getString("encrypted_path"),
                            rs.getLong("size_bytes"),
                            rs.getString("mime_type"),
                            createdAt,
                            lastAccess
                    );
                    files.add(file);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving files by folder ID: " + e.getMessage());
        }

        return files;
    }

    /**
     * Creates a new file record in the database.
     *
     * @param fileName The name of the file.
     * @param folderId The ID of the folder where the file belongs.
     * @return The created EncryptedFile object.
     */
    public EncryptedFile createFileRecord(String fileName, int folderId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO files (folder_id, original_name, encrypted_path, size_bytes, mime_type, created_at) " +
                             "VALUES (?, ?, '', 0, 'application/octet-stream', CURRENT_TIMESTAMP)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, folderId);
            stmt.setString(2, fileName);

            int affected = stmt.executeUpdate();

            if (affected > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);

                        return new EncryptedFile(
                                id,
                                folderId,
                                fileName,
                                "",
                                0,
                                "application/octet-stream",
                                LocalDateTime.now(),
                                null
                        );
                    } else {
                        System.err.println("No generated keys returned for the new file record.");
                    }
                }
            } else {
                System.err.println("No rows were affected when attempting to insert the file record.");
            }
        } catch (SQLException e) {
            System.err.println("Error creating file record: " + e.getMessage());
        }

        return null;
    }
}