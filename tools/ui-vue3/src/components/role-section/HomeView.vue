<template>
  <div class="home-view">
    <h1>Role-Based Plan Tasks</h1>
    
    <RoleSection 
      v-for="(role, index) in roles" 
      :key="index" 
      :role="role"
      @task-clicked="onTaskClicked"
    />
    
    <!-- Modal for task details -->
    <div v-if="selectedTask" class="modal" @click="closeModal">
      <div class="modal-content" @click.stop>
        <span class="close" @click="closeModal">&times;</span>
        <h2>{{ selectedTask.text }}</h2>
        <p>{{ selectedTask.describe }}</p>
        <div v-if="selectedTask.extraInfo">
          <h3>Additional Information:</h3>
          <p>{{ selectedTask.extraInfo }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import RoleSection from './role-section/RoleSection.vue';

export default {
  name: 'HomeView',
  components: {
    RoleSection
  },
  data() {
    return {
      selectedTask: null,
      roles: [
        {
          name: 'Administrator',
          tasks: [
            {
              text: 'System Monitoring',
              describe: 'Monitor system performance and health indicators to ensure optimal operation. This includes checking CPU usage, memory consumption, disk space, and network activity.',
              extraInfo: 'Requires 24/7 availability and immediate response to critical alerts.'
            },
            {
              text: 'User Management',
              describe: 'Manage user accounts, permissions, and access levels. Create, modify, or delete user profiles as needed.',
              extraInfo: 'Follow security protocols and maintain audit logs for all user changes.'
            }
          ]
        },
        {
          name: 'Developer',
          tasks: [
            {
              text: 'Code Implementation',
              describe: 'Write, test, and maintain code according to project specifications and coding standards. Participate in code reviews and collaborate with team members.',
              extraInfo: 'Adhere to version control practices and document code changes thoroughly.'
            },
            {
              text: 'Bug Fixing',
              describe: 'Identify, diagnose, and resolve software defects. Prioritize bugs based on severity and impact on users.',
              extraInfo: 'Coordinate with QA team to verify fixes and ensure no regressions.'
            }
          ]
        },
        {
          name: 'QA Engineer',
          tasks: [
            {
              text: 'Test Case Development',
              describe: 'Design and implement comprehensive test cases to validate software functionality. Ensure test coverage for all requirements.',
              extraInfo: 'Maintain test documentation and update test cases as features evolve.'
            },
            {
              text: 'Defect Reporting',
              describe: 'Document and report software defects with detailed steps to reproduce. Track defect status and verify fixes.',
              extraInfo: 'Work closely with developers to ensure clear communication of issues.'
            }
          ]
        }
      ]
    };
  },
  methods: {
    onTaskClicked(task) {
      this.selectedTask = task;
    },
    closeModal() {
      this.selectedTask = null;
    }
  }
}
</script>

<style scoped>
.home-view {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.home-view h1 {
  color: var(--text-primary);
  text-align: center;
  margin-bottom: 30px;
}

.modal {
  position: fixed;
  z-index: 1000;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
}

.modal-content {
  background-color: var(--bg-card);
  padding: 20px;
  border-radius: 8px;
  max-width: 600px;
  width: 90%;
  max-height: 80vh;
  overflow-y: auto;
}

.close {
  color: var(--text-primary);
  float: right;
  font-size: 28px;
  font-weight: bold;
  cursor: pointer;
}

.close:hover {
  color: var(--accent-primary);
}
</style>