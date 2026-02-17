<template>
  <div class="code-workspace">
    <!-- File Tree Sidebar -->
    <div class="file-tree-sidebar">
      <FileTree
        :files="files"
        :active-file-path="activeFilePath"
        @select="$emit('select-file', $event)"
      />
    </div>

    <!-- Editor Panel -->
    <div class="editor-panel">
      <!-- File Tabs (only open tabs) -->
      <div class="file-tabs">
        <div class="file-tabs-list">
          <div
            v-for="file in tabFiles"
            :key="file.path"
            class="file-tab"
            :class="{ active: activeFilePath === file.path }"
            @click="$emit('select-file', file.path)"
          >
            <Icon :icon="getFileIcon(file.path)" class="file-icon" />
            <span class="file-name">{{ getFileName(file.path) }}</span>
            <button class="file-tab-close" @click.stop="$emit('close-tab', file.path)">
              <Icon icon="carbon:close" />
            </button>
          </div>
          <div v-if="tabFiles.length === 0" class="file-tab placeholder">
            <Icon icon="carbon:document" class="file-icon" />
            <span class="file-name">No files open</span>
          </div>
        </div>
        <button
          v-if="files.length > 0"
          class="download-btn"
          :disabled="downloading"
          title="Download Project"
          @click="downloadProject"
        >
          <Icon :icon="downloading ? 'carbon:circle-dash' : 'carbon:download'" :class="{ spin: downloading }" width="16" />
        </button>
      </div>

      <!-- Editor -->
      <div class="editor-area">
        <MonacoEditor
          v-if="activeFile"
          :model-value="activeFile.content"
          :language="getLanguage(activeFilePath)"
          @change="(val: string) => $emit('code-change', val)"
        />
        <div v-else class="editor-empty">
          <Icon icon="carbon:document-add" class="empty-icon" />
          <p>Start a conversation to generate code</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import MonacoEditor from '@/components/MonacoEditor.vue'
import { Icon } from '@iconify/vue'
import JSZip from 'jszip'
import { computed, ref } from 'vue'
import FileTree from './FileTree.vue'
import { getFileIcon, getFileName, getLanguage } from '../utils/fileIcons'

export interface OpenFile {
  path: string
  content: string
}

const props = defineProps<{
  files: OpenFile[]
  openTabPaths: string[]
  activeFilePath: string
}>()

defineEmits<{
  'select-file': [path: string]
  'close-tab': [path: string]
  'code-change': [value: string]
}>()

// Tabs: only show files whose paths are in openTabPaths
const tabFiles = computed(() =>
  props.openTabPaths
    .map(p => props.files.find(f => f.path === p))
    .filter((f): f is OpenFile => f != null)
)

const activeFile = computed(() =>
  props.files.find(f => f.path === props.activeFilePath) ?? null
)

// 一键下载工程代码
const downloading = ref(false)

async function downloadProject() {
  if (downloading.value || props.files.length === 0) return
  downloading.value = true
  try {
    const zip = new JSZip()
    for (const file of props.files) {
      zip.file(file.path, file.content)
    }
    const blob = await zip.generateAsync({ type: 'blob' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'project.zip'
    a.click()
    URL.revokeObjectURL(url)
  } finally {
    downloading.value = false
  }
}
</script>

<style lang="less" scoped>
.code-workspace {
  flex: 1;
  display: flex;
  flex-direction: row;
  min-height: 0;
  overflow: hidden;
}

.file-tree-sidebar {
  width: 200px;
  flex-shrink: 0;
  min-height: 0;
  overflow: hidden;
}

.editor-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.file-tabs {
  display: flex;
  align-items: center;
  background: var(--surface-hover);
  border-bottom: 1px solid var(--surface-default);
  flex-shrink: 0;
}

.file-tabs-list {
  display: flex;
  gap: 0;
  flex: 1;
  overflow-x: auto;
  min-width: 0;

  &::-webkit-scrollbar { height: 2px; }
  &::-webkit-scrollbar-thumb { background: var(--border-primary); }
}

.download-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  flex-shrink: 0;
  margin: 0 4px;
  border-radius: 4px;
  transition: all 0.15s;

  &:hover {
    background: var(--surface-subtle);
    color: var(--text-primary);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

.file-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--text-tertiary);
  cursor: pointer;
  border-right: 1px solid var(--surface-subtle);
  white-space: nowrap;
  transition: all 0.15s;

  &:hover {
    background: var(--surface-subtle);
    color: var(--text-primary);
  }

  &.active {
    background: var(--surface-default);
    color: #fff;
    border-bottom: 2px solid var(--accent-primary, #667eea);
  }

  &.placeholder {
    cursor: default;
    opacity: 0.4;
  }

  .file-icon { font-size: 14px; }
}

.file-tab-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  border-radius: 3px;
  cursor: pointer;
  font-size: 10px;
  margin-left: 4px;

  &:hover {
    background: var(--surface-strong);
    color: #fff;
  }
}

.editor-area {
  flex: 1;
  min-height: 0;

  :deep(.monaco-editor-container) { height: 100%; }
  :deep(.editor-wrapper) { height: 100%; min-height: 0; }
}

.editor-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  gap: 12px;

  .empty-icon { font-size: 48px; opacity: 0.5; }
  p { margin: 0; font-size: 14px; }
}

.spin {
  animation: spin 1s linear infinite;
  display: inline-block;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
