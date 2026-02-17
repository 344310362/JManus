<!--
  /*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
-->

<template>
  <div class="code-agent-page">
    <!-- Header -->
    <header class="code-agent-header">
      <div class="header-content">
        <div class="header-logo">
          <Icon icon="carbon:code" width="24" />
          <h1>Code Agent</h1>
        </div>
        <div class="header-actions">
          <LanguageSwitcher />
          <button class="header-btn" @click="$router.push('/direct')" title="Back">
            <Icon icon="carbon:arrow-left" width="20" />
          </button>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <div class="code-agent-main">
      <!-- Left Panel - Chat -->
      <div class="chat-panel" :style="{ width: leftPanelWidth + '%' }">
        <div class="chat-panel-header">
          <h2>{{ $t('conversation') }}</h2>
          <div class="panel-header-actions">
            <span v-if="ws.connected.value" class="connection-dot connected" title="Connected" />
            <span v-else class="connection-dot" title="Disconnected" />
            <div class="history-btn-wrapper">
              <button class="header-btn" @click="toggleHistory" title="History">
                <Icon icon="carbon:recently-viewed" width="20" />
              </button>
              <!-- 历史会话下拉面板 -->
              <div v-if="showHistory" class="history-dropdown">
                <div class="history-dropdown-header">
                  <span>History</span>
                  <button class="history-close-btn" @click="showHistory = false">
                    <Icon icon="carbon:close" width="14" />
                  </button>
                </div>
                <div v-if="historyLoading" class="history-loading">
                  <Icon icon="carbon:circle-dash" class="spin" /> Loading...
                </div>
                <div v-else-if="historySessions.length === 0" class="history-empty">
                  No history found
                </div>
                <div v-else class="history-list">
                  <div
                    v-for="session in historySessions"
                    :key="session.conversationId"
                    class="history-item"
                    @click="restoreSession(session.conversationId)"
                  >
                    <div class="history-item-text">{{ session.userRequest || 'Untitled' }}</div>
                    <div class="history-item-meta">
                      <span :class="['history-status', session.completed ? 'done' : 'running']">
                        {{ session.completed ? 'Done' : 'Running' }}
                      </span>
                      <span v-if="session.roundCount > 1" class="history-rounds">{{ session.roundCount }} rounds</span>
                      <span>{{ formatTime(session.startTime) }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <button class="header-btn" @click="newChat" :title="$t('memory.newChat')">
              <Icon icon="carbon:add" width="20" />
            </button>
          </div>
        </div>

        <!-- Messages -->
        <div class="chat-messages" ref="messagesRef">
          <div v-for="(msg, i) in ws.messages.value" :key="i" class="chat-msg" :class="msg.type === 'agent-execution' ? 'assistant' : msg.role">
            <AgentExecutionCard
              v-if="msg.type === 'agent-execution'"
              :plan-record="msg.planRecord || ws.planRecord.value || restoredPlanRecord"
              :completed="msg.completed != null ? msg.completed : (ws.planCompleted.value || restoredPlanCompleted)"
            />
            <div v-else class="msg-bubble">{{ msg.content }}</div>
          </div>
          <div v-if="ws.generating.value && ws.streamingContent.value" class="chat-msg assistant">
            <div class="msg-bubble streaming">{{ ws.streamingContent.value }}<span class="cursor-blink">|</span></div>
          </div>
          <div v-else-if="ws.generating.value && !hasAgentExecutionCard" class="chat-msg assistant">
            <div class="msg-bubble streaming"><Icon icon="carbon:circle-dash" class="spin" /> {{ statusLabel }}</div>
          </div>
          <div v-if="ws.error.value" class="chat-error">
            <Icon icon="carbon:warning" /> {{ ws.error.value }}
          </div>
        </div>

        <!-- Input -->
        <div class="chat-input-area">
          <textarea
            v-model="inputText"
            class="chat-input"
            placeholder="Describe what you want to build..."
            :disabled="ws.generating.value"
            @keydown.enter.exact.prevent="handleSend"
            rows="2"
          />
          <button
            class="send-btn"
            :disabled="!inputText.trim() || ws.generating.value"
            @click="handleSend"
          >
            <Icon icon="carbon:send-alt" />
          </button>
        </div>
      </div>

      <!-- Resizer -->
      <div ref="resizerRef" class="panel-resizer" @dblclick="resetPanelSize">
        <div class="resizer-line"></div>
      </div>

      <!-- Right Panel - Code / Preview -->
      <div class="workspace-panel" :style="{ width: (100 - leftPanelWidth) + '%' }">
        <div class="workspace-tabs">
          <div
            class="workspace-tab"
            :class="{ active: activeWorkspaceTab === 'code' }"
            @click="activeWorkspaceTab = 'code'"
          >
            <Icon icon="carbon:code" />
            <span>Code</span>
          </div>
          <div
            class="workspace-tab"
            :class="{ active: activeWorkspaceTab === 'preview' }"
            @click="activeWorkspaceTab = 'preview'"
          >
            <Icon icon="carbon:view" />
            <span>Preview</span>
          </div>
        </div>

        <CodeWorkspace
          v-show="activeWorkspaceTab === 'code'"
          :files="openFiles"
          :open-tab-paths="openTabPaths"
          :active-file-path="activeFilePath"
          @select-file="selectFile"
          @close-tab="closeTab"
          @code-change="handleCodeChange"
        />

        <PreviewWorkspace
          v-show="activeWorkspaceTab === 'preview'"
          preview-url=""
          :static-preview-url="staticPreviewUrl"
          :html-content="previewHtmlContent"
          status="idle"
          error=""
          @share="handleShare"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import LanguageSwitcher from '@/components/language-switcher/LanguageSwitcher.vue'
import { useCodeAgentWebSocket } from '@/composables/useCodeAgentWebSocket'
import { Icon } from '@iconify/vue'
import { nextTick, onMounted, onUnmounted, ref, watch, computed } from 'vue'
import CodeWorkspace from './components/CodeWorkspace.vue'
import type { OpenFile } from './components/CodeWorkspace.vue'
import PreviewWorkspace from './components/PreviewWorkspace.vue'
import AgentExecutionCard from './components/AgentExecutionCard.vue'

defineOptions({ name: 'CodeAgentIndex' })

const ws = useCodeAgentWebSocket()

// 根据 ws.status 映射显示文本，用于 generating 时的阶段提示
const statusLabelMap: Record<string, string> = {
  'generating': 'Preparing...',
  'initializing_agent': 'Initializing Agent...',
}
const statusLabel = computed(() => statusLabelMap[ws.status.value] || 'Generating...')

// 当前消息列表中是否已有 agent-execution 卡片（plan_started 已到达）
const hasAgentExecutionCard = computed(() =>
  ws.messages.value.some(m => m.type === 'agent-execution')
)

// --- State ---
const inputText = ref('')
const messagesRef = ref<HTMLElement>()
const activeWorkspaceTab = ref<'code' | 'preview'>('code')
const activeFilePath = ref('')

// Panel resize
const resizerRef = ref<HTMLElement>()
const leftPanelWidth = ref(40)
const isResizing = ref(false)
const startX = ref(0)
const startWidth = ref(0)

// File management
const openFiles = ref<OpenFile[]>([])
const openTabPaths = ref<string[]>([])

// Static HTML preview
const staticPreviewUrl = ref('')
const previewHtmlContent = ref('')
let staticPreviewTimer: ReturnType<typeof setTimeout> | null = null

// --- History sessions ---
interface HistorySession {
  conversationId: string
  title: string
  userRequest: string
  startTime: string
  completed: boolean
  roundCount: number
}

const showHistory = ref(false)
const historyLoading = ref(false)
const historySessions = ref<HistorySession[]>([])
const restoredPlanRecord = ref<any>(null)
const restoredPlanCompleted = ref(false)

async function toggleHistory() {
  showHistory.value = !showHistory.value
  if (showHistory.value) {
    await loadHistory()
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const resp = await fetch('/api/code-agent/sessions')
    historySessions.value = await resp.json()
  } catch (e) {
    console.error('[CodeAgent] Failed to load history:', e)
    historySessions.value = []
  } finally {
    historyLoading.value = false
  }
}

async function restoreSession(conversationId: string) {
  showHistory.value = false

  // 清理当前状态
  ws.reset()
  openFiles.value = []
  openTabPaths.value = []
  activeFilePath.value = ''
  previewTriggered = false
  revokeStaticPreview()
  restoredPlanRecord.value = null
  restoredPlanCompleted.value = false

  try {
    const resp = await fetch(`/api/code-agent/sessions/${conversationId}`)
    if (!resp.ok) return
    const data = await resp.json()

    // 恢复多轮对话
    const rounds = data.rounds || []
    for (const round of rounds) {
      // 恢复用户消息
      if (round.userRequest) {
        ws.messages.value.push({
          role: 'user',
          type: 'text',
          content: round.userRequest,
          timestamp: Date.now(),
        })
      }
      // 恢复 agent-execution 卡片（每轮独立的 planRecord）
      ws.messages.value.push({
        role: 'assistant',
        type: 'agent-execution',
        content: '',
        timestamp: Date.now(),
        planRecord: round.planRecord || null,
        completed: round.completed ?? true,
      })
    }

    // 兼容：如果后端只返回最后一轮的 planRecord 也设置 restoredPlanRecord
    if (rounds.length > 0) {
      const lastRound = rounds[rounds.length - 1]
      restoredPlanRecord.value = lastRound.planRecord || null
      restoredPlanCompleted.value = lastRound.completed ?? true
    }

    // 恢复文件（后端已合并所有轮次的文件）
    const files = data.files as Array<{ path: string; content: string }>
    if (files && files.length > 0) {
      for (const f of files) {
        openFiles.value.push({ path: f.path, content: f.content })
        openTabPaths.value.push(f.path)
      }
      activeFilePath.value = openFiles.value[0].path
      activeWorkspaceTab.value = 'code'

      // 触发预览
      previewTriggered = false
      tryStartPreview()
    }
  } catch (e) {
    console.error('[CodeAgent] Failed to restore session:', e)
  }
}

function formatTime(timeStr: string): string {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// --- Watch WebSocket files → sync to editor ---
watch(
  () => ws.files.value,
  (files) => {
    console.log('[CodeAgent] files watcher triggered, ws.files count:', files.length, files.map(f => f.path))
    for (const file of files) {
      const existing = openFiles.value.find(f => f.path === file.path)
      if (existing) {
        existing.content = file.content
      } else {
        openFiles.value.push({ path: file.path, content: file.content })
        // Auto-open new files as tabs
        if (!openTabPaths.value.includes(file.path)) {
          openTabPaths.value.push(file.path)
        }
        console.log('[CodeAgent] Added to openFiles:', file.path)
      }
    }
    if (!activeFilePath.value && openFiles.value.length > 0) {
      activeFilePath.value = openFiles.value[0].path
    }
    if (files.length > 0) {
      activeWorkspaceTab.value = 'code'
    }
    console.log('[CodeAgent] openFiles now:', openFiles.value.length, openFiles.value.map(f => f.path))
  },
  { deep: true }
)

// --- Project detection and auto-scaffolding ---
const NODE_EXTENSIONS = ['.tsx', '.jsx', '.vue', '.svelte']

function detectProjectType(): 'node' | 'html' | 'none' {
  const hasPackageJson = openFiles.value.some(f => f.path === 'package.json')
  const hasNodeFiles = openFiles.value.some(f =>
    NODE_EXTENSIONS.some(ext => f.path.endsWith(ext)) ||
    f.path === 'vite.config.ts' || f.path === 'vite.config.js' ||
    f.path === 'tsconfig.json'
  )
  if (hasPackageJson || hasNodeFiles) return 'node'

  const hasHtml = openFiles.value.some(f => f.path.endsWith('.html'))
  if (hasHtml) return 'html'

  return 'none'
}

/** Core logic: detect project type and start preview */
let previewTriggered = false
function tryStartPreview() {
  if (previewTriggered || openFiles.value.length === 0) return

  const projectType = detectProjectType()
  console.log('[CodeAgent] tryStartPreview: projectType=', projectType,
    'files=', openFiles.value.map(f => f.path))

  if (projectType === 'node') {
    previewTriggered = true
    // 使用 CDN 静态预览（避免 npm install 在浏览器沙箱中下载超时）
    createCdnPreview()
    activeWorkspaceTab.value = 'preview'
  } else if (projectType === 'html') {
    previewTriggered = true
    createStaticPreview()
    activeWorkspaceTab.value = 'preview'
  }
}

// Trigger preview when generation completes
watch(
  () => ws.generating.value,
  (generating, wasGenerating) => {
    console.log('[CodeAgent] generating watcher:', { generating, wasGenerating, openFilesCount: openFiles.value.length })
    if (generating && !wasGenerating) {
      // 新一轮生成开始，重置标记以允许生成完成后重建预览
      previewTriggered = false
    }
    if (wasGenerating && !generating) {
      tryStartPreview()
    }
  }
)

// Backup trigger: also try when plan completes (in case 'done' message is delayed or missing)
watch(
  () => ws.planCompleted.value,
  (completed) => {
    if (completed && !previewTriggered) {
      console.log('[CodeAgent] planCompleted watcher: trying to start preview')
      tryStartPreview()
    }
  }
)

// Auto-scroll chat
watch(
  [() => ws.messages.value.length, () => ws.streamingContent.value],
  () => {
    nextTick(() => {
      if (messagesRef.value) {
        messagesRef.value.scrollTop = messagesRef.value.scrollHeight
      }
    })
  }
)

// --- Chat ---
const handleSend = () => {
  const text = inputText.value.trim()
  if (!text || ws.generating.value) return
  ws.send(text)
  inputText.value = ''
}

const newChat = async () => {
  ws.reset()
  openFiles.value = []
  openTabPaths.value = []
  activeFilePath.value = ''
  previewTriggered = false
  revokeStaticPreview()
  restoredPlanRecord.value = null
  restoredPlanCompleted.value = false
}

// --- File actions ---
const selectFile = (path: string) => {
  // Open tab if not already open
  if (!openTabPaths.value.includes(path)) {
    openTabPaths.value.push(path)
  }
  activeFilePath.value = path
}

const closeTab = (path: string) => {
  const idx = openTabPaths.value.indexOf(path)
  if (idx === -1) return
  openTabPaths.value.splice(idx, 1)
  // Switch active file if the closed tab was active
  if (activeFilePath.value === path) {
    activeFilePath.value = openTabPaths.value[Math.min(idx, openTabPaths.value.length - 1)] || ''
  }
}

const handleCodeChange = (value: string) => {
  const file = openFiles.value.find(f => f.path === activeFilePath.value)
  if (file) {
    file.content = value
    // Refresh preview if active (debounced)
    if (staticPreviewUrl.value) {
      if (staticPreviewTimer) clearTimeout(staticPreviewTimer)
      staticPreviewTimer = setTimeout(() => {
        const projectType = detectProjectType()
        if (projectType === 'node') {
          createCdnPreview()
        } else {
          createStaticPreview()
        }
      }, 800)
    }
  }
}

// --- CDN-Based Preview for React/Vue Projects ---
// 使用 CDN 加载框架 + Babel 浏览器端编译 JSX，无需 npm install

/**
 * 从 lucide-react 等图标库 import 中提取图标名，生成 SVG 占位组件声明。
 * 必须在 stripImportsAndExports 之前调用，否则 import 行已被删除。
 */
function extractIconStubs(code: string): string {
  const iconNames: string[] = []
  // 匹配 import { Icon1, Icon2 } from 'lucide-react' （支持多行）
  const re = /import\s*\{([^}]+)\}\s*from\s*['"]lucide-react['"]/g
  let m: RegExpExecArray | null
  while ((m = re.exec(code)) !== null) {
    m[1].split(',').forEach(s => {
      const name = s.trim()
      if (name && /^[A-Z]/.test(name)) iconNames.push(name)
    })
  }
  if (iconNames.length === 0) return ''
  // 生成通用 SVG 占位组件：24x24 圆 + 首字母
  return iconNames.map(name =>
    `const ${name} = (props) => React.createElement('svg', ` +
    `{ width: props.size||24, height: props.size||24, viewBox:'0 0 24 24', ` +
    `fill:'none', stroke:'currentColor', strokeWidth:2, className:props.className, style:props.style }, ` +
    `React.createElement('circle', {cx:12,cy:12,r:10}), ` +
    `React.createElement('text', {x:12,y:16,textAnchor:'middle',fontSize:10,fill:'currentColor',stroke:'none'}, '${name.charAt(0)}'));`
  ).join('\n') + '\n'
}

/** 从源码中移除 ES module import/export 语句，返回清理后的代码 */
function stripImportsAndExports(code: string): string {
  return code
    // 移除 import ... from '...' 语句（单行和多行）
    .replace(/^\s*import\s+[\s\S]*?\s+from\s+['"][^'"]+['"]\s*;?\s*$/gm, '')
    // 移除 import '...' 副作用导入
    .replace(/^\s*import\s+['"][^'"]+['"]\s*;?\s*$/gm, '')
    // 移除 export default
    .replace(/^\s*export\s+default\s+/gm, '')
    // 移除 export { ... }
    .replace(/^\s*export\s*\{[^}]*\}\s*;?\s*$/gm, '')
    // 移除 export const/function/class 前缀，保留声明
    .replace(/^\s*export\s+(const|let|var|function|class)\s+/gm, '$1 ')
}

/** 查找组件文件中默认导出的组件名 */
function findDefaultComponentName(code: string): string | null {
  // export default function Xxx / export default class Xxx
  let m = code.match(/export\s+default\s+(?:function|class)\s+(\w+)/)
  if (m) return m[1]
  // export default Xxx
  m = code.match(/export\s+default\s+(\w+)\s*;?\s*$/)
  if (m) return m[1]
  // const Xxx = ... ; export default Xxx  — 找最后一个 export default
  m = code.match(/export\s+default\s+(\w+)/)
  if (m) return m[1]
  // 找 const App = () => / function App(
  m = code.match(/(?:const|function)\s+(App|Main|Root|Page|Home)\b/)
  if (m) return m[1]
  return null
}

function createCdnPreview() {
  const hasReact = openFiles.value.some(f =>
    f.path.endsWith('.tsx') || f.path.endsWith('.jsx') ||
    f.content.includes("from 'react'") || f.content.includes('from "react"')
  )
  const hasVue = openFiles.value.some(f => f.path.endsWith('.vue'))
  const hasTailwind = openFiles.value.some(f =>
    f.path === 'tailwind.config.js' || f.path === 'tailwind.config.ts' ||
    f.content.includes('@tailwind') || f.content.includes('tailwindcss')
  )

  if (hasReact) {
    createReactCdnPreview(hasTailwind)
  } else if (hasVue) {
    createVueCdnPreview(hasTailwind)
  } else {
    // 回退到 HTML 静态预览
    createStaticPreview()
  }
}

function createReactCdnPreview(hasTailwind: boolean) {
  // 收集所有 CSS 内容
  const cssContents = openFiles.value
    .filter(f => f.path.endsWith('.css'))
    .map(f => f.content)
    .join('\n')

  // 找到 App 组件（通常是 src/App.tsx 或 App.tsx）
  const appFile = openFiles.value.find(f =>
    f.path.endsWith('/App.tsx') || f.path.endsWith('/App.jsx') ||
    f.path === 'App.tsx' || f.path === 'App.jsx'
  )

  if (!appFile) {
    console.warn('[CodeAgent] No App component found for React preview')
    createStaticPreview()
    return
  }

  // 收集所有组件文件（除了 main/index 入口）
  const componentFiles = openFiles.value.filter(f =>
    (f.path.endsWith('.tsx') || f.path.endsWith('.jsx')) &&
    !f.path.includes('main.') && !f.path.match(/src\/index\.[tj]sx?$/)
  )

  // React UMD 全局变量解构 —— 让代码中的 useState/useEffect 等直接可用
  const reactPreamble = `
const { useState, useEffect, useRef, useMemo, useCallback, useContext,
  useReducer, useLayoutEffect, useId, Fragment, createElement,
  createContext, forwardRef, memo, lazy, Suspense, StrictMode } = React;
const { createRoot, hydrateRoot } = ReactDOM;
`

  // 构建组件代码：先放非 App 组件，再放 App
  const nonAppComponents = componentFiles.filter(f => f.path !== appFile.path)
  let combinedCode = reactPreamble + '\n'

  // 提取所有图标库引用，生成占位组件（必须在 stripImportsAndExports 之前）
  for (const comp of componentFiles) {
    combinedCode += extractIconStubs(comp.content)
  }

  for (const comp of nonAppComponents) {
    combinedCode += '// --- ' + comp.path + ' ---\n'
    combinedCode += stripImportsAndExports(comp.content) + '\n\n'
  }

  // App 组件
  const appComponentName = findDefaultComponentName(appFile.content) || 'App'
  combinedCode += '// --- ' + appFile.path + ' ---\n'
  combinedCode += stripImportsAndExports(appFile.content) + '\n\n'

  // 渲染入口
  combinedCode += `
const rootEl = document.getElementById('root');
createRoot(rootEl).render(React.createElement(${appComponentName}));
`

  // 过滤掉 Tailwind 的 @tailwind/@apply 等指令（CDN Play 版不需要）
  const cleanCss = cssContents
    .replace(/@tailwind\s+\w+\s*;?\s*/g, '')
    .replace(/@layer\s+\w+\s*\{[^}]*\}/g, '')

  // 使用 jsDelivr CDN（国内有节点）
  const cdn = 'https://cdn.jsdelivr.net/npm'
  const tailwindScript = hasTailwind
    ? SC_OPEN + ' src="https://cdn.tailwindcss.com">' + SC_CLOSE + '\n' : ''

  // 错误捕获脚本 —— 在预览区域显示运行时错误
  const errorHandler = SC_OPEN + `>
window.onerror = function(msg, src, line, col, err) {
  var el = document.getElementById('__err');
  if (!el) { el = document.createElement('pre'); el.id = '__err';
    el.style.cssText = 'position:fixed;top:0;left:0;right:0;padding:16px;background:#fee;color:#c00;font-size:13px;z-index:99999;white-space:pre-wrap;max-height:40vh;overflow:auto;border-bottom:2px solid #c00';
    document.body.prepend(el); }
  el.textContent += msg + '\\n  at line ' + line + ':' + col + '\\n';
  return false;
};
` + SC_CLOSE

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>Preview</title>
${errorHandler}
${SC_OPEN} crossorigin src="${cdn}/react@18/umd/react.development.js">${SC_CLOSE}
${SC_OPEN} crossorigin src="${cdn}/react-dom@18/umd/react-dom.development.js">${SC_CLOSE}
${SC_OPEN} src="${cdn}/@babel/standalone@7/babel.min.js">${SC_CLOSE}
${tailwindScript}${ST_OPEN}
* { margin: 0; padding: 0; box-sizing: border-box; }
${cleanCss}
${ST_CLOSE}
</head>
<body>
<div id="root"></div>
${SC_OPEN} type="text/plain" id="__src">
${combinedCode.replace(/<\/script>/gi, '<\\/script>')}
${SC_CLOSE}
${SC_OPEN}>
(function() {
  var srcEl = document.getElementById('__src');
  if (!srcEl) return;
  var code = srcEl.textContent;
  try {
    var result = Babel.transform(code, {
      presets: ['react', ['typescript', { allExtensions: true, isTSX: true }]],
      filename: 'app.tsx'
    });
    var fn = new Function(result.code);
    fn();
  } catch(e) {
    var el = document.getElementById('__err');
    if (!el) {
      el = document.createElement('pre'); el.id = '__err';
      el.style.cssText = 'position:fixed;top:0;left:0;right:0;padding:16px;background:#fee;color:#c00;font-size:13px;z-index:99999;white-space:pre-wrap;max-height:40vh;overflow:auto;border-bottom:2px solid #c00';
      document.body.prepend(el);
    }
    el.textContent = 'Babel Compile Error:\\n' + e.message;
    console.error('[CDN Preview] Babel compile error:', e);
  }
})();
${SC_CLOSE}
</body>
</html>`

  revokeStaticPreview()
  previewHtmlContent.value = html
  const blob = new Blob([html], { type: 'text/html' })
  staticPreviewUrl.value = URL.createObjectURL(blob)
  console.log('[CodeAgent] React CDN preview created, combinedCode length:', combinedCode.length)
}

function createVueCdnPreview(hasTailwind: boolean) {
  // Vue CDN 预览：使用 Vue 3 全局构建
  const cssContents = openFiles.value
    .filter(f => f.path.endsWith('.css'))
    .map(f => f.content)
    .join('\n')

  // 找到 App.vue 的模板和脚本
  const appFile = openFiles.value.find(f =>
    f.path.endsWith('/App.vue') || f.path === 'App.vue'
  )

  // 如果找不到 .vue 文件，回退到静态预览
  if (!appFile) {
    createStaticPreview()
    return
  }

  // 简单提取 .vue 文件中的 template 和 script
  const templateMatch = appFile.content.match(/<template>([\s\S]*?)<\/template>/)
  const scriptMatch = appFile.content.match(/<script[^>]*>([\s\S]*?)<\/script>/)
  const template = templateMatch ? templateMatch[1].trim() : '<div>App</div>'
  const script = scriptMatch ? stripImportsAndExports(scriptMatch[1]) : ''

  const cleanCss = cssContents.replace(/@tailwind\s+\w+\s*;?\s*/g, '')
  const cdn = 'https://cdn.jsdelivr.net/npm'
  const tailwindScript = hasTailwind
    ? SC_OPEN + ' src="https://cdn.tailwindcss.com">' + SC_CLOSE + '\n' : ''

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>Preview</title>
${SC_OPEN} src="${cdn}/vue@3/dist/vue.global.js">${SC_CLOSE}
${tailwindScript}${ST_OPEN}
* { margin: 0; padding: 0; box-sizing: border-box; }
${cleanCss}
${ST_CLOSE}
</head>
<body>
<div id="app">${template}</div>
${SC_OPEN}>
const { createApp, ref, reactive, computed, onMounted, watch } = Vue;
${script}
createApp({}).mount('#app');
${SC_CLOSE}
</body>
</html>`

  revokeStaticPreview()
  previewHtmlContent.value = html
  const blob = new Blob([html], { type: 'text/html' })
  staticPreviewUrl.value = URL.createObjectURL(blob)
  console.log('[CodeAgent] Vue CDN preview created')
}

// --- Static HTML Preview ---
// Build tag strings dynamically to avoid confusing the Vue SFC compiler
const ST_OPEN = '<' + 'style>'
const ST_CLOSE = '</' + 'style>'
const SC_OPEN = '<' + 'script'
const SC_CLOSE = '</' + 'script>'

function createStaticPreview() {
  const htmlFile = openFiles.value.find(f => f.path.endsWith('index.html'))
    || openFiles.value.find(f => f.path.endsWith('.html'))
  if (!htmlFile) return

  let html = htmlFile.content

  // Inline local CSS link tags with style blocks
  html = html.replace(
    /<link\s+[^>]*href=["']([^"']+\.css)["'][^>]*\/?>/gi,
    (_match, href: string) => {
      if (href.startsWith('http://') || href.startsWith('https://') || href.startsWith('//')) {
        return _match
      }
      const cssFile = openFiles.value.find(f => f.path === href || f.path.endsWith('/' + href))
      if (cssFile) {
        return ST_OPEN + '\n' + cssFile.content + '\n' + ST_CLOSE
      }
      return _match
    }
  )

  // Inline local JS src tags with inline blocks
  const jsPattern = new RegExp(SC_OPEN + '\\s+[^>]*src=["\']([^"\']+\\.js)["\'][^>]*>' + SC_CLOSE, 'gi')
  html = html.replace(
    jsPattern,
    (_match, src: string) => {
      if (src.startsWith('http://') || src.startsWith('https://') || src.startsWith('//')) {
        return _match
      }
      const jsFile = openFiles.value.find(f => f.path === src || f.path.endsWith('/' + src))
      if (jsFile) {
        return SC_OPEN + '>\n' + jsFile.content + '\n' + SC_CLOSE
      }
      return _match
    }
  )

  // Revoke old blob URL
  revokeStaticPreview()

  previewHtmlContent.value = html
  const blob = new Blob([html], { type: 'text/html' })
  staticPreviewUrl.value = URL.createObjectURL(blob)
}

function revokeStaticPreview() {
  if (staticPreviewUrl.value) {
    URL.revokeObjectURL(staticPreviewUrl.value)
    staticPreviewUrl.value = ''
  }
  previewHtmlContent.value = ''
}

async function handleShare(html: string) {
  const resp = await fetch('/api/code-agent/share', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ html })
  })
  const data = await resp.json()
  const shareUrl = window.location.origin + data.url
  await navigator.clipboard.writeText(shareUrl)
  window.open(shareUrl, '_blank')
}

// --- Panel resize logic ---
const handleMouseDown = (e: MouseEvent) => {
  isResizing.value = true
  startX.value = e.clientX
  startWidth.value = leftPanelWidth.value
  document.addEventListener('mousemove', handleMouseMove, { passive: false })
  document.addEventListener('mouseup', handleMouseUp)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  e.preventDefault()
}

const handleMouseMove = (e: MouseEvent) => {
  if (!isResizing.value) return
  const delta = ((e.clientX - startX.value) / window.innerWidth) * 100
  leftPanelWidth.value = Math.max(20, Math.min(70, startWidth.value + delta))
  e.preventDefault()
}

const handleMouseUp = () => {
  isResizing.value = false
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  localStorage.setItem('codeAgentPanelWidth', leftPanelWidth.value.toString())
}

const resetPanelSize = () => {
  leftPanelWidth.value = 40
  localStorage.setItem('codeAgentPanelWidth', '40')
}

onMounted(() => {
  const saved = localStorage.getItem('codeAgentPanelWidth')
  if (saved) leftPanelWidth.value = parseFloat(saved)

  ws.connect()

  nextTick(() => {
    resizerRef.value?.addEventListener('mousedown', handleMouseDown)
  })

  // 点击外部关闭历史面板
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  resizerRef.value?.removeEventListener('mousedown', handleMouseDown)
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
  document.removeEventListener('click', handleClickOutside)
  if (staticPreviewTimer) clearTimeout(staticPreviewTimer)
  revokeStaticPreview()
  ws.disconnect()
})

function handleClickOutside(e: MouseEvent) {
  if (!showHistory.value) return
  const wrapper = document.querySelector('.history-btn-wrapper')
  if (wrapper && !wrapper.contains(e.target as Node)) {
    showHistory.value = false
  }
}
</script>

<style lang="less" scoped>
.code-agent-page {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-primary);
}

// --- Header ---
.code-agent-header {
  padding: 4px 12px;
  border-bottom: 1px solid #1a1a1a;
  flex-shrink: 0;
  background: rgba(var(--bg-primary-rgb), 0.02);
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-logo {
  display: flex;
  align-items: center;
  gap: 10px;

  h1 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    background: linear-gradient(135deg, var(--accent-primary, #667eea) 0%, #09df75 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--border-default);
  border-radius: 6px;
  background: var(--surface-subtle);
  color: var(--text-primary, #fff);
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: var(--border-default);
    border-color: var(--border-primary);
  }
}

// --- Main Layout ---
.code-agent-main {
  flex: 1;
  display: flex;
  min-height: 0;
}

// --- Chat Panel (Left) ---
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.chat-panel-header {
  padding: 12px 10px;
  border-bottom: 1px solid var(--bg-secondary, #1a1a1a);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  background: rgba(var(--bg-primary-rgb), 0.02);

  h2 {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: var(--text-primary, #fff);
  }
}

.panel-header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.history-btn-wrapper {
  position: relative;
  z-index: 1000;
}

.history-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 6px;
  width: 300px;
  max-height: 400px;
  background: var(--surface-default);
  border: 1px solid var(--border-default);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  z-index: 1001;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.history-dropdown-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-default);
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary, #fff);
}

.history-close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  border-radius: 3px;

  &:hover { background: var(--surface-subtle); color: var(--text-primary); }
}

.history-loading, .history-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 20px;
  color: var(--text-muted);
  font-size: 12px;
}

.history-list {
  overflow-y: auto;
  max-height: 340px;

  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { background: var(--border-primary); border-radius: 2px; }
}

.history-item {
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  transition: background 0.15s;

  &:hover { background: var(--surface-subtle); }
  &:last-child { border-bottom: none; }
}

.history-item-text {
  font-size: 12px;
  color: var(--text-primary, #fff);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.history-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 3px;
  font-size: 10px;
  color: var(--text-muted);
}

.history-status {
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 9px;
  font-weight: 500;

  &.done {
    background: rgba(9, 223, 117, 0.12);
    color: #09df75;
  }
  &.running {
    background: var(--accent-surface-3);
    color: var(--accent-primary, #667eea);
  }
}

.history-rounds {
  font-size: 9px;
  color: var(--text-muted);
  opacity: 0.7;
}

.connection-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--scrollbar-thumb-hover);
  flex-shrink: 0;

  &.connected { background: #09df75; }
}

// --- Chat Messages ---
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;

  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-thumb { background: var(--border-primary); border-radius: 3px; }
}

.chat-msg {
  display: flex;

  &.user { justify-content: flex-end; }
  &.assistant { justify-content: flex-start; }

  .msg-bubble {
    max-width: 85%;
    padding: 10px 14px;
    border-radius: 14px;
    font-size: 14px;
    line-height: 1.5;
    word-break: break-word;
    white-space: pre-wrap;
  }

  &.user .msg-bubble {
    background: linear-gradient(135deg, var(--accent-primary, #667eea) 0%, #09df75 100%);
    color: #fff;
    border-bottom-right-radius: 4px;
  }

  &.assistant .msg-bubble {
    background: var(--surface-default);
    color: var(--text-primary, #fff);
    border-bottom-left-radius: 4px;

    &.streaming { opacity: 0.9; }
  }
}

.cursor-blink {
  animation: blink 1s step-end infinite;
}
@keyframes blink {
  50% { opacity: 0; }
}

.spin {
  animation: spin 1s linear infinite;
  display: inline-block;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

.chat-error {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--error-surface);
  border: 1px solid var(--error-border);
  border-radius: 8px;
  color: var(--error-text);
  font-size: 13px;
}

// --- Chat Input ---
.chat-input-area {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid var(--surface-default);
  background: rgba(var(--bg-primary-rgb), 0.02);
  flex-shrink: 0;
}

.chat-input {
  flex: 1;
  background: var(--surface-subtle);
  border: 1px solid var(--surface-strong);
  border-radius: 8px;
  padding: 8px 12px;
  color: var(--text-primary, #fff);
  font-size: 14px;
  resize: none;
  outline: none;
  font-family: inherit;

  &::placeholder { color: var(--text-placeholder); }
  &:focus { border-color: var(--accent-primary, #667eea); }
  &:disabled { opacity: 0.5; }
}

.send-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--accent-primary, #667eea) 0%, #09df75 100%);
  color: #fff;
  cursor: pointer;
  font-size: 16px;
  flex-shrink: 0;
  align-self: flex-end;
  transition: opacity 0.2s;

  &:disabled { opacity: 0.4; cursor: not-allowed; }
  &:not(:disabled):hover { opacity: 0.85; }
}

// --- Resizer ---
.panel-resizer {
  width: 6px;
  height: 100%;
  background: var(--bg-primary);
  cursor: col-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background-color 0.2s;

  &:hover .resizer-line {
    background: #4a90e2;
    width: 2px;
  }
}

.resizer-line {
  width: 1px;
  height: 40px;
  background: var(--border-default);
  border-radius: 1px;
  transition: all 0.2s;
}

// --- Workspace Panel (Right) ---
.workspace-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.workspace-tabs {
  display: flex;
  padding: 4px;
  gap: 0;
  background: var(--surface-subtle);
  border-bottom: 1px solid #1a1a1a;
  flex-shrink: 0;
}

.workspace-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 16px;
  border-radius: 6px;
  cursor: pointer;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 500;
  flex: 1;
  justify-content: center;
  transition: all 0.2s;

  &:hover { background: var(--surface-default); }

  &.active {
    background: linear-gradient(135deg, var(--accent-primary, #667eea) 0%, #09df75 100%);
    color: #fff;
    box-shadow: 0 2px 8px var(--accent-border-3);
  }

  .iconify { font-size: 16px; }
}
</style>
