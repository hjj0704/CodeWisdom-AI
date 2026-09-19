import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import router from './router'
import { useThemeStore } from './stores/theme'
import { useSettingsStore } from './stores/settings'
import './style.css'

const pinia = createPinia()
const app = createApp(App)
app.use(pinia)
app.use(router)
app.use(ElementPlus)
useThemeStore().init()
useSettingsStore().applyMotionPreference()
app.mount('#app')
