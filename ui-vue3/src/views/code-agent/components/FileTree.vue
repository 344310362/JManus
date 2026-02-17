<template>
  <div class="file-tree">
    <div class="file-tree-header">
      <Icon icon="carbon:folder" class="header-icon" />
      <span>Files</span>
    </div>
    <div class="file-tree-content">
      <template v-if="treeNodes.length > 0">
        <FileTreeItem
          v-for="node in treeNodes"
          :key="node.name"
          :node="node"
          :depth="0"
          :active-file-path="activeFilePath"
          @select="$emit('select', $event)"
        />
      </template>
      <div v-else class="tree-empty">
        <span>No files yet</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { computed } from 'vue'
import FileTreeItem from './FileTreeItem.vue'
import type { TreeNode } from './FileTreeItem.vue'

const props = defineProps<{
  files: { path: string }[]
  activeFilePath: string
}>()

defineEmits<{
  select: [path: string]
}>()

const treeNodes = computed(() => {
  return buildTree(props.files.map(f => f.path))
})

function buildTree(paths: string[]): TreeNode[] {
  const root: TreeNode = { name: '', children: [] }

  for (const filePath of paths) {
    const parts = filePath.split('/')
    let current = root

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]
      const isFile = i === parts.length - 1

      if (isFile) {
        current.children!.push({ name: part, path: filePath })
      } else {
        let dir = current.children!.find(c => c.children && c.name === part)
        if (!dir) {
          dir = { name: part, children: [] }
          current.children!.push(dir)
        }
        current = dir
      }
    }
  }

  // Sort: directories first (alphabetical), then files (alphabetical)
  sortTree(root)
  return root.children!
}

function sortTree(node: TreeNode) {
  if (!node.children) return
  node.children.sort((a, b) => {
    const aIsDir = !!a.children
    const bIsDir = !!b.children
    if (aIsDir !== bIsDir) return aIsDir ? -1 : 1
    return a.name.localeCompare(b.name)
  })
  for (const child of node.children) {
    sortTree(child)
  }
}
</script>

<style lang="less" scoped>
.file-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--surface-hover);
  border-right: 1px solid var(--surface-default);
}

.file-tree-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid var(--surface-default);
  flex-shrink: 0;

  .header-icon { font-size: 14px; }
}

.file-tree-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 4px 0;

  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { background: var(--border-primary); border-radius: 2px; }
}

.tree-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
