@echo off
REM Batch script to run the color replacement utility

cd /d "%~dp0"
echo Running color replacement utility...
node tools/replace-colors.js
echo Color replacement completed!
pause