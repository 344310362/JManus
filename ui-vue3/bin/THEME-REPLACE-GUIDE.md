# CSS 主题色替换工具

将 `.vue` / `.css` / `.less` 文件中硬编码的 `rgba(...)` 和 hex 颜色值，批量替换为 CSS 主题变量，解决 dark/light 主题切换时的颜色冲突问题（如白色背景 + 白色文字不可见）。

## 快速开始

在 `ui-vue3/` 目录下执行：

```bash
# 1. 先预览（不修改文件，只统计替换数量）
bin\replace-hardcoded-colors.bat --dry-run        # Windows CMD
pwsh bin/replace-hardcoded-colors.ps1 -DryRun     # PowerShell
bash bin/replace-hardcoded-colors.sh --dry-run    # Git Bash / Linux / macOS

# 2. 确认无误后执行实际替换
bin\replace-hardcoded-colors.bat                   # Windows CMD
pwsh bin/replace-hardcoded-colors.ps1              # PowerShell
bash bin/replace-hardcoded-colors.sh               # Git Bash / Linux / macOS
```

脚本会自动定位 `ui-vue3/src` 目录，也可手动指定：

```bash
pwsh bin/replace-hardcoded-colors.ps1 -SrcDir "D:/other/project/src"
bash bin/replace-hardcoded-colors.sh /path/to/src
```

## 替换规则

### 1. 半透明白色 → `--surface-*` / `--text-*`

原本用于暗色主题的白色叠加层，在亮色主题下不可见。

| 原始值 | 替换为 | 语义 |
|--------|--------|------|
| `rgba(255,255,255, 0.02~0.04)` | `var(--surface-hover)` | 极微弱悬停态 |
| `rgba(255,255,255, 0.05~0.06)` | `var(--surface-subtle)` | 微弱表面提升 |
| `rgba(255,255,255, 0.08~0.1)` | `var(--surface-default)` | 默认表面提升 |
| `rgba(255,255,255, 0.12~0.15)` | `var(--surface-strong)` | 较强表面提升 |
| `rgba(255,255,255, 0.2)` | `var(--surface-emphasis)` | 强调表面 |
| `rgba(255,255,255, 0.25)` | `var(--text-faint)` | 极弱文本 |
| `rgba(255,255,255, 0.3~0.45)` | `var(--text-muted)` | 弱化文本 |
| `rgba(255,255,255, 0.35)` | `var(--text-placeholder)` | 占位符文本 |
| `rgba(255,255,255, 0.5)` | `var(--text-tertiary)` | 三级文本 |

### 2. 半透明黑色 → `--overlay-*`

| 原始值 | 替换为 | 语义 |
|--------|--------|------|
| `rgba(0,0,0, 0.05~0.1)` | `var(--overlay-subtle)` | 微弱遮罩 |
| `rgba(0,0,0, 0.15~0.2)` | `var(--overlay-light)` | 轻遮罩 |
| `rgba(0,0,0, 0.25~0.4)` | `var(--overlay-medium)` | 中等遮罩 |
| `rgba(0,0,0, 0.5~0.6)` | `var(--overlay-heavy)` | 重遮罩（弹窗背景） |

### 3. 主题色 (102,126,234) → `--accent-*`

| 原始值 | 替换为 | 语义 |
|--------|--------|------|
| `rgba(102,126,234, 0.05)` | `var(--accent-surface-1)` | 极浅主题色背景 |
| `rgba(102,126,234, 0.08~0.1)` | `var(--accent-surface-2)` | 浅主题色背景 |
| `rgba(102,126,234, 0.15)` | `var(--accent-glow)` | 主题色光晕 |
| `rgba(102,126,234, 0.2)` | `var(--accent-surface-3)` | 中等主题色背景 |
| `rgba(102,126,234, 0.3)` | `var(--accent-border-3)` | 主题色边框 |

### 4. 状态色

| 原始值 | 替换为 |
|--------|--------|
| `rgba(34,197,94, 0.05~0.1)` | `var(--success-surface)` |
| `rgba(34,197,94, 0.15~0.3)` | `var(--success-border)` |
| `rgba(255,77,79, 0.1~0.15)` | `var(--error-surface)` |
| `rgba(255,77,79, 0.2~0.3)` | `var(--error-border)` |
| `rgba(251,191,36, 0.08~0.1)` | `var(--warning-surface)` |
| `rgba(251,191,36, 0.2~0.25)` | `var(--warning-border)` |

### 5. Hex 颜色

| 原始值 | 替换为 |
|--------|--------|
| `#667eea` | `var(--accent-primary)` |
| `#ff6b6b` | `var(--error-text)` |
| `#9ca3af` | `var(--text-tertiary)` |
| `#22c55e` | `var(--success-text)` |

## 主题变量定义

变量定义在以下两个文件中：

- `src/assets/themes/dark.css` — 暗色主题（默认）
- `src/assets/themes/light.css` — 亮色主题

每个变量在两个主题中有不同的值。例如：

```css
/* dark.css */
--surface-default: rgba(255, 255, 255, 0.08);  /* 白色叠加 → 微亮 */
--text-muted:      rgba(255, 255, 255, 0.4);   /* 半透明白 → 灰色文字 */

/* light.css */
--surface-default: rgba(0, 0, 0, 0.06);        /* 黑色叠加 → 微暗 */
--text-muted:      rgba(0, 0, 0, 0.4);         /* 半透明黑 → 灰色文字 */
```

### 完整变量清单

| 分组 | 变量名 | 用途 |
|------|--------|------|
| 表面 | `--surface-hover` | 悬停态背景 |
| | `--surface-subtle` | 微弱表面 |
| | `--surface-default` | 默认表面 |
| | `--surface-strong` | 较强表面 |
| | `--surface-emphasis` | 强调表面 |
| 遮罩 | `--overlay-subtle` | 微弱遮罩 |
| | `--overlay-light` | 轻遮罩 |
| | `--overlay-medium` | 中等遮罩 |
| | `--overlay-heavy` | 重遮罩 |
| 文本 | `--text-muted` | 弱化文本 |
| | `--text-faint` | 极弱文本 |
| | `--text-placeholder` | 占位符 |
| 边框 | `--border-subtle` | 微弱边框 |
| | `--border-default` | 默认边框 |
| | `--border-strong` | 较强边框 |
| | `--border-hard` | 实色边框 |
| 主题色 | `--accent-surface-1/2/3` | 主题色背景 |
| | `--accent-border-1/2/3` | 主题色边框 |
| | `--accent-glow` | 主题色光晕 |
| 状态 | `--success-surface/border/text` | 成功色 |
| | `--error-surface/border/text` | 错误色 |
| | `--warning-surface/border/text` | 警告色 |
| 阴影 | `--shadow-sm/md/lg` | 阴影层级 |
| | `--shadow-accent` | 主题色阴影 |

## 注意事项

1. 脚本会自动跳过 `src/assets/themes/` 目录，避免修改主题定义文件本身
2. 脚本会跳过 `node_modules` 目录
3. 同一个 `rgba` 值在不同上下文（background / border / color）中可能语义不同，替换后建议人工检查关键页面
4. `linear-gradient(...)` 内的 `rgba` 值也会被替换，如果渐变效果异常需手动调整
5. 已经使用 `var(--xxx, fallback)` 写法的不会被重复替换
6. 建议在替换前先 `git stash` 或提交当前改动，方便回滚
