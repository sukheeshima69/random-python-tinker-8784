@echo off
REM ============================================================
REM  2FA J2ME — Build Script for WTK 2.5.2 + JDK 8 (32-bit)
REM  Run from the 2FA-J2ME directory.
REM
REM  Prerequisites:
REM    - JDK 8  (32-bit, e.g. C:\Java\jdk1.8.0_xxx)
REM    - WTK 2.5.2  (e.g. C:\WTK252)
REM
REM  Usage:
REM    build.bat                  — compile + package
REM    build.bat run              — compile + package + run emulator
REM    build.bat clean            — delete build artifacts
REM ============================================================

setlocal enabledelayedexpansion

REM ---- EDIT THESE PATHS to match your installation ----
set "JAVA_HOME=C:\Program Files (x86)\Java\jdk1.8.0_202"
set "WTK_HOME=C:\WTK252"
REM -----------------------------------------------------

set "PROJECT_DIR=%~dp0"
set "SRC_DIR=%PROJECT_DIR%src"
set "RES_DIR=%PROJECT_DIR%res"
set "BUILD_DIR=%PROJECT_DIR%build"
set "BIN_DIR=%PROJECT_DIR%bin"
set "DIST_DIR=%PROJECT_DIR%dist"

set "CLDC_JAR=%WTK_HOME%\lib\cldcapi11.jar"
set "MIDP_JAR=%WTK_HOME%\lib\midpapi20.jar"
REM Compile-time-only MMAPI stubs (JSR-135). Shipped in lib\, never packaged.
REM The phone itself provides the real camera implementation.
set "MMAPI_JAR=%PROJECT_DIR%lib\microemu-jsr-135-2.0.4.jar"
set "MIDPEXT_JAR=%PROJECT_DIR%lib\microemu-midp-2.0.4.jar"
set "FILECONN_JAR=%PROJECT_DIR%lib\microemu-jsr-75-2.0.4.jar"
set "PREVERIFY=%WTK_HOME%\bin\preverify.exe"
set "JAR=%JAVA_HOME%\bin\jar.exe"
set "JAVAC=%JAVA_HOME%\bin\javac.exe"

set "MIDLET_NAME=2FA"
set "JAR_FILE=%DIST_DIR%\%MIDLET_NAME%.jar"
set "JAD_FILE=%DIST_DIR%\%MIDLET_NAME%.jad"

REM ---- Handle arguments ----
if "%1"=="clean" goto :clean
if "%1"=="run" goto :build_run
goto :build

:clean
echo Cleaning build artifacts...
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%BIN_DIR%" rmdir /s /q "%BIN_DIR%"
if exist "%JAR_FILE%" del "%JAR_FILE%"
echo Done.
goto :eof

:build_run
call :do_build
if errorlevel 1 goto :eof
echo.
echo Starting emulator...
"%WTK_HOME%\bin\emulator.exe" -Xdescriptor:"%JAD_FILE%"
goto :eof

:build
call :do_build
goto :eof

:do_build
echo.
echo ============================================================
echo  2FA J2ME Build
echo ============================================================
echo.

REM Check tools exist
if not exist "%JAVAC%" (
    echo ERROR: javac not found at %JAVAC%
    echo Edit JAVA_HOME in this script.
    exit /b 1
)
if not exist "%PREVERIFY%" (
    echo ERROR: preverify not found at %PREVERIFY%
    echo Edit WTK_HOME in this script.
    exit /b 1
)

REM Clean build dir
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%BIN_DIR%" rmdir /s /q "%BIN_DIR%"
mkdir "%BUILD_DIR%"
mkdir "%BIN_DIR%"

echo [1/4] Compiling Java sources...
REM Collect every .java under src\ (app + vendored QR decoder) into an
REM argument file, so new packages never need manual glob updates.
REM NOTE: paths are written with forward slashes because javac treats
REM backslash as an escape character inside @argument files.
(for /R "%SRC_DIR%" %%f in (*.java) do (
  set "SRCPATH=%%f"
  echo "!SRCPATH:\=/!"
)) > "%BUILD_DIR%\sources.txt"
"%JAVAC%" -source 1.3 -target 1.1 -bootclasspath "%CLDC_JAR%;%MIDP_JAR%" -classpath "%CLDC_JAR%;%MIDP_JAR%;%MMAPI_JAR%;%MIDPEXT_JAR%;%FILECONN_JAR%" -d "%BUILD_DIR%" -g:none -O @"%BUILD_DIR%\sources.txt"
if errorlevel 1 (
    echo COMPILATION FAILED
    exit /b 1
)
echo      OK

echo [2/4] Preverifying for CLDC 1.1...
"%PREVERIFY%" -classpath "%CLDC_JAR%;%MIDP_JAR%;%MMAPI_JAR%;%MIDPEXT_JAR%;%FILECONN_JAR%" -d "%BIN_DIR%" "%BUILD_DIR%"
if errorlevel 1 (
    echo PREVERIFY FAILED
    exit /b 1
)
echo      OK

echo [3/4] Packaging JAR...
REM Copy resources into the output
if exist "%RES_DIR%" xcopy /s /e /y /q "%RES_DIR%\*" "%BIN_DIR%\" >nul

REM Build manifest
copy /y "%SRC_DIR%\MANIFEST.MF" "%BUILD_DIR%\MANIFEST.MF" >nul

REM Create JAR
pushd "%BIN_DIR%"
"%JAR%" cfm "%JAR_FILE%" "%BUILD_DIR%\MANIFEST.MF" .
popd
if errorlevel 1 (
    echo JAR PACKAGING FAILED
    exit /b 1
)
echo      OK

echo [4/4] Updating JAD file...
REM Update the JAR size in the JAD file
for %%A in ("%JAR_FILE%") do set JAR_SIZE=%%~zA
(
    echo MIDlet-Name: %MIDLET_NAME%
    echo MIDlet-Version: 1.0.0
    echo MIDlet-Vendor: Rupa
    echo MIDlet-Description: TOTP Authenticator for Nokia E71
    echo MIDlet-1: %MIDLET_NAME%, /icon25.png, com.twofa.app.TwoFAMidlet
    echo MicroEdition-Profile: MIDP-2.0
    echo MicroEdition-Configuration: CLDC-1.1
    echo MIDlet-Jar-Size: %JAR_SIZE%
    echo MIDlet-Jar-URL: %MIDLET_NAME%.jar
    echo MIDlet-Data-Size: 4096
) > "%JAD_FILE%"
echo      OK

echo.
echo ============================================================
echo  BUILD SUCCESSFUL
echo  JAR: %JAR_FILE%
echo  JAD: %JAD_FILE%
echo  Size: %JAR_SIZE% bytes
echo.
echo  To run in emulator:  build.bat run
echo  To deploy to E71:    Copy 2FA.jar to phone via Bluetooth/USB
echo ============================================================
exit /b 0
