#!/bin/bash
java --module-path "./lib/javafx-sdk-17.0.14/lib" --add-modules javafx.controls,javafx.fxml -jar target/FileVault-shaded.jar
read -p "Drücke [Enter] zum weitermachen..."