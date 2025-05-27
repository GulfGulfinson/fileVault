@echo off
setlocal EnableDelayedExpansion

echo Starte FileVault...

REM Get JavaFX path from Maven
set "JAVA_FX_VERSION=17.0.14"
set "M2_REPO=%USERPROFILE%\.m2\repository\org\openjfx"

REM Include both generic and platform-specific JavaFX JARs
set "JAVAFX_PATH="
for %%m in (javafx-base javafx-graphics javafx-controls javafx-fxml) do (
    if "!JAVAFX_PATH!"=="" (
        set "JAVAFX_PATH=!M2_REPO!\%%m\%JAVA_FX_VERSION%\%%m-%JAVA_FX_VERSION%.jar;!M2_REPO!\%%m\%JAVA_FX_VERSION%\%%m-%JAVA_FX_VERSION%-win.jar"
    ) else (
        set "JAVAFX_PATH=!JAVAFX_PATH!;!M2_REPO!\%%m\%JAVA_FX_VERSION%\%%m-%JAVA_FX_VERSION%.jar;!M2_REPO!\%%m\%JAVA_FX_VERSION%\%%m-%JAVA_FX_VERSION%-win.jar"
    )
)
set "JAVAFX_MODULES=javafx.controls,javafx.fxml,javafx.base,javafx.graphics"

REM Generate classpath for other dependencies
if exist mvnw.cmd (
    set MVN_CMD=mvnw.cmd
) else (
    set MVN_CMD=mvn
)
%MVN_CMD% dependency:build-classpath -Dmdep.outputFile=cp.txt -q -DincludeScope=runtime -DexcludeGroupIds=org.openjfx
set /p CLASSPATH=<cp.txt
set "CLASSPATH=%CLASSPATH%;target\FileVault-shaded.jar"
del cp.txt

REM Run the application
java ^
    --module-path "!JAVAFX_PATH!" ^
    --add-modules %JAVAFX_MODULES% ^
    --add-opens java.base/java.lang=javafx.base ^
    --add-opens java.base/java.nio=javafx.base ^
    -Dprism.order=sw ^
    -cp "!CLASSPATH!" ^
    com.filevault.FileVaultApp

if errorlevel 1 (
    echo Fehler beim Starten der Anwendung.
    exit /b 1
) 