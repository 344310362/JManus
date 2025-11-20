<template>
  <div class="role-section">
    <h2>{{ role.name }}</h2>
    <div class="tasks">
      <div 
        v-for="(task, index) in role.tasks" 
        :key="index" 
        class="task-item"
        @click="handleTaskClick(task)"
      >
        <h3>{{ task.text }}</h3>
        <p class="task-description">{{ task.describe }}</p>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'RoleSection',
  props: {
    role: {
      type: Object,
      required: true,
      default: () => ({
        name: '',
        tasks: []
      })
    }
  },
  methods: {
    handleTaskClick(task) {
      // Emit an event when a task is clicked
      this.$emit('task-clicked', task);
    }
  }
}
</script>

<style scoped>
.role-section {
  margin: 20px 0;
  padding: 20px;
  border: 1px solid var(--border-primary);
  border-radius: 8px;
  background-color: var(--bg-card);
}

.role-section h2 {
  color: var(--text-primary);
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-primary);
}

.task-item {
  padding: 15px;
  margin: 10px 0;
  border-radius: 6px;
  background-color: var(--bg-secondary);
  cursor: pointer;
  transition: all 0.3s ease;
}

.task-item:hover {
  background-color: var(--bg-tertiary);
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

.task-item h3 {
  color: var(--text-primary);
  margin: 0 0 8px 0;
}

.task-description {
  color: var(--text-secondary);
  margin: 0;
  font-size: 0.9em;
  line-height: 1.5;
}
</style>