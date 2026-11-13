@echo off
setlocal
cd /d "%~dp0"
echo MedAtlas Companion - pornire locala, doar pe acest laptop.
where py >nul 2>nul
if %ERRORLEVEL% EQU 0 goto withpy
where python >nul 2>nul
if %ERRORLEVEL% EQU 0 goto withpython
echo.
echo Python nu este instalat sau nu este in PATH.
echo Instaleaza Python 3, apoi ruleaza acest fisier din nou.
pause
exit /b 1
:withpy
start "MedAtlas Companion server" /min py -m http.server 8765 --bind 127.0.0.1
goto browser
:withpython
start "MedAtlas Companion server" /min python -m http.server 8765 --bind 127.0.0.1
:browser
timeout /t 2 /nobreak >nul
start "" "http://127.0.0.1:8765"
echo Companion este disponibil local la http://127.0.0.1:8765
echo Pentru oprire, inchide fereastra "MedAtlas Companion server".
:end
endlocal
