REM ============================================================
REM  Generate app icon PNG files for 2FA J2ME app
REM  Uses Python with Pillow to create shield+keyhole icons
REM  in warm amber/cream/teal palette at 25/29/32px + 512px master
REM
REM  Requires: python3, pip install pillow
REM ============================================================

@echo off
echo Generating 2FA app icons...

python "%~dp0generate_icons.py"

if errorlevel 1 (
    echo.
    echo If Python is not installed, install it:
    echo   winget install Python.Python.3.12
    echo Then install Pillow:
    echo   pip install Pillow
    echo.
    echo Alternatively, the app will work without icons (no icon shown in list).
)

echo Done.
pause
