<template>
  <div class="sidebar">
    <nav>
      <ul>
        <li><a href="#home" @click="navigate('home')">Home</a></li>
        <li><a href="#roles" @click="navigate('roles')">Role Tasks</a></li>
        <li><a href="#about" @click="navigate('about')">About</a></li>
      </ul>
    </nav>
  </div>
</template>

<script>
export default {
  name: 'Sidebar',
  methods: {
    navigate(section) {
      this.$emit('navigate', section);
    }
  }
}
</script>

<style scoped>
.sidebar {
  width: 250px;
  background: var(--bg-primary);
  color: var(--text-primary);
  height: 100vh;
  position: fixed;
  padding: 20px 0;
}

nav ul {
  list-style-type: none;
  padding: 0;
}

nav ul li {
  padding: 10px 20px;
}

nav ul li a {
  color: var(--text-primary);
  text-decoration: none;
  display: block;
  padding: 10px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

nav ul li a:hover {
  background-color: var(--bg-secondary);
}
</style>