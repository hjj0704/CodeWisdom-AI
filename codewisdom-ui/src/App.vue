<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'
import { SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from './stores/auth'
import { useProjectStore } from './stores/project'
import InteractiveTour from './components/InteractiveTour.vue'
import { importTourSteps } from './data/onboardingSteps'
import { completeImportTour, isImportTourPending } from './utils/onboarding'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const projectStore = useProjectStore()

const showImportTour = ref(false)

const isAuthLayout = computed(() => route.meta.guest === true)
const isWorkbench = computed(() => route.name === 'project-workbench')

const workbenchTo = computed(() => {
  const fromRoute = route.params.projectId
  if (fromRoute) return `/projects/${fromRoute}`
  const recent = projectStore.recentProjects[0]?.projectId
  if (recent) return `/projects/${recent}`
  return '/import'
})

function logout() {
  auth.logout()
  router.push('/login')
}

function tryStartImportTour() {
  if (auth.isLoggedIn && isImportTourPending() && route.name === 'import') {
    showImportTour.value = true
  }
}

onMounted(() => {
  tryStartImportTour()
})

watch(
  () => route.name,
  () => tryStartImportTour(),
)
</script>

<template>
  <div class="app-root">
    <div class="cw-bg" aria-hidden="true">
      <div class="cw-bg__grid" />
      <div class="cw-bg__orb cw-bg__orb--1" />
      <div class="cw-bg__orb cw-bg__orb--2" />
    </div>

    <el-container class="layout">
      <el-header height="64px" class="topbar glass-panel">
        <div class="topbar-left">
          <router-link to="/import" class="brand">
            <span class="brand__mark">CW</span>
            <span class="brand__text">
              <span class="brand__name">CodeWisdom</span>
              <span class="brand__tag">AI Platform</span>
            </span>
          </router-link>
          <nav v-if="!isAuthLayout && auth.isLoggedIn" class="nav">
            <router-link to="/import" active-class="active">项目导入</router-link>
            <router-link
              :to="workbenchTo"
              active-class="active"
              :class="{ active: isWorkbench }"
            >
              工作台
            </router-link>
            <router-link to="/help" active-class="active" data-tour="nav-help">帮助</router-link>
          </nav>
        </div>
        <div class="topbar-right">
          <template v-if="auth.isLoggedIn && !isAuthLayout">
            <div class="user-chip">
              <span class="user-chip__dot" />
              <span class="user-chip__name">{{ auth.profile?.nickname || auth.profile?.username }}</span>
            </div>
            <el-button class="logout-btn" :icon="SwitchButton" round @click="logout">退出</el-button>
          </template>
          <template v-else-if="isAuthLayout">
            <router-link v-if="route.name !== 'login'" to="/login" class="nav-link">登录</router-link>
            <router-link v-if="route.name !== 'register'" to="/register" class="nav-link nav-link--primary">
              注册
            </router-link>
          </template>
        </div>
      </el-header>

      <el-main
        :class="{
          'main-auth': isAuthLayout,
          'main-app': !isAuthLayout && !isWorkbench,
          'main-workbench': isWorkbench,
        }"
      >
        <RouterView />
      </el-main>

      <footer v-if="!isAuthLayout && auth.isLoggedIn" class="footer">
        <span>CodeWisdom AI · 多智能体代码治理</span>
        <span class="footer__sep">·</span>
        <router-link to="/help" class="footer-link">功能说明</router-link>
        <span class="footer__sep">·</span>
        <span>辅助分析结果，需人工确认</span>
      </footer>
    </el-container>

    <InteractiveTour
      v-model="showImportTour"
      :steps="importTourSteps"
      :on-complete="completeImportTour"
    />
  </div>
</template>

<style scoped>
.app-root {
  position: relative;
  min-height: 100vh;
}

.layout {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  background: transparent;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 12px 16px 0;
  padding: 0 20px !important;
  height: 64px !important;
  border-radius: var(--cw-radius);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 32px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;
}

.brand__mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(34, 211, 238, 0.2), rgba(129, 140, 248, 0.2));
  border: 1px solid var(--cw-border-strong);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--cw-mono);
  font-size: 12px;
  font-weight: 700;
  color: var(--cw-accent);
  box-shadow: 0 0 20px var(--cw-accent-glow);
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand__name {
  font-weight: 700;
  font-size: 16px;
  color: var(--cw-text);
}

.brand__tag {
  font-size: 11px;
  color: var(--cw-text-muted);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.nav a {
  color: var(--cw-text-muted);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  padding: 8px 4px;
  border-bottom: 2px solid transparent;
  transition: color 0.2s, border-color 0.2s;
}

.nav a.active,
.nav a:hover {
  color: var(--cw-accent);
  border-bottom-color: var(--cw-accent);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(34, 211, 238, 0.08);
  border: 1px solid var(--cw-border);
}

.user-chip__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--cw-success);
  box-shadow: 0 0 8px rgba(52, 211, 153, 0.6);
}

.user-chip__name {
  font-size: 13px;
  color: var(--cw-text);
}

.logout-btn {
  border-color: var(--cw-border) !important;
  color: var(--cw-text-muted) !important;
  background: transparent !important;
}

.logout-btn:hover {
  color: var(--cw-accent) !important;
  border-color: var(--cw-border-strong) !important;
}

.nav-link {
  font-size: 14px;
  padding: 8px 16px;
  border-radius: 999px;
  color: var(--cw-text-muted);
  border: 1px solid transparent;
  text-decoration: none;
}

.nav-link--primary {
  color: var(--cw-bg-deep);
  background: linear-gradient(135deg, #0891b2, #22d3ee);
  font-weight: 600;
  box-shadow: 0 4px 16px rgba(34, 211, 238, 0.3);
}

.nav-link--primary:hover {
  color: var(--cw-bg-deep);
  text-shadow: none;
  box-shadow: 0 6px 24px rgba(34, 211, 238, 0.45);
}

.main-app {
  max-width: 1080px;
  margin: 0 auto;
  padding: 28px 20px 80px;
}

.main-workbench {
  max-width: 1600px;
  margin: 0 auto;
  padding: 20px 16px 72px;
}

.main-auth {
  padding: 0;
  max-width: none;
}

.footer {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 16px;
  font-size: 12px;
  color: var(--cw-text-muted);
}

.footer__sep {
  margin: 0 8px;
  opacity: 0.4;
}

.footer-link {
  color: var(--cw-accent);
  text-decoration: none;
}

.footer-link:hover {
  text-decoration: underline;
}
</style>
