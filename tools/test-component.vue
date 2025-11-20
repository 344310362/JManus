<template>
  <div class="test-component">
    <h1>Test Component</h1>
    <p class="text-white">White text</p>
    <p class="text-primer">Primer color text</p>
    <p class="text-medium-opacity">Medium opacity text (0.7)</p>
    <p class="incorrect-variable">Incorrect variable usage</p>
    <div class="bordered-element">Bordered element</div>
    <div class="gradient-background">Gradient background</div>
  </div>
</template>

<style scoped>
.test-component {
  background: var(--bg-primary);
  color: var(--text-primary);
  border: 1px solid #667eea;
}

h1 {
  color: var(--text-primary);
}

.text-white {
  color: var(--text-primary);
}

.text-primer {
  color: var(--primer-color);
}

.text-medium-opacity {
  color: var(--text-secondary);
}

.incorrect-variable {
  color: var(--text-primary, #ffffff);
}

.bordered-element {
  border: 1px solid var(--border-primary);
  padding: 10px;
  margin: 10px 0;
}

.gradient-background {
  background: linear-gradient(135deg, var(--accent-primary, #667eea) 0%, var(--primer-color) 100%);
  height: 100px;
}
</style>