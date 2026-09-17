import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  completeOnboarding,
  isOnboardingPending,
  markOnboardingForNewUser,
} from '../utils/onboarding'

const TOKEN_KEY = 'cw_token'
const PROFILE_KEY = 'cw_profile'

export { isOnboardingPending, markOnboardingForNewUser, completeOnboarding }

export interface AuthProfile {
  userId: number
  username: string
  nickname: string
}

function loadProfile(): AuthProfile | null {
  const raw = localStorage.getItem(PROFILE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthProfile
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const profile = ref<AuthProfile | null>(loadProfile())

  const isLoggedIn = computed(() => !!token.value)

  function setSession(newToken: string, newProfile: AuthProfile) {
    token.value = newToken
    profile.value = newProfile
    localStorage.setItem(TOKEN_KEY, newToken)
    localStorage.setItem(PROFILE_KEY, JSON.stringify(newProfile))
  }

  function logout() {
    token.value = null
    profile.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(PROFILE_KEY)
  }

  return { token, profile, isLoggedIn, setSession, logout, markOnboardingForNewUser, completeOnboarding }
})
