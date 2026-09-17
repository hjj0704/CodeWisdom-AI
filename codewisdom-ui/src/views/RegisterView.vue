<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { FolderOpened, Lock, UserFilled } from '@element-plus/icons-vue'
import { register } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirm: '',
  nickname: '',
})

async function onSubmit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  if (form.password !== form.confirm) {
    ElMessage.warning('两次密码不一致')
    return
  }
  loading.value = true
  try {
    const data = await register({
      username: form.username.trim(),
      password: form.password,
      nickname: form.nickname.trim() || undefined,
    })
    auth.setSession(data.token, {
      userId: data.userId,
      username: data.username,
      nickname: data.nickname,
    })
    auth.markOnboardingForNewUser()
    ElMessage.success('注册成功，已自动登录')
    await router.replace('/import')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '注册失败')
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
        <span class="section-label">Join the Platform</span>
      </div>
      <h1 class="gradient-text">开启你的代码治理之旅</h1>
      <p class="auth-brand__desc">
        注册账号后即可导入 Git / ZIP 项目，接入线上分析管线，体验完整的智能体工作流。
      </p>
      <ul class="auth-features">
        <li>
          <div class="auth-features__icon"><el-icon><UserFilled /></el-icon></div>
          <div>
            <div class="auth-features__title">独立账号空间</div>
            <div class="auth-features__sub">安全令牌鉴权，会话本地加密存储</div>
          </div>
        </li>
        <li>
          <div class="auth-features__icon"><el-icon><FolderOpened /></el-icon></div>
          <div>
            <div class="auth-features__title">一键项目导入</div>
            <div class="auth-features__sub">支持 Git 克隆与 ZIP 解压，自动构建文件树</div>
          </div>
        </li>
        <li>
          <div class="auth-features__icon"><el-icon><Lock /></el-icon></div>
          <div>
            <div class="auth-features__title">BCrypt 密码保护</div>
            <div class="auth-features__sub">服务端哈希存储，传输经 HTTPS 网关</div>
          </div>
        </li>
      </ul>
    </div>

    <div class="auth-form-panel glass-panel glass-panel--glow">
      <h2>创建账号</h2>
      <p class="auth-form-panel__sub">填写信息完成注册，系统将自动登录</p>
      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" placeholder="字母、数字、下划线" size="large" />
        </el-form-item>
        <el-form-item label="昵称（可选）">
          <el-input v-model="form.nickname" autocomplete="nickname" placeholder="控制台展示名称" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" size="large" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input
            v-model="form.confirm"
            type="password"
            show-password
            autocomplete="new-password"
            size="large"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="submit" size="large" :loading="loading" @click="onSubmit">
          注册并进入
        </el-button>
      </el-form>
      <p class="auth-foot">
        已有账号？
        <router-link to="/login">去登录</router-link>
      </p>
    </div>
  </div>
</template>
