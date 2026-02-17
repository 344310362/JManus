<template>
  <div class="preview-workspace">
    <!-- Toolbar with URL -->
    <div v-if="effectiveUrl" class="preview-toolbar">
      <div class="preview-url-bar">
        <Icon icon="carbon:globe" />
        <span>{{ urlDisplayText }}</span>
      </div>
      <button class="toolbar-btn" @click="refresh" title="Refresh">
        <Icon icon="carbon:renew" width="18" />
      </button>
      <button
        v-if="props.htmlContent"
        class="toolbar-btn"
        @click="emit('share', props.htmlContent)"
        title="Share Preview"
      >
        <Icon icon="carbon:send-alt" width="18" />
      </button>
    </div>

    <!-- Content -->
    <div class="preview-frame-container">
      <iframe
        v-if="effectiveUrl"
        ref="iframeRef"
        :src="effectiveUrl"
        class="preview-iframe"
        sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-modals"
      />
      <div v-else-if="error" class="preview-empty">
        <Icon icon="carbon:warning" class="empty-icon error-icon" />
        <p>{{ error }}</p>
      </div>
      <div v-else class="preview-empty">
        <Icon icon="carbon:application-web" class="empty-icon" />
        <p>Preview will appear here after code generation</p>
        <span class="empty-hint">The sandbox will auto-start when code is ready</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { computed, ref } from 'vue'

const props = defineProps<{
  previewUrl: string
  staticPreviewUrl?: string
  status: string
  error: string
  htmlContent?: string
}>()

const emit = defineEmits<{
  share: [html: string]
}>()

const iframeRef = ref<HTMLIFrameElement>()

const effectiveUrl = computed(() => props.previewUrl || props.staticPreviewUrl || '')

const urlDisplayText = computed(() => {
  if (props.previewUrl) return props.previewUrl
  if (props.staticPreviewUrl) return 'CDN Preview'
  return ''
})

function refresh() {
  if (iframeRef.value && effectiveUrl.value) {
    iframeRef.value.src = effectiveUrl.value
  }
}
</script>

<style lang="less" scoped>
.preview-workspace {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.preview-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: var(--surface-hover);
  border-bottom: 1px solid var(--surface-default);
  flex-shrink: 0;
}

.toolbar-btn {
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

.preview-url-bar {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  background: var(--overlay-medium);
  border: 1px solid var(--border-default);
  border-radius: 6px;
  font-size: 12px;
  color: var(--text-tertiary);
  overflow: hidden;

  .iconify { flex-shrink: 0; font-size: 14px; }
  span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}

.preview-frame-container {
  flex: 1;
  min-height: 0;
  position: relative;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
}

.preview-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  gap: 12px;

  .empty-icon { font-size: 48px; opacity: 0.5; }
  .error-icon { color: var(--error-text); opacity: 1; }
  p { margin: 0; font-size: 14px; }
  .empty-hint { font-size: 12px; opacity: 0.6; }
}

.spin {
  animation: spin 1s linear infinite;
  display: inline-block;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
