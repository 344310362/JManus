/* Update main.ts to initialize theme */
import './assets/main.css'
import './assets/themes/dark.css'
import './assets/themes/light.css'

import { createApp, type App as VueApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import 'vue3-colorpicker/style.css'

import App from './App.vue'
import router from './router'
import { i18n,initializeLanguage } from './base/i18n'
import { useMessageDialogSingleton } from '@/composables/useMessageDialog'
import 'ant-design-vue/dist/reset.css'
import { themeConfig } from './utils/theme'
import actions from '@/qiankun/actions'
import { userStore } from "@/stores/user"
// Configure Iconify
import { addAPIProvider } from '@iconify/vue'

// Add fallback API providers
addAPIProvider('', {
  resources: ['https://api.iconify.design', 'https://api.unisvg.com', 'https://api.simplesvg.com'],
})
import 'nprogress/nprogress.css'
import Vue3ColorPicker from 'vue3-colorpicker'
import 'vue3-colorpicker/style.css'
// qiankun 相关辅助
import { renderWithQiankun, qiankunWindow,type QiankunProps } from 'vite-plugin-qiankun/dist/helper'


// 全局应用实例
let instance: VueApp | null = null
let mountPoint: Element | null = null

// 渲染函数
function render(props: QiankunProps = {}) {
  console.log('render function is called with props:', props);
  const { container,    userVo,
    accessToken,onGoToLogin } = props

  userStore.setUserInfo({
    permissions: [],
    roles: [],
    user: userVo,
    token: accessToken
  })
  console.log('userStore.user:', userStore.user);

  // 让主应用跳转登录页
  if (container  && !accessToken) {
    if (typeof onGoToLogin === 'function') {
      onGoToLogin();
    }
    return;
  }

  window.addEventListener('tokenUpdated', (e) => {
    userStore.updateAccessToken(e.detail.accessToken);
    console.log('token updated:', e.detail.accessToken)
  });

  // 指定挂载节点（防止 ID 冲突）
  mountPoint = container?.querySelector('#app') || document.querySelector('#app')

  if (!mountPoint) {
    console.error('[sub-app] mount point not found')
    return
  }

  // 避免重复挂载
  if (instance) {
    instance.unmount()
    instance = null
  }

  instance = createApp(App)
  // Initialize theme
  themeConfig.initTheme()
  const pinia = createPinia()

  // 注册所有插件
  instance.use(pinia)
  instance.use(Antd)
  instance.use(Vue3ColorPicker)
  instance.use(i18n)
  instance.use(router)
  instance.use(Vue3ColorPicker)
  // Initialize message dialog singleton early to ensure watchEffect is registered
// This ensures plan execution tracking works regardless of which route is accessed
  useMessageDialogSingleton()


  // 传递父应用通信能力（可选）
  if (props.onGlobalStateChange) {
    actions.setActions(props)
  }

  initializeLanguage()
    .then(() => {
      // 挂载到指定容器
      instance.mount(mountPoint)
    })
    .catch(error => {
      console.error('Failed to initialize language, mounting app with default language:', error)
      // 挂载到指定容器
      instance.mount(mountPoint)
    })

  // Add global passive touchstart listener to prevent warnings
  // This helps with performance by marking touch events as passive
  // Note: This won't fix warnings from third-party libraries, but helps with our own code
  if (typeof window !== 'undefined') {
    // Add a passive touchstart listener to the document to improve scroll performance
    // This is a workaround for browsers that warn about non-passive touchstart listeners
    document.addEventListener(
      'touchstart',
      () => {
        // Empty handler, just to mark the event as passive
      },
      { passive: true, capture: true }
    )
  }
}


const initQianKun = () => {
  console.log('initQianKun function is called');
  // ✅ qiankun 注册生命周期
  renderWithQiankun({
    bootstrap() {
      console.log('[sub-app] bootstrap')
    },
    mount(props) {
      console.log('[sub-app] mount with props:', props)
      render(props)
    },
    unmount() {
      console.log('[sub-app1] unmount')
      if (instance) {
        instance.unmount()
        instance = null
      }
      // 安全地清理挂载点
      if (mountPoint) {
        try {
          while (mountPoint.firstChild) {
            mountPoint.removeChild(mountPoint.firstChild)
          }
        } catch (e) {
          console.warn('[sub-app1] Error while cleaning mount point:', e)
        }
        mountPoint = null
      }
    },
    update(props) {
      console.log('[sub-app] update', props)
    }
  })
}

qiankunWindow.__POWERED_BY_QIANKUN__ ? initQianKun() : render()
