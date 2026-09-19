<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Connection, Cpu, DataAnalysis } from '@element-plus/icons-vue'
import { login } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import BrandMark from '../components/BrandMark.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

async function onSubmit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    const data = await login({
      username: form.username.trim(),
      password: form.password,
    })
    auth.setSession(data.token, {
      userId: data.userId,
      username: data.username,
      nickname: data.nickname,
    })
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/import'
    await router.replace(redirect)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-shell">
    <div class="auth-brand">
      <div class="auth-brand__logo">
        <BrandMark :size="48" class="auth-brand__mark" />
        <span class="section-label">Secure Access</span>
      </div>
      <h1 class="gradient-text">登录后即可导入项目</h1>
      <p class="auth-brand__desc">
        账号用来区分「你的项目」和别人的项目。登录后可以导入代码、在线查看和检查问题。
      </p>
      <ul class="auth-features">
        <li>
          <div class="auth-features__icon"><el-icon><Connection /></el-icon></div>
          <div>
            <div class="auth-features__title">代码只属于你</div>
            <div class="auth-features__sub">导入内容保存在你的账号下，不会对外公开</div>
          </div>
        </li>
        <li>
          <div class="auth-features__icon"><el-icon><Cpu /></el-icon></div>
          <div>
            <div class="auth-features__title">不会窃取源码</div>
            <div class="auth-features__sub">仅用于你在本平台上的查看与分析，不用于对外模型训练</div>
          </div>
        </li>
        <li>
          <div class="auth-features__icon"><el-icon><DataAnalysis /></el-icon></div>
          <div>
            <div class="auth-features__title">改完要你确认</div>
            <div class="auth-features__sub">AI 或系统的修改建议，都需要你点头后才写入文件</div>
          </div>
        </li>
      </ul>
    </div>

    <div class="auth-form-panel glass-panel glass-panel--glow">
      <h2>登录</h2>
      <p v-if="route.query.reason" class="auth-form-panel__sub">
        导入或打开项目需要登录。登录后会回到你刚才的页面。
      </p>
      <p v-else class="auth-form-panel__sub">已有账号请直接登录；没有账号可先注册。</p>
      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" placeholder="3~32 位字母数字" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="至少 6 位"
            size="large"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="submit" size="large" :loading="loading" @click="onSubmit">
          登录
        </el-button>
      </el-form>
      <p class="auth-foot">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </p>
    </div>
  </div>
</template>
