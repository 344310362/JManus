# JManus Color Replacer Utility

This utility replaces hardcoded color values in Vue component files with theme variables defined in the project's theme CSS files.

## Purpose

The utility helps maintain consistent theming across the application by replacing hardcoded color values with CSS variables that respect the active theme (dark/light).

## How it works

1. The utility scans all Vue files in the specified directory (default: `ui-vue3/src/views`)
2. It extracts color variables from the theme CSS files (in `ui-vue3/src/assets/themes`)
3. It replaces hardcoded color values with CSS `var()` functions referencing the appropriate theme variables
4. It preserves the original color as a fallback value in case the variable is not defined

## Usage

### Running the utility

#### On Unix-like systems (Linux/macOS):
```bash
# From the tools directory
node replace-colors.js

# Or using npm script
npm run replace-colors

# With custom paths
node replace-colors.js ../ui-vue3/src/views ../ui-vue3/src/assets/themes
node replace-colors.js ../ui-vue3/src/components ../ui-vue3/src/assets/themes

# Using Makefile target
make replace-colors
```

#### On Windows:
```cmd
# Run the batch script
tools\replace-colors.bat
```

### Command line arguments

1. First argument: Path to Vue components directory (default: `../ui-vue3/src/views`)
2. Second argument: Path to themes directory (default: `../ui-vue3/src/assets/themes`)

## Example

Before:
```css
background: #0a0a0a;
color: #ffffff;
```

After:
```css
background: var(--bg-primary, #0a0a0a);
color: var(--text-primary, #ffffff);
```

## Theme Files

The utility processes the following theme files:
- `dark.css` - Dark theme (default)
- `light.css` - Light theme
- `light-back.css` - Alternative light theme

## Color Mapping

The utility maps hardcoded colors to the following theme variables:

### Background Colors
- `--bg-primary` - Main background
- `--bg-secondary` - Secondary background
- `--bg-tertiary` - Tertiary background
- `--bg-card` - Card background
- `--bg-input` - Input field background

### Text Colors
- `--text-primary` - Primary text
- `--text-secondary` - Secondary text
- `--text-tertiary` - Tertiary text
- `--text-heading` - Heading text
- `--text-link` - Link text

### Border Colors
- `--border-primary` - Primary border
- `--border-secondary` - Secondary border

### Accent Colors
- `--accent-primary` - Primary accent
- `--accent-secondary` - Secondary accent
- `--accent-tertiary` - Tertiary accent

### Status Colors
- `--success` - Success state
- `--warning` - Warning state
- `--error` - Error state
- `--info` - Information state

### Special Colors
- `--scrollbar-track` - Scrollbar track
- `--scrollbar-thumb` - Scrollbar thumb
- `--scrollbar-thumb-hover` - Scrollbar thumb on hover
- `--selection-bg` - Text selection background
