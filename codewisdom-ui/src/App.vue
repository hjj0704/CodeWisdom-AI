<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isAuthLayout = computed(() => route.meta.guest === true)

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-header height="56px" class="topbar">
      <div class="topbar-left">
        <router-link to="/import" class="brand">CodeWisdom AI</router-link>
        <nav v-if="!isAuthLayout && auth.isLoggedIn" class="nav">
          <router-link to="/import" active-class="active">项目导入</router-link>
        </nav>
      </div>
      <div class="topbar-right">
        <template v-if="auth.isLoggedIn && !isAuthLayout">
          <span class="user">{{ auth.profile?.nickname || auth.profile?.username }}</span>
          <el-button link type="primary" @click="logout">退出</el-button>
        </template>
        <template v-else-if="isAuthLayout">
          <router-link v-if="route.name !== 'login'" to="/login" class="link">登录</router-link>
          <router-link v-if="route.name !== 'register'" to="/register" class="link">注册</router-link>
        </template>
      </div>
    </el-header>
    <el-main :class="{ 'main-auth': isAuthLayout, 'main-app': !isAuthLayout }">
      <RouterView />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  background: #f5f7fa;
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  box-shadow: 0 1px 0 rgba(15, 23, 42, 0.04);
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 24px;
}
.brand {
  font-weight: 700;
  font-size: 18px;
  color: #1f2937;
  text-decoration: none;
}
.nav a {
  color: #6b7280;
  text-decoration: none;
  font-size: 14px;
  margin-right: 16px;
}
.nav a.active {
  color: #409eff;
  font-weight: 600;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user {
  color: #374151;
  font-size: 14px;
}
.link {
  color: #409eff;
  font-size: 14px;
  text-decoration: none;
}
.main-app {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 20px 48px;
}
.main-auth {
  padding: 0;
  max-width: none;
}
</style>
