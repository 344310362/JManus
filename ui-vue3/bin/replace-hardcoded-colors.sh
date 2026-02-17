#!/usr/bin/env bash
# replace-hardcoded-colors.sh
# 一键替换 CSS 硬编码颜色为主题变量
# 用法: bash bin/replace-hardcoded-colors.sh [--dry-run]
#       bash bin/replace-hardcoded-colors.sh [src_dir] [--dry-run]

set -euo pipefail

# 自动推断 src 目录：脚本所在 bin/ 的同级 src/
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_SRC="$(dirname "$SCRIPT_DIR")/src"

SRC_DIR="$DEFAULT_SRC"
DRY_RUN=false
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    *)         SRC_DIR="$arg" ;;
  esac
done

if [ ! -d "$SRC_DIR" ]; then
  echo "错误: 目录不存在: $SRC_DIR" >&2
  exit 1
fi

TOTAL_FILES=0
TOTAL_REPLACEMENTS=0

# sed 兼容: macOS 用 gsed 或 sed -i ''，Linux 用 sed -i
if command -v gsed &>/dev/null; then
  SED="gsed"
elif sed --version 2>/dev/null | grep -q GNU; then
  SED="sed"
else
  # macOS BSD sed
  SED="sed"
fi

# 通用 sed -i 封装
sedi() {
  if [[ "$SED" == "sed" ]] && ! sed --version 2>/dev/null | grep -q GNU; then
    sed -i '' "$@"
  else
    $SED -i "$@"
  fi
}

# W = rgba(255,255,255,X) 的 sed 正则 (ERE)
W='rgba\([[:space:]]*255[[:space:]]*,[[:space:]]*255[[:space:]]*,[[:space:]]*255[[:space:]]*,[[:space:]]*'
WE='[[:space:]]*\)'
# B = rgba(0,0,0,X)
B='rgba\([[:space:]]*0[[:space:]]*,[[:space:]]*0[[:space:]]*,[[:space:]]*0[[:space:]]*,[[:space:]]*'
# A = rgba(102,126,234,X)
A='rgba\([[:space:]]*102[[:space:]]*,[[:space:]]*126[[:space:]]*,[[:space:]]*234[[:space:]]*,[[:space:]]*'
# G = rgba(34,197,94,X)
G='rgba\([[:space:]]*34[[:space:]]*,[[:space:]]*197[[:space:]]*,[[:space:]]*94[[:space:]]*,[[:space:]]*'
# R = rgba(255,77,79,X)
R='rgba\([[:space:]]*255[[:space:]]*,[[:space:]]*77[[:space:]]*,[[:space:]]*79[[:space:]]*,[[:space:]]*'
# Y = rgba(251,191,36,X)
Y='rgba\([[:space:]]*251[[:space:]]*,[[:space:]]*191[[:space:]]*,[[:space:]]*36[[:space:]]*,[[:space:]]*'

# 替换规则数组: "pattern|replacement|description"
RULES=(
  # --- white alpha → surface / text ---
  "${W}0\.02${WE}|var(--surface-hover)|w-0.02→surface-hover"
  "${W}0\.03${WE}|var(--surface-hover)|w-0.03→surface-hover"
  "${W}0\.04${WE}|var(--surface-hover)|w-0.04→surface-hover"
  "${W}0\.05${WE}|var(--surface-subtle)|w-0.05→surface-subtle"
  "${W}0\.06${WE}|var(--surface-subtle)|w-0.06→surface-subtle"
  "${W}0\.08${WE}|var(--surface-default)|w-0.08→surface-default"
  "${W}0\.1${WE}|var(--surface-default)|w-0.1→surface-default"
  "${W}0\.12${WE}|var(--surface-strong)|w-0.12→surface-strong"
  "${W}0\.15${WE}|var(--surface-strong)|w-0.15→surface-strong"
  "${W}0\.2${WE}|var(--surface-emphasis)|w-0.2→surface-emphasis"
  "${W}0\.25${WE}|var(--text-faint)|w-0.25→text-faint"
  "${W}0\.3${WE}|var(--text-muted)|w-0.3→text-muted"
  "${W}0\.35${WE}|var(--text-placeholder)|w-0.35→text-placeholder"
  "${W}0\.4${WE}|var(--text-muted)|w-0.4→text-muted"
  "${W}0\.45${WE}|var(--text-muted)|w-0.45→text-muted"
  "${W}0\.5${WE}|var(--text-tertiary)|w-0.5→text-tertiary"

  # --- black alpha → overlay ---
  "${B}0\.05${WE}|var(--overlay-subtle)|b-0.05→overlay-subtle"
  "${B}0\.1${WE}|var(--overlay-subtle)|b-0.1→overlay-subtle"
  "${B}0\.15${WE}|var(--overlay-light)|b-0.15→overlay-light"
  "${B}0\.2${WE}|var(--overlay-light)|b-0.2→overlay-light"
  "${B}0\.25${WE}|var(--overlay-medium)|b-0.25→overlay-medium"
  "${B}0\.3${WE}|var(--overlay-medium)|b-0.3→overlay-medium"
  "${B}0\.4${WE}|var(--overlay-medium)|b-0.4→overlay-medium"
  "${B}0\.5${WE}|var(--overlay-heavy)|b-0.5→overlay-heavy"
  "${B}0\.6${WE}|var(--overlay-heavy)|b-0.6→overlay-heavy"

  # --- accent (102,126,234) ---
  "${A}0\.05${WE}|var(--accent-surface-1)|a-0.05→accent-surface-1"
  "${A}0\.08${WE}|var(--accent-surface-2)|a-0.08→accent-surface-2"
  "${A}0\.1${WE}|var(--accent-surface-2)|a-0.1→accent-surface-2"
  "${A}0\.15${WE}|var(--accent-glow)|a-0.15→accent-glow"
  "${A}0\.2${WE}|var(--accent-surface-3)|a-0.2→accent-surface-3"
  "${A}0\.3${WE}|var(--accent-border-3)|a-0.3→accent-border-3"

  # --- success (34,197,94) ---
  "${G}0\.05${WE}|var(--success-surface)|g-0.05→success-surface"
  "${G}0\.08${WE}|var(--success-surface)|g-0.08→success-surface"
  "${G}0\.1${WE}|var(--success-surface)|g-0.1→success-surface"
  "${G}0\.15${WE}|var(--success-border)|g-0.15→success-border"
  "${G}0\.2${WE}|var(--success-border)|g-0.2→success-border"
  "${G}0\.3${WE}|var(--success-border)|g-0.3→success-border"

  # --- error (255,77,79) ---
  "${R}0\.1${WE}|var(--error-surface)|r-0.1→error-surface"
  "${R}0\.12${WE}|var(--error-surface)|r-0.12→error-surface"
  "${R}0\.15${WE}|var(--error-surface)|r-0.15→error-surface"
  "${R}0\.2${WE}|var(--error-border)|r-0.2→error-border"
  "${R}0\.25${WE}|var(--error-border)|r-0.25→error-border"
  "${R}0\.3${WE}|var(--error-border)|r-0.3→error-border"

  # --- warning (251,191,36) ---
  "${Y}0\.08${WE}|var(--warning-surface)|y-0.08→warning-surface"
  "${Y}0\.1${WE}|var(--warning-surface)|y-0.1→warning-surface"
  "${Y}0\.2${WE}|var(--warning-border)|y-0.2→warning-border"
  "${Y}0\.25${WE}|var(--warning-border)|y-0.25→warning-border"
)

echo "========================================"
echo " CSS 硬编码颜色替换工具"
echo " 扫描目录: $SRC_DIR"
if $DRY_RUN; then
  echo " 模式: DRY RUN (仅预览)"
else
  echo " 模式: 实际替换"
fi
echo "========================================"

# 查找文件 (排除 themes/ 和 node_modules)
FILES=$(find "$SRC_DIR" \( -name "*.vue" -o -name "*.css" -o -name "*.less" \) \
  -not -path "*/themes/*" -not -path "*/node_modules/*" 2>/dev/null)

for file in $FILES; do
  file_count=0

  for rule in "${RULES[@]}"; do
    IFS='|' read -r pattern replacement desc <<< "$rule"

    # 计算匹配数
    count=$(grep -cE "$pattern" "$file" 2>/dev/null || true)
    if [ "$count" -gt 0 ]; then
      file_count=$((file_count + count))
      if ! $DRY_RUN; then
        sedi -E "s|${pattern}|${replacement}|g" "$file"
      fi
    fi
  done

  # hex 替换 (单独处理，需要更精确的匹配)
  for hex_rule in \
    "#667eea|var(--accent-primary)" \
    "#ff6b6b|var(--error-text)" \
    "#9ca3af|var(--text-tertiary)" \
    "#22c55e|var(--success-text)"; do
    IFS='|' read -r hex_val hex_repl <<< "$hex_rule"
    count=$(grep -cw "$hex_val" "$file" 2>/dev/null || true)
    if [ "$count" -gt 0 ]; then
      file_count=$((file_count + count))
      if ! $DRY_RUN; then
        sedi "s|${hex_val}|${hex_repl}|g" "$file"
      fi
    fi
  done

  if [ "$file_count" -gt 0 ]; then
    TOTAL_FILES=$((TOTAL_FILES + 1))
    TOTAL_REPLACEMENTS=$((TOTAL_REPLACEMENTS + file_count))
    rel_path="${file#$SRC_DIR/}"
    echo "  [${file_count}] ${rel_path}"
  fi
done

echo ""
echo "========================================"
echo " 完成! 共 ${TOTAL_FILES} 个文件, ${TOTAL_REPLACEMENTS} 处替换"
if $DRY_RUN; then
  echo " (DRY RUN 模式，未实际修改文件)"
  echo " 去掉 --dry-run 参数执行实际替换"
fi
echo "========================================"
