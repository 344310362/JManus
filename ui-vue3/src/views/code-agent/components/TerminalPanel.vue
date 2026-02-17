<template>
  <div class="terminal-panel">
    <div class="terminal-header">
      <span class="terminal-title">
        <Icon icon="carbon:terminal" />
        Terminal
      </span>
      <button class="terminal-btn" @click="$emit('clear')" title="Clear">
        <Icon icon="carbon:trash-can" width="16" />
      </button>
    </div>
    <div class="terminal-body" ref="bodyRef">
      <div v-if="logs.length === 0" class="terminal-empty">
        Waiting for sandbox output...
      </div>
      <div v-for="(line, i) in logs" :key="i" class="terminal-line">{{ line }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { ref, watch, nextTick } from 'vue'

const props = defineProps<{ logs: string[] }>()
defineEmits<{ clear: [] }>()

const bodyRef = ref<HTMLElement>()

watch(
  () => props.logs.length,
  () => {
    nextTick(() => {
      if (bodyRef.value) {
        bodyRef.value.scrollTop = bodyRef.value.scrollHeight
      }
    })
  }
)
</script>

<style lang="less" scoped>
.terminal-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: #0d0d0d;
}

.terminal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: var(--surface-hover);
  border-bottom: 1px solid var(--surface-default);
  flex-shrink: 0;
}

.terminal-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.terminal-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  border-radius: 4px;
  cursor: pointer;

  &:hover {
    background: var(--border-default);
    color: var(--text-secondary);
  }
}

.terminal-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  line-height: 1.6;

  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-thumb { background: var(--surface-strong); border-radius: 3px; }
}

.terminal-empty {
  color: var(--scrollbar-thumb-hover);
  font-style: italic;
}

.terminal-line {
  color: #d4d4d4;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
