/* Theme Switcher Component */
<template>
  <div class="theme-switcher">
    <button
      class="theme-btn"
      @click="toggleDropdown"
      :title="$t('theme.switch')"
    >
      <Icon icon="carbon:color-palette" width="18" />
      <span class="current-theme">{{ currentThemeLabel }}</span>
      <Icon :icon="showDropdown ? 'carbon:chevron-up' : 'carbon:chevron-down'" width="14" class="chevron" />
    </button>

    <div v-if="showDropdown" class="theme-dropdown" @click.stop>
      <div class="dropdown-header">
        <span>{{ $t('theme.switch') }}</span>
        <button class="close-btn" @click="showDropdown = false">
          <Icon icon="carbon:close" width="16" />
        </button>
      </div>
      <div class="theme-options">
        <button
          v-for="option in themeOptions"
          :key="option.value"
          class="theme-option"
          :class="{
            active: currentTheme === option.value,
            loading: isChangingTheme && currentTheme !== option.value
          }"
          :disabled="isChangingTheme"
          @click="selectTheme(option.value)"
        >
          <span class="theme-name">{{ $t(`theme.${option.value}`) }}</span>
          <Icon
            v-if="isChangingTheme && currentTheme !== option.value"
            icon="carbon:circle-dash"
            width="16"
            class="loading-icon"
          />
          <Icon
            v-else-if="currentTheme === option.value"
            icon="carbon:checkmark"
            width="16"
            class="check-icon"
          />
        </button>
      </div>
    </div>

    <!-- Backdrop -->
    <div
      v-if="showDropdown"
      class="backdrop"
      @click="showDropdown = false"
    ></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Icon } from '@iconify/vue'

const { t } = useI18n()

// Theme options
const themeOptions = [
  { value: 'dark', title: t('theme.dark') },
  { value: 'light', title: t('theme.light') },
]

// Theme state
const showDropdown = ref(false)
const isChangingTheme = ref(false)

// Get current theme from localStorage or default to 'dark'
const currentTheme = ref(localStorage.getItem('jmanus-theme') || 'light')

// Computed properties
const currentThemeLabel = computed(() => {
  const current = themeOptions.find(opt => opt.value === currentTheme.value)
  return current ? current.title : t('theme.dark')
})

// Methods
const toggleDropdown = () => {
  showDropdown.value = !showDropdown.value
}

const selectTheme = async (theme: string) => {
  if (isChangingTheme.value || currentTheme.value === theme) return

  try {
    isChangingTheme.value = true
    // Apply theme immediately
    applyTheme(theme)
    // Save to localStorage
    localStorage.setItem('jmanus-theme', theme)
    // Update reactive ref
    currentTheme.value = theme
    // Close dropdown
    showDropdown.value = false
  } catch (error) {
    console.error('Failed to change theme:', error)
  } finally {
    isChangingTheme.value = false
  }
}

const applyTheme = (theme: string) => {
  document.documentElement.setAttribute('data-theme', theme)
}

// Close dropdown when clicking outside
const handleClickOutside = (event: MouseEvent) => {
  const target = event.target as HTMLElement
  if (!target.closest('.theme-switcher')) {
    showDropdown.value = false
  }
}

// Close dropdown on escape key
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    showDropdown.value = false
  }
}

// Initialize theme on mount
onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleKeydown)
  applyTheme(currentTheme.value)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.theme-switcher {
  position: relative;
  display: inline-block;
}

.theme-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: transparent;
  border: 1.5px solid var(--accent-primary, var(--accent-primary, #667eea));
  border-radius: 8px;
  color: var(--accent-secondary, #8da2fb);
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 14px;
  font-weight: 600;
  outline: none;
}

.theme-btn:hover {
  background: rgba(102, 126, 234, 0.15);
  border-color: var(--accent-secondary, var(--accent-secondary, #7c9eff));
  color: var(--accent-tertiary, var(--accent-tertiary, #a3bffa));
  box-shadow: 0 0 15px rgba(102, 126, 234, 0.2);
}

.theme-btn:focus {
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.5);
}

.current-theme {
  color: inherit;
  font-weight: 600;
  min-width: 40px;
  text-align: left;
  text-shadow: none;
}

.chevron {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  opacity: 0.9;
  filter: none;
}

.theme-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  z-index: 9999;
  margin-top: 4px;
  background: linear-gradient(135deg, rgba(40, 40, 50, 0.95), rgba(30, 30, 40, 0.95));
  backdrop-filter: blur(16px);
  border: 1px solid var(--border-secondary, var(--selection-bg, rgba(102, 126, 234, 0.3)));
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(102, 126, 234, 0.2);
  min-width: 200px;
  animation: slideDown 0.2s ease;
}

.dropdown-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-secondary, rgba(102, 126, 234, 0.2));
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary, var(--text-primary, #ffffff));
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1), rgba(102, 126, 234, 0.05));
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-tertiary, rgba(255, 255, 255, 0.6));
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-secondary, rgba(255, 255, 255, 0.8));
}

.theme-options {
  padding: 8px 0;
}

.theme-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 10px 16px;
  background: none;
  border: none;
  color: var(--text-tertiary, rgba(255, 255, 255, 0.7));
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
}

.theme-option:hover {
  background: var(--scrollbar-track, rgba(255, 255, 255, 0.05));
  color: var(--text-primary, rgba(255, 255, 255, 0.9));
}

.theme-option.active {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.2), rgba(102, 126, 234, 0.1));
  color: var(--accent-primary, var(--accent-secondary, #7c9eff));
  border-left: 3px solid var(--accent-primary, var(--accent-primary, #667eea));
  padding-left: 13px;
}

.theme-option.loading {
  opacity: 0.6;
  cursor: not-allowed;
}

.theme-option:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.theme-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
}

.check-icon {
  color: var(--accent-primary, var(--accent-primary, #667eea));
  opacity: 0.8;
}

.loading-icon {
  color: var(--accent-primary, var(--accent-primary, #667eea));
  opacity: 0.8;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9998;
  background: transparent;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-8px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* Responsive design */
@media (max-width: 768px) {
  .theme-dropdown {
    right: -8px;
    left: -8px;
    width: auto;
    min-width: auto;
  }

  .theme-btn {
    padding: 6px 10px;
    font-size: 13px;
  }

  .current-theme {
    min-width: 35px;
  }
}

/* Light theme adjustments */
:root[data-theme="light"] .theme-dropdown {
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(0, 0, 0, 0.1);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

:root[data-theme="light"] .dropdown-header {
  color: rgba(0, 0, 0, 0.8);
  border-bottom-color: rgba(0, 0, 0, 0.1);
}

:root[data-theme="light"] .close-btn {
  color: rgba(0, 0, 0, 0.6);
}

:root[data-theme="light"] .close-btn:hover {
  background: rgba(0, 0, 0, 0.1);
  color: rgba(0, 0, 0, 0.8);
}

:root[data-theme="light"] .theme-option {
  color: rgba(0, 0, 0, 0.7);
}

:root[data-theme="light"] .theme-option:hover {
  background: rgba(0, 0, 0, 0.05);
  color: rgba(0, 0, 0, 0.9);
}
</style>