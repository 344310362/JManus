<template>
  <div class="roles-section">
    <h2 class="section-title">{{ $t('home.rolesSectionTitle',"角色任务快捷入口") }}</h2>

    <div class="roles-grid">
      <div
        v-for="role in roles"
      :key="role.id"
      class="role-card"
      @click="handleRoleClick(role)"
      >
      <div class="role-icon">{{ role.iconText }}</div>
<!--      <h3 class="role-title">{{ role.title }}</h3>-->
<!--      <p class="role-description">{{ role.description }}</p>-->
      <ul class="tasks-list">
        <li
          v-for="task in role.tasks"
        :key="task.id"
        class="task-item"
        @click.stop="handleTaskClick(task)"
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
  import { useRouter } from 'vue-router'
  import { useI18n } from 'vue-i18n'
  import { useTaskStore } from '@/stores/task'

  const router = useRouter()
  const { t } = useI18n()
  const taskStore = useTaskStore()

  // 角色数据（可根据实际需求从 i18n 或 API 获取）
  const roles = [
  {
    id: 'ops',
    iconText: t('home.roles.ops.icon',"运维"),
    title: t('home.roles.ops.title',"运维"),
    description: t('home.roles.ops.description'),
    tasks: [
  { id: 'add-service', icon: '+1', text: t('home.roles.ops.tasks.addService') },
  { id: 'remove-service', icon: '-1', text: t('home.roles.ops.tasks.removeService') },
  { id: 'restart-service', icon: '↻', text: t('home.roles.ops.tasks.restartService') },
  { id: 'monitor-service', icon: '📊', text: t('home.roles.ops.tasks.monitorService') },
  { id: 'modify-config', icon: '⚙️', text: t('home.roles.ops.tasks.modifyConfig') }
    ]
  },
  {
    id: 'product',
    iconText: t('home.roles.product.icon',"产品"),
    title: t('home.roles.product.title',"产品"),
    description: t('home.roles.product.description',"产品"),
    tasks: [
  { id: 'create-requirement', icon: '📝', text: t('home.roles.product.tasks.createRequirement') },
  { id: 'update-spec', icon: '🔄', text: t('home.roles.product.tasks.updateSpec') },
  { id: 'confirm-acceptance', icon: '✅', text: t('home.roles.product.tasks.confirmAcceptance') },
  { id: 'schedule-planning', icon: '📅', text: t('home.roles.product.tasks.schedulePlanning') },
  { id: 'collect-feedback', icon: '💬', text: t('home.roles.product.tasks.collectFeedback') }
    ]
  },
  {
    id: 'designer',
    iconText: t('home.roles.designer.icon',"设计"),
    title: t('home.roles.designer.title',"设计"),
    description: t('home.roles.designer.description'),
    tasks: [
  { id: 'generate-logo', icon: '🎨', text: t('home.roles.designer.tasks.generateLogo') },
  { id: 'create-social-image', icon: '🖼️', text: t('home.roles.designer.tasks.createSocialImage') },
  { id: 'design-mobile-ui', icon: '📱', text: t('home.roles.designer.tasks.designMobileUI') },
  { id: 'adjust-colors', icon: '🌈', text: t('home.roles.designer.tasks.adjustColors') },
  { id: 'optimize-layout', icon: '📐', text: t('home.roles.designer.tasks.optimizeLayout') }
    ]
  },
  {
    id: 'developer',
    iconText: t('home.roles.developer.icon',"开发"),
    title: t('home.roles.developer.title',"开发"),
    description: t('home.roles.developer.description'),
    tasks: [
  { id: 'generate-code', icon: '💻', text: t('home.roles.developer.tasks.generateCode') },
  { id: 'fix-bug', icon: '🔧', text: t('home.roles.developer.tasks.fixBug') },
  { id: 'add-feature', icon: '📈', text: t('home.roles.developer.tasks.addFeature') },
  { id: 'write-tests', icon: '🧪', text: t('home.roles.developer.tasks.writeTests') },
  { id: 'optimize-performance', icon: '🚀', text: t('home.roles.developer.tasks.optimizePerformance') }
    ]
  }
  ]

  // 方法
  const goToDirectPage = () => {
  const chatId = Date.now().toString()
  router.push({ name: 'direct', params: { id: chatId } })
}

  const handleRoleClick = (role: any) => {
  // 可扩展：点击角色卡片时的逻辑（如展开/折叠任务等）
  console.log('[RoleQuickAccess] Role clicked:', role.id)
}

  const handleTaskClick = (task: any) => {
  // 设置任务内容并跳转
  taskStore.setTask(task.text)
  const chatId = Date.now().toString()
  router.push({ name: 'direct', params: { id: chatId } })
}
</script>

<style scoped>
  .roles-section {
    padding: 60px 60px;
    background: #fff;
  }

  .section-title {
    font-size: 1.5rem;
    margin-bottom: 20px;
    text-align: center;
    color: #333;
    font-weight: 600;
  }

  .roles-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 20px;
    padding: 10px;
  }

  .role-card {
    background: linear-gradient(135deg, #f8f9fa 0%, var(--bg-input, #ffffff) 100%);
    border-radius: 12px;
    padding: 20px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
    transition: all 0.3s ease;
    border: 1px solid #e9ecef;
    cursor: pointer;
  }

  .role-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
    border-color: var(--accent-primary, #667eea);
  }

  .role-icon {
    width: 50px;
    height: 50px;
    background: linear-gradient(135deg, var(--accent-primary, #667eea) 0%, #09df75 100%);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 15px;
    color: var(--text-primary);
    font-size: 18px;
    font-weight: bold;
  }

  .role-title {
    font-size: 1.2rem;
    font-weight: 700;
    margin-bottom: 10px;
    color: #333;
  }

  .role-description {
    color: #666;
    font-size: 0.9rem;
    margin-bottom: 15px;
  }

  .tasks-list {
    list-style: none;
  }

  .task-item {
    display: flex;
    align-items: center;
    padding: 8px 12px;
    background: rgba(var(--bg-primary-rgb), 0.7);
    border-radius: 6px;
    margin-bottom: 8px;
    font-size: 0.9rem;
    transition: all 0.2s ease;
    cursor: pointer;
  }

  .task-item:hover {
    background: rgba(102, 126, 234, 0.1);
    transform: translateX(5px);
  }

  .task-icon {
    width: 20px;
    height: 20px;
    background: var(--accent-primary, #667eea);
    border-radius: 4px;
    margin-right: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-primary);
    font-size: 12px;
  }

  .direct-button {
    display: block;
    width: 200px;
    margin: 30px auto;
    padding: 12px 24px;
    border: none;
    border-radius: 8px;
    background: linear-gradient(135deg, var(--accent-primary, #667eea) 0%, #09df75 100%);
    color: var(--text-primary);
    font-size: 16px;
    cursor: pointer;
    text-align: center;
    transition: all 0.2s ease;
    font-weight: 600;
  }

  .direct-button:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
  }
</style>
