@echo off
REM replace-hardcoded-colors.bat
REM 一键替换 CSS 硬编码颜色为主题变量
REM 用法:
REM   bin\replace-hardcoded-colors.bat              (实际替换)
REM   bin\replace-hardcoded-colors.bat --dry-run    (仅预览)

setlocal

set "DRY_RUN="

if "%~1"=="--dry-run" set "DRY_RUN=-DryRun"
if "%~1"=="--help" (
    echo 用法: bin\replace-hardcoded-colors.bat [--dry-run]
    echo.
    echo   --dry-run    仅预览，不实际修改文件
    echo.
    echo 此脚本会扫描 ui-vue3/src 下的 .vue/.css/.less 文件，
    echo 将硬编码的 rgba 颜色替换为 CSS 主题变量。
    echo.
    echo 替换范围:
    echo   rgba(255,255,255,X) → --surface-* / --text-*
    echo   rgba(0,0,0,X)       → --overlay-*
    echo   rgba(102,126,234,X) → --accent-*
    echo   rgba(34,197,94,X)   → --success-*
    echo   rgba(255,77,79,X)   → --error-*
    echo   rgba(251,191,36,X)  → --warning-*
    echo   #667eea / #ff6b6b   → --accent-primary / --error-text
    exit /b 0
)

REM bat 不传 SrcDir，让 ps1 自动推断
REM 检查 PowerShell 是否可用
where pwsh >nul 2>&1
if %errorlevel%==0 (
    echo [INFO] 使用 pwsh ^(PowerShell 7+^)
    pwsh -ExecutionPolicy Bypass -File "%~dp0replace-hardcoded-colors.ps1" %DRY_RUN%
    goto :done
)

where powershell >nul 2>&1
if %errorlevel%==0 (
    echo [INFO] 使用 Windows PowerShell
    powershell -ExecutionPolicy Bypass -File "%~dp0replace-hardcoded-colors.ps1" %DRY_RUN%
    goto :done
)

echo [ERROR] 未找到 PowerShell，请安装 PowerShell 或使用 Git Bash 运行 .sh 脚本
exit /b 1

:done
endlocal
