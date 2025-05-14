# FileVault

FileVault ist eine moderne Java-Anwendung zur sicheren Dateiverschlüsselung und -verwaltung. Mit einer benutzerfreundlichen Oberfläche können Sie vertrauliche Dateien in virtuellen Ordnern organisieren und mit starker Verschlüsselung schützen.

Das Projekt entstand im Rahmen des Kurses "Objektorientierte Programmierung II: Datenstrukturen und Java-Klassenbibliothek" und durchlief mehrere Entwicklungsphasen von der grundlegenden Verschlüsselung über Datenbankintegration bis hin zum JavaFX-Interface.

🔗 **Live Demo & Dokumentation**: [https://GulfGulfinson.github.io/fileVault](https://GulfGulfinson.github.io/fileVault)

## 🔐 Hauptfunktionen

- **Sichere Verschlüsselung** mit AES-256-GCM für maximalen Datenschutz
- **Intuitive Dateiverwaltung** in virtuellen Ordnern mit Drag & Drop
- **Cross-Platform Kompatibilität** für Windows, macOS und Linux
- **Responsive UI** mit modernem JavaFX-Design und Dark Mode
- **Passwort-basierte Authentifizierung** mit sicherer Schlüsselableitung
- **Integritätsschutz** durch GCM-Authentifizierung

## 🔧 Technische Details

### Sicherheitsfunktionen
- **Verschlüsselungsalgorithmus**: AES-256-GCM (Galois/Counter Mode)
- **Schlüsselableitung**: PBKDF2 mit HMAC-SHA256, 65.536 Iterationen
- **Zufallszahlengenerierung**: Kryptografisch sicher für IV (96 Bit) und Salts
- **Authentifizierung**: 128-Bit Auth-Tag zur Integritätsprüfung
- **Datenschutz**: Keine Speicherung von Klartextpasswörtern

### Datenspeicherung
- **Verschlüsselte Daten**: `~/.filevault/data/` (plattformunabhängig)
- **Metadaten**: SQLite-Datenbank in `~/.filevault/vault.db`
- **Backups**: Automatische Datensicherung (konfigurierbar)

## 💻 Installation

### Option 1: Release herunterladen (empfohlen)
1. Laden Sie die [neueste Version](https://github.com/GulfGulfinson/fileVault/releases) herunter
2. Entpacken Sie die ZIP-Datei
3. Starten Sie die Anwendung:
   ```
   ./start.sh    # Für Linux/Mac
   start.bat     # Für Windows
   ```

### Option 2: Aus dem Quellcode bauen
1. Voraussetzungen:
   - Java 17+ (OpenJDK oder Oracle JDK)
   - Maven 3.8+

2. Repository klonen:
   ```bash
   git clone https://github.com/GulfGulfinson/fileVault.git
   cd fileVault
   ```

3. Bauen und Starten:
   ```bash
   mvn clean package
   ./start.sh   # Linux/Mac
   start.bat    # Windows
   ```
   
   Alternativ:
   ```bash
   mvn javafx:run
   ```

> **Hinweis**: Bei der ersten Ausführung wird automatisch ein neuer Benutzer angelegt. 
> Für einen neuen Benutzer muss die bestehende Datenbank (`~/.filevault/vault.db`) gelöscht werden.

### Zukünftige Installationsmethoden (in Entwicklung)

#### Docker Container
```bash
docker pull ghcr.io/GulfGulfinson/fileVault:latest
docker run -v ~/.filevault:/root/.filevault ghcr.io/GulfGulfinson/fileVault:latest
```

#### GitHub Packages
```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub GulfGulfinson Apache Maven Packages</name>
        <url>https://maven.pkg.github.com/GulfGulfinson/fileVault</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.filevault</groupId>
    <artifactId>FileVault</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 🚀 Verwendung

1. **Anmeldung**: Starten Sie die App und erstellen Sie ein sicheres Master-Passwort
2. **Dateien importieren**: Ziehen Sie Dateien in die App oder nutzen Sie den Import-Dialog
3. **Ordnerstruktur**: Erstellen Sie virtuelle Ordner zur Organisation
4. **Verschlüsselung**: Alle importierten Dateien werden automatisch verschlüsselt
5. **Entschlüsselung**: Exportieren Sie Dateien, um sie im Originalformat zu nutzen

## 📂 Projektstruktur

```
src/main/java/com/filevault/
├── controller/     # UI-Controller und Anwendungslogik
├── model/          # Datenmodelle und Objektstrukturen
├── view/           # JavaFX FXML und UI-Komponenten
├── security/       # Verschlüsselung und Authentifizierung
├── storage/        # Datei- und Datenbankverwaltung
└── util/           # Hilfsfunktionen und Utilities
```

## 📖 Dokumentation

- **Website**: [https://GulfGulfinson.github.io/fileVault](https://GulfGulfinson.github.io/fileVault)
- **JavaDoc**: [Vollständige API-Dokumentation](https://GulfGulfinson.github.io/fileVault/javadoc/main.html)
- **Live Demo**: [WebAssembly-Demo im Browser](https://GulfGulfinson.github.io/fileVault#wasm-demo-container)

## 🤝 Beitragen

Beiträge zum Projekt sind willkommen! Weitere Informationen finden Sie in der [CONTRIBUTING.md](docs/markdown/CONTRIBUTING.md).

## 📄 Lizenz

Dieses Projekt steht unter der MIT-Lizenz. Details finden Sie in der [LICENSE](docs/markdown/LICENSE.md) Datei.

---

Entwickelt von Phillip Schneider | [GitHub Profil](https://github.com/GulfGulfinson)
