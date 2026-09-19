import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'cw_theme'

function readStoredTheme(): ThemeMode {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'dark') return 'dark'
  return 'light'
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(readStoredTheme())

  const isDark = computed(() => mode.value === 'dark')

  function apply() {
    document.documentElement.classList.toggle('dark', mode.value === 'dark')
    document.documentElement.dataset.theme = mode.value
  }

  function init() {
    apply()
  }

  function toggle() {
    mode.value = mode.value === 'dark' ? 'light' : 'dark'
    localStorage.setItem(STORAGE_KEY, mode.value)
    apply()
  }

  return { mode, isDark, init, toggle }
})
