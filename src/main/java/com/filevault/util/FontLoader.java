package com.filevault.util;

import java.io.InputStream;

import javafx.scene.text.Font;

/**
 * Utility-Klasse zum Laden und Registrieren benutzerdefinierter Schriftarten.
 */
public class FontLoader {
    
    private static boolean fontsLoaded = false;
    
    /**
     * Lädt und registriert alle benutzerdefinierten Schriftarten, die in der Anwendung verwendet werden.
     * Diese Methode sollte einmal beim Start der Anwendung aufgerufen werden.
     */
    public static void loadFonts() {
        if (fontsLoaded) {
            return;
        }
        
        try {
            // Lade Material Icons Schriftart
            InputStream materialIconsStream = FontLoader.class.getResourceAsStream(
                    "/com/filevault/icons/material-icons.woff2");
            
            if (materialIconsStream != null) {
                Font.loadFont(materialIconsStream, 18);
                LoggingUtil.logInfo("FontLoader", "Material Icons font loaded successfully");
            } else {
                LoggingUtil.logWarning("FontLoader", "Could not load Material Icons font - resource not found");
            }
            
            fontsLoaded = true;
        } catch (Exception e) {
            LoggingUtil.logError("FontLoader", "Failed to load fonts: " + e.getMessage());
        }
    }
    
    /**
     * Gibt den Zeichencode für einen bestimmten Material Icon-Namen zurück.
     *
     * @param iconName Der Name des Material Icons
     * @return Das Unicode-Zeichen für das Icon
     */
    public static String getMaterialIcon(String iconName) {
        switch (iconName) {
            case "folder": return "\uE2C7";
            case "folder_open": return "\uE2C8";
            case "insert_drive_file": return "\uE24D";
            case "delete": return "\uE872";
            case "edit": return "\uE3C9";
            case "add": return "\uE145";
            case "refresh": return "\uE5D5";
            case "dark_mode": return "\uE51C";
            case "light_mode": return "\uE518";
            case "upload": return "\uE2C6";
            case "download": return "\uE2C4";
            case "home": return "\uE88A";
            case "lock": return "\uE897";
            case "person": return "\uE7FD";
            case "settings": return "\uE8B8";
            default: return "";
        }
    }
} 