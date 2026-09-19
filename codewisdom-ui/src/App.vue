<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Moon, Sunny, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from './stores/auth'
import { useThemeStore } from './stores/theme'
import BrandMark from './components/BrandMark.vue'
import InteractiveTour from './components/InteractiveTour.vue'
import { importTourSteps } from './data/onboardingSteps'
import {
  completeGuestImportTour,
  completeImportTour,
  isGuestImportTourPending,
  isImportTourPending,
} from './utils/onboarding'

/** 深色主题专属：A–Z 字母光效自上而下飘落，需细看方可模糊辨认 */
const BG_LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'

const bgLetters = BG_LETTERS.split('').map((letter, i) => ({
  letter,
  left: `${3.5 + i * 3.65}%`,
  delay: `${(i * 1.35) % 26}s`,
  duration: `${48 + (i % 5) * 12}s`,
  opacity: 0.16 + (i % 4) * 0.05,
  blur: 1.3 + (i % 3) * 0.55,
  size: 20 + (i % 5) * 5,
  sway: `${((i % 7) - 3) * 12}px`,
  rotate: `${((i % 5) - 2) * 5}deg`,
}))

/** 浅色主题专属：流光层 + 自上而下柔和光点 */
const lightMotes = Array.from({ length: 22 }, (_, i) => {
  const n = i + 1
  const streak = n % 4 === 0
  return {
    left: `${2 + (n * 4.6) % 96}%`,
    variant: streak ? 'streak' : 'orb',
    width: streak ? 36 + (n % 3) * 14 : 80 + (n % 5) * 24,
    height: streak ? 108 + (n % 4) * 36 : 80 + (n % 5) * 24,
    blur: 7 + (n % 4) * 2.5,
    delay: `${(n * 1.5) % 18}s`,
    duration: `${17 + (n % 6) * 2.8}s`,
    opacity: 0.48 + (n % 4) * 0.14,
    sway: `${(n % 5 - 2) * 14}px`,
  }
})

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const themeStore = useThemeStore()

const showImportTour = ref(false)

const isAuthLayout = computed(() => route.meta.guest === true)
const isWorkbench = computed(() => route.name === 'project-workbench')

const navActive = computed(() => {
  if (route.name === 'import') return 'import'
  if (route.name === 'project-workbench') return 'workbench'
  if (route.name === 'help') return 'help'
  if (route.name === 'settings') return 'settings'
  return ''
})

const workbenchTo = computed(() => {
  if (route.name === 'project-workbench' && route.params.projectId) {
    return `/projects/${route.params.projectId}`
  }
  return '/import'
})

function logout() {
  auth.logout()
  router.push('/import')
}

function onImportTourComplete() {
  if (auth.isLoggedIn) {
    completeImportTour()
  } else {
    completeGuestImportTour()
  }
}

function tryStartImportTour() {
  if (route.name !== 'import') return
  if (auth.isLoggedIn && isImportTourPending()) {
    showImportTour.value = true
    return
  }
  if (!auth.isLoggedIn && isGuestImportTourPending()) {
    showImportTour.value = true
  }
}

async function onNavImport() {
  if (navActive.value === 'import') return
  if (route.name === 'project-workbench') {
    try {
      await ElMessageBox.confirm(
        '确定返回导入中心吗？未保存的修改会留在当前项目，之后可从「我的项目」再次进入。',
        '返回导入中心',
        { confirmButtonText: '返回导入中心', cancelButtonText: '留在此页', type: 'info' },
      )
    } catch {
      return
    }
  }
  await router.push('/import')
}

async function onNavWorkbench() {
  if (navActive.value === 'workbench') return
  if (!auth.isLoggedIn) {
    await router.push({ name: 'login', query: { redirect: workbenchTo.value, reason: 'open-workbench' } })
    return
  }
  if (workbenchTo.value === '/import') {
    ElMessage.info('请先在导入中心的「我的项目」里打开一个项目')
    await router.push('/import')
    return
  }
  await router.push(workbenchTo.value)
}

async function onNavHelp() {
  if (navActive.value === 'help') return
  await router.push('/help')
}

async function onNavSettings() {
  if (navActive.value === 'settings') return
  await router.push('/settings')
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
      <div v-if="!themeStore.isDark" class="cw-bg__light">
        <div class="cw-bg__aurora cw-bg__aurora--a" />
        <div class="cw-bg__aurora cw-bg__aurora--b" />
        <div class="cw-bg__aurora cw-bg__aurora--c" />
        <div class="cw-bg__motes">
          <span
            v-for="(m, idx) in lightMotes"
            :key="idx"
            class="cw-bg__mote"
            :class="`cw-bg__mote--${m.variant}`"
            :style="{
              '--m-left': m.left,
              '--m-w': `${m.width}px`,
              '--m-h': `${m.height}px`,
              '--m-blur': `${m.blur}px`,
              '--m-delay': m.delay,
              '--m-duration': m.duration,
              '--m-opacity': String(m.opacity),
              '--m-sway': m.sway,
            }"
          />
        </div>
      </div>
      <div v-if="themeStore.isDark" class="cw-bg__letters">
        <span
          v-for="(item, idx) in bgLetters"
          :key="idx"
          class="cw-bg__letter"
          :style="{
            '--l-left': item.left,
            '--l-size': `${item.size}px`,
            '--l-blur': `${item.blur}px`,
            '--l-delay': item.delay,
            '--l-duration': item.duration,
            '--l-opacity': String(item.opacity),
            '--l-sway': item.sway,
            '--l-rotate': item.rotate,
          }"
        >{{ item.letter }}</span>
      </div>
    </div>

    <el-container class="layout">
      <el-header height="56px" class="topbar">
        <div class="topbar-inner">
          <div class="topbar-left">
            <router-link to="/import" class="brand">
              <BrandMark :size="36" class="brand__mark" />
              <span class="brand__text">
                <span class="brand__name">CodeWisdom</span>
                <span class="brand__tag">代码智能平台</span>
              </span>
            </router-link>
            <nav v-if="!isAuthLayout" class="nav">
              <button
                type="button"
                class="nav-item"
                :class="{ active: navActive === 'import' }"
                @click="onNavImport"
              >
                导入中心
              </button>
              <button
                type="button"
                class="nav-item"
                :class="{ active: navActive === 'workbench' }"
                @click="onNavWorkbench"
              >
                工作台
              </button>
              <button
                type="button"
                class="nav-item"
                data-tour="nav-help"
                :class="{ active: navActive === 'help' }"
                @click="onNavHelp"
              >
                帮助
              </button>
              <button
                type="button"
                class="nav-item"
                :class="{ active: navActive === 'settings' }"
                @click="onNavSettings"
              >
                设置
              </button>
            </nav>
          </div>
          <div class="topbar-right">
            <template v-if="!isAuthLayout">
              <el-tooltip :content="themeStore.isDark ? '切换浅色' : '切换深色'" placement="bottom">
                <el-button
                  class="icon-btn"
                  circle
                  :icon="themeStore.isDark ? Sunny : Moon"
                  @click="themeStore.toggle()"
                />
              </el-tooltip>
            </template>
            <template v-if="auth.isLoggedIn && !isAuthLayout">
              <div class="user-chip">
                <span class="user-chip__avatar">{{ (auth.profile?.nickname || auth.profile?.username || '?')[0] }}</span>
                <span class="user-chip__name">{{ auth.profile?.nickname || auth.profile?.username }}</span>
              </div>
              <el-button class="logout-btn" size="default" @click="logout">
                <el-icon><SwitchButton /></el-icon>
                退出
              </el-button>
            </template>
            <template v-else-if="isAuthLayout">
              <router-link v-if="route.name !== 'login'" to="/login" class="nav-link">登录</router-link>
              <router-link v-if="route.name !== 'register'" to="/register" class="nav-link nav-link--primary">
                免费注册
              </router-link>
            </template>
            <template v-else-if="!isAuthLayout">
              <router-link to="/login" class="nav-link">登录</router-link>
              <router-link to="/register" class="nav-link nav-link--primary">免费注册</router-link>
            </template>
          </div>
        </div>
      </el-header>

      <el-main
        :class="{
          'main-auth': isAuthLayout,
          'main-app': !isAuthLayout && !isWorkbench,
          'main-workbench': isWorkbench,
        }"
      >
        <RouterView v-slot="{ Component, route: viewRoute }">
          <Transition name="cw-page" mode="out-in">
            <KeepAlive include="ProjectWorkbench">
              <component
                :is="Component"
                :key="
                  viewRoute.name === 'project-workbench'
                    ? `workbench-${viewRoute.params.projectId}`
                    : viewRoute.fullPath
                "
              />
            </KeepAlive>
          </Transition>
        </RouterView>
      </el-main>

      <footer v-if="!isAuthLayout" class="footer">
        <span>CodeWisdom · 在线看代码、查问题、试修改</span>
        <span class="footer__sep">|</span>
        <router-link to="/help" class="footer-link">功能说明</router-link>
        <span class="footer__sep">|</span>
        <span>分析结果仅供参考，修改需您确认</span>
      </footer>
    </el-container>

    <InteractiveTour
      v-model="showImportTour"
      :steps="importTourSteps"
      :on-complete="onImportTourComplete"
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
  position: sticky;
  top: 0;
  z-index: 100;
  padding: 0 !important;
  height: 56px !important;
  background: var(--cw-bg-elevated);
  border-bottom: 1px solid var(--cw-border);
  box-shadow: var(--cw-shadow-sm);
}

.topbar-inner {
  max-width: 1440px;
  margin: 0 auto;
  height: 100%;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 40px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;
}

.brand__mark {
  display: block;
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.brand__name {
  font-weight: 600;
  font-size: 16px;
  color: var(--cw-text);
}

.brand__tag {
  font-size: 11px;
  color: var(--cw-text-muted);
}

.nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-item {
  color: var(--cw-text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  padding: 8px 16px;
  border-radius: var(--cw-radius-sm);
  transition: color 0.2s ease, background 0.2s ease;
  border: none;
  background: transparent;
  cursor: pointer;
  font-family: inherit;
  line-height: 1.5;
}

.nav-item:hover {
  color: var(--cw-primary);
  background: var(--cw-primary-bg);
}

.nav-item.active {
  color: var(--cw-primary);
  background: var(--cw-primary-bg);
  font-weight: 600;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.icon-btn {
  border-color: var(--cw-border) !important;
  color: var(--cw-text-secondary) !important;
  background: transparent !important;
}

.icon-btn:hover {
  color: var(--cw-primary) !important;
  border-color: var(--cw-primary-border) !important;
  background: var(--cw-primary-bg) !important;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 4px;
  border-radius: 999px;
  background: var(--cw-surface-muted);
  border: 1px solid var(--cw-border);
}

.user-chip__avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--cw-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-chip__name {
  font-size: 13px;
  color: var(--cw-text);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-btn {
  border-color: var(--cw-border) !important;
  color: var(--cw-text-secondary) !important;
}

.logout-btn:hover {
  color: var(--cw-danger) !important;
  border-color: var(--cw-danger) !important;
  background: #fff1f0 !important;
}

html.dark .logout-btn:hover {
  background: rgba(255, 77, 79, 0.12) !important;
}

.nav-link {
  font-size: 14px;
  padding: 8px 16px;
  border-radius: var(--cw-radius-sm);
  color: var(--cw-text-secondary);
  border: 1px solid transparent;
  text-decoration: none;
  font-weight: 500;
}

.nav-link:hover {
  color: var(--cw-primary);
}

.nav-link--primary {
  color: #fff !important;
  background: var(--cw-primary);
  font-weight: 600;
}

.nav-link--primary:hover {
  color: #fff !important;
  background: var(--cw-primary-hover);
}

.main-app {
  max-width: 1120px;
  margin: 0 auto;
  padding: 32px 24px 64px;
  overflow: visible;
}

.main-workbench {
  max-width: 1600px;
  margin: 0 auto;
  padding: 20px 20px 56px;
}

.main-auth {
  padding: 0;
  max-width: none;
}

.footer {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 20px 16px;
  font-size: 12px;
  color: var(--cw-text-muted);
  border-top: 1px solid var(--cw-border);
  background: var(--cw-bg-elevated);
}

.footer__sep {
  margin: 0 10px;
  color: var(--cw-border-strong);
}

.footer-link {
  color: var(--cw-text-secondary);
}

.footer-link:hover {
  color: var(--cw-primary);
}
</style>
