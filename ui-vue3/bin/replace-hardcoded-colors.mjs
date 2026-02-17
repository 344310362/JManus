#!/usr/bin/env node
/**
 * CSS 硬编码颜色替换工具 (Node.js 版)
 * 用法: node bin/replace-hardcoded-colors.mjs [--dry-run]
 */
import { readFileSync, writeFileSync } from 'fs';
import { resolve, relative } from 'path';
import { globSync } from 'fs';

// ---- 配置 ----
const srcDir = resolve(import.meta.dirname, '..', 'src');
const dryRun = process.argv.includes('--dry-run');

// ---- 替换规则: [pattern, replacement, description] ----
const rules = [
  // rgba(255, 255, 255, X) → surface / text 变量
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.0[23]\s*\)/g, 'var(--surface-hover)', 'white-alpha-low → surface-hover'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.0[456]\s*\)/g, 'var(--surface-subtle)', 'white-alpha → surface-subtle'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.0[89]\s*\)/g, 'var(--surface-default)', 'white-alpha → surface-default'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.1\s*\)/g, 'var(--border-default)', 'white-alpha-0.1 → border-default'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.12\s*\)/g, 'var(--surface-strong)', 'white-alpha-0.12 → surface-strong'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.15\s*\)/g, 'var(--surface-strong)', 'white-alpha-0.15 → surface-strong'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.2\s*\)/g, 'var(--border-primary)', 'white-alpha-0.2 → border-primary'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.25\s*\)/g, 'var(--text-faint)', 'white-alpha-0.25 → text-faint'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.3\s*\)/g, 'var(--scrollbar-thumb-hover)', 'white-alpha-0.3 → scrollbar-thumb-hover'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.35\s*\)/g, 'var(--text-placeholder)', 'white-alpha-0.35 → text-placeholder'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.4\s*\)/g, 'var(--text-muted)', 'white-alpha-0.4 → text-muted'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.5\s*\)/g, 'var(--text-tertiary)', 'white-alpha-0.5 → text-tertiary'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.6\s*\)/g, 'var(--text-tertiary)', 'white-alpha-0.6 → text-tertiary'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.7\s*\)/g, 'var(--text-secondary)', 'white-alpha-0.7 → text-secondary'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.8\s*\)/g, 'var(--text-secondary)', 'white-alpha-0.8 → text-secondary'],
  [/rgba\(\s*255\s*,\s*255\s*,\s*255\s*,\s*0\.9[0-5]?\s*\)/g, 'var(--text-primary)', 'white-alpha-0.9+ → text-primary'],

  // rgba(0, 0, 0, X) → overlay 变量
  [/rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.0[5-9]\s*\)/g, 'var(--overlay-subtle)', 'black-alpha-low → overlay-subtle'],
  [/rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.1\s*\)/g, 'var(--overlay-subtle)', 'black-alpha-0.1 → overlay-subtle'],
  [/rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.1[5-9]\s*\)/g, 'var(--overlay-light)', 'black-alpha → overlay-light'],
  [/rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.2\s*\)/g, 'var(--overlay-light)', 'black-alpha-0.2 → overlay-light'],
  [/rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.2[5-9]\s*\)/g, 'var(--overlay-medium)', 'black-alpha → overlay-medium'],
  [/rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.[34]\s*\)/g, 'var(--overlay-medium)', 'black-alpha → overlay-medium'],
  [/rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.[56]\s*\)/g, 'var(--overlay-heavy)', 'black-alpha → overlay-heavy'],
  [/rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\.7\s*\)/g, 'var(--overlay-heavy)', 'black-alpha-0.7 → overlay-heavy'],

  // rgba(102, 126, 234, X) → accent 变量
  [/rgba\(\s*102\s*,\s*126\s*,\s*234\s*,\s*0\.05\s*\)/g, 'var(--accent-surface-1)', 'accent-0.05 → accent-surface-1'],
  [/rgba\(\s*102\s*,\s*126\s*,\s*234\s*,\s*0\.1\s*\)/g, 'var(--accent-surface-2)', 'accent-0.1 → accent-surface-2'],
  [/rgba\(\s*102\s*,\s*126\s*,\s*234\s*,\s*0\.15\s*\)/g, 'var(--accent-glow)', 'accent-0.15 → accent-glow'],
  [/rgba\(\s*102\s*,\s*126\s*,\s*234\s*,\s*0\.2\s*\)/g, 'var(--accent-surface-3)', 'accent-0.2 → accent-surface-3'],
  [/rgba\(\s*102\s*,\s*126\s*,\s*234\s*,\s*0\.3\s*\)/g, 'var(--accent-border-3)', 'accent-0.3 → accent-border-3'],
  [/rgba\(\s*102\s*,\s*126\s*,\s*234\s*,\s*0\.5\s*\)/g, 'var(--accent-border-3)', 'accent-0.5 → accent-border-3'],
  [/rgba\(\s*102\s*,\s*126\s*,\s*234\s*,\s*0\.6\s*\)/g, 'var(--accent-primary)', 'accent-0.6 → accent-primary'],

  // 状态色
  [/rgba\(\s*34\s*,\s*197\s*,\s*94\s*,\s*0\.0[5-9]\s*\)/g, 'var(--success-surface)', 'green → success-surface'],
  [/rgba\(\s*34\s*,\s*197\s*,\s*94\s*,\s*0\.[12]\s*\)/g, 'var(--success-border)', 'green → success-border'],
  [/rgba\(\s*34\s*,\s*197\s*,\s*94\s*,\s*0\.3\s*\)/g, 'var(--success-border)', 'green-0.3 → success-border'],

  [/rgba\(\s*255\s*,\s*77\s*,\s*79\s*,\s*0\.1[0-5]?\s*\)/g, 'var(--error-surface)', 'red → error-surface'],
  [/rgba\(\s*255\s*,\s*77\s*,\s*79\s*,\s*0\.[23]\s*\)/g, 'var(--error-border)', 'red → error-border'],
  [/rgba\(\s*255\s*,\s*77\s*,\s*79\s*,\s*0\.25\s*\)/g, 'var(--error-border)', 'red-0.25 → error-border'],

  [/rgba\(\s*251\s*,\s*191\s*,\s*36\s*,\s*0\.0[89]\s*\)/g, 'var(--warning-surface)', 'yellow → warning-surface'],
  [/rgba\(\s*251\s*,\s*191\s*,\s*36\s*,\s*0\.1\s*\)/g, 'var(--warning-surface)', 'yellow-0.1 → warning-surface'],
  [/rgba\(\s*251\s*,\s*191\s*,\s*36\s*,\s*0\.2[0-5]?\s*\)/g, 'var(--warning-border)', 'yellow → warning-border'],

  // 硬编码 hex
  [/(?<![-\w])#667eea(?![0-9a-fA-F])/g, 'var(--accent-primary)', '#667eea → accent-primary'],
  [/(?<![-\w])#ff6b6b(?![0-9a-fA-F])/g, 'var(--error-text)', '#ff6b6b → error-text'],
  [/(?<![-\w])#9ca3af(?![0-9a-fA-F])/g, 'var(--text-tertiary)', '#9ca3af → text-tertiary'],
  [/(?<![-\w])#22c55e(?![0-9a-fA-F])/g, 'var(--success-text)', '#22c55e → success-text'],
];

// ---- 判断匹配位置是否在 var() 内部或 CSS 变量定义行 ----
function isInsideVar(content, matchIndex) {
  // 往前找最近的 var( 或 )
  const before = content.substring(Math.max(0, matchIndex - 80), matchIndex);
  // 如果前面有未闭合的 var(，说明在 fallback 里
  const lastVarOpen = before.lastIndexOf('var(');
  if (lastVarOpen === -1) return false;
  const lastClose = before.lastIndexOf(')');
  return lastClose < lastVarOpen; // var( 在 ) 之后 → 未闭合
}

function isVarDefinitionLine(content, matchIndex) {
  // 找到当前行的开头
  const lineStart = content.lastIndexOf('\n', matchIndex) + 1;
  const line = content.substring(lineStart, matchIndex);
  return /^\s*--/.test(line);
}

function isInsideSvgDataUri(content, matchIndex) {
  const before = content.substring(Math.max(0, matchIndex - 200), matchIndex);
  return before.includes('data:image/svg+xml');
}

// ---- 收集文件 ----
import { readdirSync, statSync } from 'fs';

function walkDir(dir) {
  const results = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const fullPath = resolve(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules' || entry.name === 'themes') continue;
      results.push(...walkDir(fullPath));
    } else if (/\.(vue|css|less)$/.test(entry.name)) {
      results.push(fullPath);
    }
  }
  return results;
}

const files = walkDir(srcDir);
let totalFiles = 0;
let totalReplacements = 0;

console.log('========================================');
console.log(` CSS 硬编码颜色替换工具 (Node.js)`);
console.log(` 扫描目录: ${srcDir}`);
console.log(` 文件数量: ${files.length}`);
console.log(` 模式: ${dryRun ? 'DRY RUN (仅预览)' : '实际替换'}`);
console.log('========================================');

for (const filePath of files) {
  let content = readFileSync(filePath, 'utf-8');
  const original = content;
  let fileReplacements = 0;
  const details = [];

  for (const [pattern, replacement, desc] of rules) {
    // 重置 regex lastIndex
    pattern.lastIndex = 0;

    // 先找所有匹配，过滤掉 var() 内的
    const validMatches = [];
    let m;
    while ((m = pattern.exec(content)) !== null) {
      if (!isInsideVar(content, m.index) &&
          !isVarDefinitionLine(content, m.index) &&
          !isInsideSvgDataUri(content, m.index)) {
        validMatches.push(m);
      }
    }

    if (validMatches.length === 0) continue;

    // 从后往前替换，避免 index 偏移
    let newContent = content;
    for (let i = validMatches.length - 1; i >= 0; i--) {
      const match = validMatches[i];
      newContent = newContent.substring(0, match.index) +
                   replacement +
                   newContent.substring(match.index + match[0].length);
    }

    fileReplacements += validMatches.length;
    details.push(`    ${desc} (x${validMatches.length})`);
    content = newContent;
  }

  if (fileReplacements > 0) {
    totalFiles++;
    totalReplacements += fileReplacements;
    const rel = relative(srcDir, filePath).replace(/\\/g, '/');
    console.log(`  [${fileReplacements}] ${rel}`);
    for (const d of details) console.log(d);

    if (!dryRun) {
      writeFileSync(filePath, content, 'utf-8');
    }
  }
}

console.log('');
console.log('========================================');
console.log(` 完成! 共 ${totalFiles} 个文件, ${totalReplacements} 处替换`);
if (dryRun) {
  console.log(' (DRY RUN 模式，未实际修改文件)');
  console.log(' 去掉 --dry-run 参数执行实际替换');
}
console.log('========================================');
