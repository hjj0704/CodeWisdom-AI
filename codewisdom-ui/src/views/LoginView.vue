<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Connection, Cpu, DataAnalysis } from '@element-plus/icons-vue'
import { login } from '../api/auth'
import { useAuthStore } from '../stores/auth'

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
        <div class="auth-brand__mark">CW</div>
        <span class="section-label">Secure Access</span>
      </div>
      <h1 class="gradient-text">下一代代码智能治理平台</h1>
      <p class="auth-brand__desc">
        基于多智能体编排，串联静态分析、自动修复与量化评测，让代码质量可观测、可迭代。
      </p>
      <ul class="auth-features">
        <li>
          <div class="auth-features__icon"><el-icon><Connection /></el-icon></div>
          <div>
            <div class="auth-features__title">云端网关直连</div>
            <div class="auth-features__sub">本地仅跑前端，API 经 ECS 微服务集群</div>
          </div>
        </li>
        <li>
          <div class="auth-features__icon"><el-icon><Cpu /></el-icon></div>
          <div>
            <div class="auth-features__title">多智能体编排</div>
            <div class="auth-features__sub">注释生成 · 修复建议 · 人工复核闭环</div>
          </div>
        </li>
        <li>
          <div class="auth-features__icon"><el-icon><DataAnalysis /></el-icon></div>
          <div>
            <div class="auth-features__title">量化评测导出</div>
            <div class="auth-features__sub">指标追踪与错误溯源，支撑持续迭代</div>
          </div>
        </li>
      </ul>
    </div>

    <div class="auth-form-panel glass-panel glass-panel--glow">
      <h2>欢迎回来</h2>
      <p class="auth-form-panel__sub">登录以继续管理项目导入与治理流程</p>
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
          进入控制台
        </el-button>
      </el-form>
      <p class="auth-foot">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </p>
    </div>
  </div>
</template>
