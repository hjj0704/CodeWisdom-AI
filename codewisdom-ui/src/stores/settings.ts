import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_KEY = 'cw_user_settings'

export interface UserSettings {
  editorFontSize: number
  editorMinimap: boolean
  reduceMotion: boolean
  workbenchHideDependency: boolean
  workbenchHideCatch: boolean
  workbenchCompactPanels: boolean
}

const DEFAULTS: UserSettings = {
  editorFontSize: 14,
  editorMinimap: true,
  reduceMotion: false,
  workbenchHideDependency: true,
  workbenchHideCatch: true,
  workbenchCompactPanels: false,
}

function load(): UserSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { ...DEFAULTS }
    return { ...DEFAULTS, ...JSON.parse(raw) } as UserSettings
  } catch {
    return { ...DEFAULTS }
  }
}

export const useSettingsStore = defineStore('settings', () => {
  const settings = ref<UserSettings>(load())

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings.value))
    applyMotionPreference()
  }

  function applyMotionPreference() {
    document.documentElement.classList.toggle('cw-reduce-motion', settings.value.reduceMotion)
  }

  function reset() {
    settings.value = { ...DEFAULTS }
    persist()
  }

  function patch(partial: Partial<UserSettings>) {
    settings.value = { ...settings.value, ...partial }
    persist()
  }

  applyMotionPreference()

  return { settings, patch, reset, applyMotionPreference }
})
