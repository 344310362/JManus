<template>
  <div id="app">
    <Sidebar @navigate="handleNavigation" />
    <div class="main-content">
      <HomeView v-if="currentView === 'home' || currentView === 'roles'" />
      <div v-else class="about-view">
        <h1>About</h1>
        <p>This is a sample application demonstrating role-based plan tasks with detailed descriptions.</p>
      </div>
    </div>
  </div>
</template>

<script>
import Sidebar from './components/Sidebar.vue';
import HomeView from './components/role-section/HomeView.vue';

export default {
  name: 'App',
  components: {
    Sidebar,
    HomeView
  },
  data() {
    return {
      currentView: 'home'
    };
  },
  methods: {
    handleNavigation(section) {
      this.currentView = section;
    }
  }
};
</script>

<style>
:root {
  /* Dark theme colors */
  --bg-primary: #0a0a0a;
  --bg-secondary: #1a1a1a;
  --bg-tertiary: #2a2a2a;
  --bg-card: #1e1e1e;
  --text-primary: #ffffff;
  --text-secondary: rgba(255, 255, 255, 0.7);
  --text-tertiary: rgba(255, 255, 255, 0.5);
  --border-primary: rgba(255, 255, 255, 0.1);
  --accent-primary: #667eea;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Arial', sans-serif;
  background-color: var(--bg-primary);
  color: var(--text-primary);
}

#app {
  display: flex;
}

.main-content {
  flex: 1;
  margin-left: 250px;
  padding: 20px;
}
</style>