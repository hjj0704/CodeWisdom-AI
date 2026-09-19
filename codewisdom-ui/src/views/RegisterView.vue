<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { FolderOpened, Lock, UserFilled } from '@element-plus/icons-vue'
import { register } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import BrandMark from '../components/BrandMark.vue'

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
        <BrandMark :size="48" class="auth-brand__mark" />
        <span class="section-label">Join the Platform</span>
      </div>
      <h1 class="gradient-text">免费注册，开始用</h1>
      <p class="auth-brand__desc">
        注册后可以导入网上链接或 ZIP 压缩包里的代码，在线查看、检查问题、尝试修改。
      </p>
      <ul class="auth-features">
        <li>
          <div class="auth-features__icon"><el-icon><UserFilled /></el-icon></div>
          <div>
            <div class="auth-features__title">你的项目归你管</div>
            <div class="auth-features__sub">每个账号只能看到自己的项目</div>
          </div>
        </li>
        <li>
          <div class="auth-features__icon"><el-icon><FolderOpened /></el-icon></div>
          <div>
            <div class="auth-features__title">两种导入方式</div>
            <div class="auth-features__sub">网上链接（Git）或本地上传 ZIP 都可以</div>
          </div>
        </li>
        <li>
          <div class="auth-features__icon"><el-icon><Lock /></el-icon></div>
          <div>
            <div class="auth-features__title">密码安全保存</div>
            <div class="auth-features__sub">密码加密存储，不会明文保存</div>
          </div>
        </li>
      </ul>
    </div>

    <div class="auth-form-panel glass-panel glass-panel--glow">
      <h2>创建账号</h2>
      <p class="auth-form-panel__sub">填好信息点注册，会自动登录并进入导入中心</p>
      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" placeholder="字母、数字、下划线" size="large" />
        </el-form-item>
        <el-form-item label="昵称（可选）">
          <el-input v-model="form.nickname" autocomplete="nickname" placeholder="页面上显示的名字" size="large" />
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
