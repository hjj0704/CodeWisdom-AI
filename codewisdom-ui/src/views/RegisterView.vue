<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-head">
        <h1>创建账号</h1>
        <p>注册后即可导入 Git / ZIP 项目，接入线上网关</p>
      </div>
      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" placeholder="字母、数字、下划线" />
        </el-form-item>
        <el-form-item label="昵称（可选）">
          <el-input v-model="form.nickname" autocomplete="nickname" placeholder="展示名称" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input
            v-model="form.confirm"
            type="password"
            show-password
            autocomplete="new-password"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">注册</el-button>
      </el-form>
      <p class="foot">
        已有账号？
        <router-link to="/login">去登录</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: calc(100vh - 56px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background: linear-gradient(135deg, #eef2ff 0%, #f5f7fa 45%, #e8f4ff 100%);
}
.auth-card {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 16px;
  padding: 32px 28px 24px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.08);
  border: 1px solid #ebeef5;
}
.auth-head h1 {
  margin: 0 0 8px;
  font-size: 24px;
  color: #1f2937;
}
.auth-head p {
  margin: 0 0 24px;
  color: #6b7280;
  font-size: 14px;
}
.submit {
  width: 100%;
  margin-top: 8px;
}
.foot {
  margin: 20px 0 0;
  text-align: center;
  color: #6b7280;
  font-size: 14px;
}
.foot a {
  color: #409eff;
  font-weight: 500;
}
</style>
