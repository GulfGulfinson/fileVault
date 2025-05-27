#!/bin/bash

echo "Starte FileVault..."

# Get JavaFX path from Maven
JAVA_FX_VERSION="17.0.14"
M2_REPO="$HOME/.m2/repository/org/openjfx"

# Include both generic and platform-specific JavaFX JARs
JAVAFX_PATH=""
for module in javafx-base javafx-graphics javafx-controls javafx-fxml; do
    JAVAFX_PATH="$JAVAFX_PATH:$M2_REPO/$module/$JAVA_FX_VERSION/$module-$JAVA_FX_VERSION.jar"
    JAVAFX_PATH="$JAVAFX_PATH:$M2_REPO/$module/$JAVA_FX_VERSION/$module-$JAVA_FX_VERSION-linux.jar"
done
JAVAFX_PATH=${JAVAFX_PATH:1}  # Remove the leading ':'
JAVAFX_MODULES="javafx.controls,javafx.fxml,javafx.base,javafx.graphics"

# Generate classpath for other dependencies
if [ -x ./mvnw ]; then
    MVN_CMD=./mvnw
else
    MVN_CMD=mvn
fi
$MVN_CMD dependency:build-classpath -Dmdep.outputFile=cp.txt -q -DincludeScope=runtime -DexcludeGroupIds=org.openjfx
CLASSPATH=$(cat cp.txt):target/FileVault-shaded.jar
rm cp.txt

# Run the application
java \
    --module-path "$JAVAFX_PATH" \
    --add-modules "$JAVAFX_MODULES" \
    --add-opens java.base/java.lang=javafx.base \
    --add-opens java.base/java.nio=javafx.base \
    -Dprism.order=sw \
    -cp "$CLASSPATH" \
    com.filevault.FileVaultApp