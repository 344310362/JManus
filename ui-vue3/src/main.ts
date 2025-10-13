/* Update main.ts to initialize theme */
import './assets/main.css'
import './assets/themes/dark.css'
import './assets/themes/light.css'

import { createApp, type App as VueApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import Vue3ColorPicker from 'vue3-colorpicker'
import 'vue3-colorpicker/style.css'

import App from './App.vue'
import router from './router'
import { i18n } from './base/i18n'
import { themeConfig } from './utils/theme'
import actions from '@/qiankun/actions'
// qiankun 相关辅助
import { renderWithQiankun, qiankunWindow,type QiankunProps } from 'vite-plugin-qiankun/dist/helper'

/*
const pinia = createPinia()
const app = createApp(App)

// Initialize theme
themeConfig.initTheme()

app.use(pinia).use(Antd).use(Vue3ColorPicker).use(i18n).use(router).mount('#app')*/


// 全局应用实例
let instance: VueApp | null = null
let mountPoint: Element | null = null

// 渲染函数
function render(props: QiankunProps = {}) {
    console.log('render function is called with props:', props);
    const { container } = props

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

    // 传递父应用通信能力（可选）
    if (props.onGlobalStateChange) {
        actions.setActions(props)
    }

    // 挂载到指定容器
    instance.mount(mountPoint)
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