<template>
  <div class="roles-section">
<!--    <h2 class="section-title">{{ $t('home.rolesSectionTitle', '角色任务快捷入口') }}</h2>-->

    <div class="tabs-container">
      <!-- Tab 标签 -->
      <div class="tab-list">
        <button
          v-for="role in roles"
          :key="role.id"
          class="tab-button"
          :class="{ active: activeTab === role.id }"
          @click="activeTab = role.id"
        >
          {{ role.iconText }}
        </button>
      </div>

      <!-- Tab 内容 -->
      <div class="tab-content">
        <ul class="tasks-list">
          <li
            v-for="task in getActiveRole.tasks"
            :key="task.id"
            class="task-item"
            @click="handleTaskClick(task)"
          >
            <span class="task-icon">{{ task.icon }}</span>
            {{ task.text }}
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useTaskStore } from '@/stores/task'

const router = useRouter()
const { t } = useI18n()
const taskStore = useTaskStore()

// 角色数据（保持不变）
const roles = [
  {
    id: 'explore',
    iconText: '探索',
    tasks: [
      { id: 'aliyun-gujia', icon: '💬', text: '查看阿里巴巴今日股价',describe: '打开百度搜索阿里巴巴今日股价' },
      { id: 'article-ai', icon: '💬', text: '生成小说-AI统治地球',describe: '生成小说-AI统治地球',planId: "plan-1763633806506","planTemplateId": "new-1763632772594" },
    ]
  },
  {
    id: 'ops',
    iconText: t('home.roles.ops.icon', '运维'),
    tasks: [
      { id: 'ip-search', icon: '💬', text: '服务ip查询',describe: '先提示用户输入查询的 ip，然后调用 service_manager 的 search_ip 方法，查看 ip 对应的服务' },
    ]
  },
  {
    id: 'product',
    iconText: t('home.roles.product.icon', '产品'),
    tasks: [
      { id: 'create-requirement', icon: '📝', text: t('home.roles.product.tasks.createRequirement', '创建新产品需求文档') },
      { id: 'update-spec', icon: '🔄', text: t('home.roles.product.tasks.updateSpec', '更新现有需求规格') },
      { id: 'confirm-acceptance', icon: '✅', text: t('home.roles.product.tasks.confirmAcceptance', '确认需求验收标准') },
      { id: 'schedule-planning', icon: '📅', text: t('home.roles.product.tasks.schedulePlanning', '安排需求排期计划') },
      { id: 'collect-feedback', icon: '💬', text: t('home.roles.product.tasks.collectFeedback', '收集用户反馈意见') }
    ]
  },
  {
    id: 'designer',
    iconText: t('home.roles.designer.icon', '设计'),
    tasks: [
      { id: 'generate-logo', icon: '🎨', text: t('home.roles.designer.tasks.generateLogo', '生成品牌LOGO设计') },
      { id: 'create-social-image', icon: '🖼️', text: t('home.roles.designer.tasks.createSocialImage', '创建社交媒体配图') },
      { id: 'design-mobile-ui', icon: '📱', text: t('home.roles.designer.tasks.designMobileUI', '设计移动端界面原型') },
      { id: 'adjust-colors', icon: '🌈', text: t('home.roles.designer.tasks.adjustColors', '调整色彩搭配方案') },
      { id: 'optimize-layout', icon: '📐', text: t('home.roles.designer.tasks.optimizeLayout', '优化版式布局结构') }
    ]
  },
  {
    id: 'developer',
    iconText: t('home.roles.developer.icon', '开发'),
    tasks: [
      { id: 'generate-code', icon: '💻', text: t('home.roles.developer.tasks.generateCode', '生成基础代码框架') },
      { id: 'fix-bug', icon: '🔧', text: t('home.roles.developer.tasks.fixBug', '修复已知BUG问题') },
      { id: 'add-feature', icon: '📈', text: t('home.roles.developer.tasks.addFeature', '添加新功能模块') },
      { id: 'write-tests', icon: '🧪', text: t('home.roles.developer.tasks.writeTests', '编写单元测试用例') },
      { id: 'optimize-performance', icon: '🚀', text: t('home.roles.developer.tasks.optimizePerformance', '优化性能瓶颈') }
    ]
  }
]

// 默认激活第一个 tab
const activeTab = ref(roles[0].id)

// 计算当前激活的角色
const getActiveRole = computed(() => {
  return roles.find(role => role.id === activeTab.value) || roles[0]
})

// 方法
const handleTaskClick = (task: any) => {
  taskStore.setTask(task.describe? task.describe :task.text)
  // debugger
  // if(task.planTemplateId){
  //   taskStore.setTaskRunning(task.planId)
  // }
  const chatId = Date.now().toString()
  router.push({ name: 'direct', params: { id: chatId } })
}
</script>

<style scoped>
.roles-section {
  padding: 30px 30px;
  background: rgba(var(--selection-bg)); /* 80% 不透明 */
}

.section-title {
  font-size: 1.4rem;
  margin-bottom: 24px;
  text-align: center;
  color: #333;
  font-weight: 600;
}

.tabs-container {
  max-width: 600px;
  margin: 0 auto;
}

.tab-list {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.tab-button {
  padding: 8px 16px;
  border: 1px solid var(--border-primary, #e0e0e0);
  background: var(--bg-secondary);
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #555;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-button:hover {
  border-color: var(--accent-primary, #667eea);
  background: var(--bg-primary);
  color: var(--accent-primary, #667eea);
}

.tab-button.active {
  background: linear-gradient(135deg, var(--accent-primary, #667eea) 0%, #09df75 100%);
  color: var(--text-primary);
  border-color: transparent;
  box-shadow: 0 2px 8px var(--selection-bg, rgba(102, 126, 234, 0.3));
}

.tasks-list {
  list-style: none;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
  padding: 0;
}

.task-item {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  background: var(--bg-secondary);
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.2s ease;
  cursor: pointer;
  border: 1px solid #eee;
}

.task-item:hover {
  background: rgba(102, 126, 234, 0.08);
  transform: translateY(-2px);
  border-color: var(--accent-primary, #667eea);
}

.task-icon {
  width: 22px;
  height: 22px;
  background: var(--accent-primary, #667eea);
  border-radius: 4px;
  margin-right: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-primary);
  font-size: 12px;
  flex-shrink: 0;
}
</style>
