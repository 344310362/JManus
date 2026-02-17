# replace-hardcoded-colors.ps1
# 一键替换 CSS 硬编码颜色为主题变量
# 用法: .\bin\replace-hardcoded-colors.ps1 [-SrcDir "src"] [-DryRun]

param(
    [string]$SrcDir = "",
    [switch]$DryRun
)

# 自动推断 src 目录：脚本所在 bin/ 的同级 src/
if (-not $SrcDir) {
    $ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $SrcDir = Join-Path (Split-Path -Parent $ScriptDir) "src"
}

$ErrorActionPreference = "Stop"

if (-not (Test-Path $SrcDir)) {
    Write-Error "目录不存在: $SrcDir"
    exit 1
}

# 统计
$totalFiles = 0
$totalReplacements = 0

# ============================================================
# 替换规则定义: [regex_pattern, replacement, description]
# 注意: 规则按顺序执行，更具体的模式放前面
# ============================================================
$rules = @(
    # ---- rgba(255, 255, 255, X) → surface / text 变量 ----
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.02\s*\)', 'var(--surface-hover)',    'white-alpha-0.02 → surface-hover'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.03\s*\)', 'var(--surface-hover)',    'white-alpha-0.03 → surface-hover'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.04\s*\)', 'var(--surface-hover)',    'white-alpha-0.04 → surface-hover'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.05\s*\)', 'var(--surface-subtle)',   'white-alpha-0.05 → surface-subtle'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.06\s*\)', 'var(--surface-subtle)',   'white-alpha-0.06 → surface-subtle'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.08\s*\)', 'var(--surface-default)',  'white-alpha-0.08 → surface-default'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.1\s*\)',  'var(--surface-default)',  'white-alpha-0.1  → surface-default'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.12\s*\)', 'var(--surface-strong)',   'white-alpha-0.12 → surface-strong'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.15\s*\)', 'var(--surface-strong)',   'white-alpha-0.15 → surface-strong'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.2\s*\)',  'var(--surface-emphasis)', 'white-alpha-0.2  → surface-emphasis'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.25\s*\)', 'var(--text-faint)',       'white-alpha-0.25 → text-faint'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.3\s*\)',  'var(--text-muted)',       'white-alpha-0.3  → text-muted'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.35\s*\)', 'var(--text-placeholder)','white-alpha-0.35 → text-placeholder'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.4\s*\)',  'var(--text-muted)',       'white-alpha-0.4  → text-muted'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.45\s*\)', 'var(--text-muted)',       'white-alpha-0.45 → text-muted'),
    @('rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.5\s*\)',  'var(--text-tertiary)',    'white-alpha-0.5  → text-tertiary'),

    # ---- rgba(0, 0, 0, X) → overlay 变量 ----
    @('rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.05\s*\)', 'var(--overlay-subtle)',  'black-alpha-0.05 → overlay-subtle'),
    @('rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.1\s*\)',  'var(--overlay-subtle)',  'black-alpha-0.1  → overlay-subtle'),
    @('rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.15\s*\)', 'var(--overlay-light)',   'black-alpha-0.15 → overlay-light'),
    @('rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.2\s*\)',  'var(--overlay-light)',   'black-alpha-0.2  → overlay-light'),
    @('rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.25\s*\)', 'var(--overlay-medium)',  'black-alpha-0.25 → overlay-medium'),
    @('rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.3\s*\)',  'var(--overlay-medium)',  'black-alpha-0.3  → overlay-medium'),
    @('rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.4\s*\)',  'var(--overlay-medium)',  'black-alpha-0.4  → overlay-medium'),
    @('rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.5\s*\)',  'var(--overlay-heavy)',   'black-alpha-0.5  → overlay-heavy'),
    @('rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.6\s*\)',  'var(--overlay-heavy)',   'black-alpha-0.6  → overlay-heavy'),

    # ---- rgba(102, 126, 234, X) → accent 变量 ----
    @('rgba\(\s*102\s*,\s*126\s*,\s*234\s*,\s*0\.05\s*\)', 'var(--accent-surface-1)', 'accent-0.05 → accent-surface-1'),
    @('rgba\(\s*102\s*,\s*126\s*,\s*234\s*,\s*0\.08\s*\)', 'var(--accent-surface-2)', 'accent-0.08 → accent-surface-2'),
    @('rgba\(\s*102\s*,\s*126\s*,\s*234\s*,\s*0\.1\s*\)',  'var(--accent-surface-2)', 'accent-0.1  → accent-surface-2'),
    @('rgba\(\s*102\s*,\s*126\s*,\s*234\s*,\s*0\.15\s*\)', 'var(--accent-glow)',      'accent-0.15 → accent-glow'),
    @('rgba\(\s*102\s*,\s*126\s*,\s*234\s*,\s*0\.2\s*\)',  'var(--accent-surface-3)', 'accent-0.2  → accent-surface-3'),
    @('rgba\(\s*102\s*,\s*126\s*,\s*234\s*,\s*0\.3\s*\)',  'var(--accent-border-3)',  'accent-0.3  → accent-border-3'),

    # ---- 状态色 surfaces ----
    @('rgba\(\s*34\s*,\s*197\s*,\s*94\s*,\s*0\.05\s*\)',  'var(--success-surface)', 'green-0.05 → success-surface'),
    @('rgba\(\s*34\s*,\s*197\s*,\s*94\s*,\s*0\.08\s*\)',  'var(--success-surface)', 'green-0.08 → success-surface'),
    @('rgba\(\s*34\s*,\s*197\s*,\s*94\s*,\s*0\.1\s*\)',   'var(--success-surface)', 'green-0.1  → success-surface'),
    @('rgba\(\s*34\s*,\s*197\s*,\s*94\s*,\s*0\.15\s*\)',  'var(--success-border)',  'green-0.15 → success-border'),
    @('rgba\(\s*34\s*,\s*197\s*,\s*94\s*,\s*0\.2\s*\)',   'var(--success-border)',  'green-0.2  → success-border'),
    @('rgba\(\s*34\s*,\s*197\s*,\s*94\s*,\s*0\.3\s*\)',   'var(--success-border)',  'green-0.3  → success-border'),

    @('rgba\(\s*255\s*,\s*77\s*,\s*79\s*,\s*0\.1\s*\)',   'var(--error-surface)',   'red-0.1  → error-surface'),
    @('rgba\(\s*255\s*,\s*77\s*,\s*79\s*,\s*0\.12\s*\)',  'var(--error-surface)',   'red-0.12 → error-surface'),
    @('rgba\(\s*255\s*,\s*77\s*,\s*79\s*,\s*0\.15\s*\)',  'var(--error-surface)',   'red-0.15 → error-surface'),
    @('rgba\(\s*255\s*,\s*77\s*,\s*79\s*,\s*0\.2\s*\)',   'var(--error-border)',    'red-0.2  → error-border'),
    @('rgba\(\s*255\s*,\s*77\s*,\s*79\s*,\s*0\.25\s*\)',  'var(--error-border)',    'red-0.25 → error-border'),
    @('rgba\(\s*255\s*,\s*77\s*,\s*79\s*,\s*0\.3\s*\)',   'var(--error-border)',    'red-0.3  → error-border'),

    @('rgba\(\s*251\s*,\s*191\s*,\s*36\s*,\s*0\.08\s*\)', 'var(--warning-surface)', 'yellow-0.08 → warning-surface'),
    @('rgba\(\s*251\s*,\s*191\s*,\s*36\s*,\s*0\.1\s*\)',  'var(--warning-surface)', 'yellow-0.1  → warning-surface'),
    @('rgba\(\s*251\s*,\s*191\s*,\s*36\s*,\s*0\.2\s*\)',  'var(--warning-border)',  'yellow-0.2  → warning-border'),
    @('rgba\(\s*251\s*,\s*191\s*,\s*36\s*,\s*0\.25\s*\)', 'var(--warning-border)',  'yellow-0.25 → warning-border'),

    # ---- 硬编码 hex → 变量 (仅最安全的替换) ----
    @('(?<![-\w])#667eea(?![0-9a-fA-F])', 'var(--accent-primary)', '#667eea → accent-primary'),
    @('(?<![-\w])#ff6b6b(?![0-9a-fA-F])', 'var(--error-text)',     '#ff6b6b → error-text'),
    @('(?<![-\w])#9ca3af(?![0-9a-fA-F])', 'var(--text-tertiary)',  '#9ca3af → text-tertiary'),
    @('(?<![-\w])#22c55e(?![0-9a-fA-F])', 'var(--success-text)',   '#22c55e → success-text')
)

# ============================================================
# 执行替换
# ============================================================
$files = Get-ChildItem -Path $SrcDir -Recurse -Include "*.vue","*.css","*.less" |
    Where-Object { $_.FullName -notmatch '[\\/]themes[\\/]' -and $_.FullName -notmatch 'node_modules' }

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " CSS 硬编码颜色替换工具" -ForegroundColor Cyan
Write-Host " 扫描目录: $SrcDir" -ForegroundColor Cyan
Write-Host " 文件数量: $($files.Count)" -ForegroundColor Cyan
Write-Host " 模式: $(if ($DryRun) { 'DRY RUN (仅预览)' } else { '实际替换' })" -ForegroundColor $(if ($DryRun) { 'Yellow' } else { 'Green' })
Write-Host "========================================" -ForegroundColor Cyan

foreach ($file in $files) {
    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
    $original = $content
    $fileReplacements = 0

    foreach ($rule in $rules) {
        $pattern = $rule[0]
        $replacement = $rule[1]
        $desc = $rule[2]

        # 跳过已经在 var() 内的值 和 CSS 变量定义行
        $matches = [regex]::Matches($content, $pattern)
        foreach ($m in $matches) {
            # 检查匹配位置前面是否有 var( 或 --
            $before = ""
            if ($m.Index -gt 30) {
                $before = $content.Substring($m.Index - 30, 30)
            } elseif ($m.Index -gt 0) {
                $before = $content.Substring(0, $m.Index)
            }
            if ($before -match 'var\(' -or $before -match '^\s*--') {
                continue
            }
        }

        $newContent = [regex]::Replace($content, $pattern, $replacement)
        if ($newContent -ne $content) {
            $count = [regex]::Matches($content, $pattern).Count
            $fileReplacements += $count
            $content = $newContent
        }
    }

    if ($fileReplacements -gt 0) {
        $totalFiles++
        $totalReplacements += $fileReplacements
        $relativePath = $file.FullName.Replace((Resolve-Path $SrcDir).Path, "").TrimStart('\', '/')
        Write-Host "  [${fileReplacements}] $relativePath" -ForegroundColor Yellow

        if (-not $DryRun) {
            Set-Content -Path $file.FullName -Value $content -Encoding UTF8 -NoNewline
        }
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " 完成! 共 $totalFiles 个文件, $totalReplacements 处替换" -ForegroundColor Green
if ($DryRun) {
    Write-Host " (DRY RUN 模式，未实际修改文件)" -ForegroundColor Yellow
    Write-Host " 去掉 -DryRun 参数执行实际替换" -ForegroundColor Yellow
}
Write-Host "========================================" -ForegroundColor Cyan
