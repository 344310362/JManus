<template>
  <div class="tree-item">
    <!-- Directory node -->
    <div
      v-if="node.children"
      class="tree-node dir-node"
      :style="{ paddingLeft: depth * 12 + 'px' }"
      @click="expanded = !expanded"
    >
      <Icon :icon="expanded ? 'carbon:chevron-down' : 'carbon:chevron-right'" class="chevron" />
      <Icon :icon="expanded ? 'vscode-icons:default-folder-opened' : 'vscode-icons:default-folder'" class="node-icon" />
      <span class="node-label">{{ node.name }}</span>
    </div>

    <!-- File node -->
    <div
      v-else
      class="tree-node file-node"
      :class="{ active: node.path === activeFilePath }"
      :style="{ paddingLeft: depth * 12 + 16 + 'px' }"
      @click="$emit('select', node.path!)"
    >
      <Icon :icon="getFileIcon(node.path!)" class="node-icon" />
      <span class="node-label">{{ node.name }}</span>
    </div>

    <!-- Children -->
    <template v-if="node.children && expanded">
      <FileTreeItem
        v-for="child in node.children"
        :key="child.name"
        :node="child"
        :depth="depth + 1"
        :active-file-path="activeFilePath"
        @select="$emit('select', $event)"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { ref } from 'vue'
import { getFileIcon } from '../utils/fileIcons'

export interface TreeNode {
  name: string
  path?: string            // full path, only for files
  children?: TreeNode[]    // present for directories
}

defineProps<{
  node: TreeNode
  depth: number
  activeFilePath: string
}>()

defineEmits<{
  select: [path: string]
}>()

const expanded = ref(true)
</script>

<style lang="less" scoped>
.tree-node {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  font-size: 12px;
  cursor: pointer;
  color: var(--text-secondary);
  white-space: nowrap;
  user-select: none;

  &:hover {
    background: var(--surface-subtle);
    color: var(--text-primary);
  }

  &.active {
    background: var(--surface-default);
    color: #fff;
  }
}

.chevron {
  font-size: 12px;
  flex-shrink: 0;
  opacity: 0.6;
}

.node-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.node-label {
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
