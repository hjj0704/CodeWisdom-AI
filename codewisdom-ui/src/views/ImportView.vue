<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { importFromGit, importFromZip, pingGateway, type ImportResult } from '../api/import'
import { useProjectStore } from '../stores/project'

const store = useProjectStore()
const loading = ref(false)
const gatewayOk = ref<boolean | null>(null)

const gitForm = reactive({
  url: '',
  branch: '',
  name: '',
})

const zipName = ref('')
const zipFile = ref<File | null>(null)
const lastResult = ref<ImportResult | null>(null)

async function checkGateway() {
  try {
    const data = await pingGateway()
    gatewayOk.value = true
    ElMessage.success(`网关连通：${data}`)
  } catch (e) {
    gatewayOk.value = false
    ElMessage.error(e instanceof Error ? e.message : '网关不可达')
  }
}

async function submitGit() {
  if (!gitForm.url.trim()) {
    ElMessage.warning('请填写仓库地址')
    return
  }
  loading.value = true
  try {
    const result = await importFromGit({
      url: gitForm.url.trim(),
      branch: gitForm.branch.trim() || undefined,
      name: gitForm.name.trim() || undefined,
    })
    lastResult.value = result
    store.setImportResult(result)
    ElMessage.success(`导入成功，项目 ID=${result.projectId}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导入失败')
  } finally {
    loading.value = false
  }
}

function onZipSelected(event: Event) {
  const input = event.target as HTMLInputElement
  zipFile.value = input.files?.[0] ?? null
}

async function submitZip() {
  if (!zipFile.value) {
    ElMessage.warning('请选择 ZIP 文件')
    return
  }
  loading.value = true
  try {
    const result = await importFromZip(
      zipFile.value,
      zipName.value.trim() || undefined,
    )
    lastResult.value = result
    store.setImportResult(result)
    ElMessage.success(`导入成功，项目 ID=${result.projectId}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导入失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>项目导入</span>
          <el-button size="small" @click="checkGateway">检测网关</el-button>
        </div>
      </template>

      <el-alert
        v-if="gatewayOk === false"
        type="error"
        show-icon
        title="无法连接 API。请确认 ECS 网关 8080 可达，且 .env.development 中 VITE_API_PROXY_TARGET 正确。"
        class="mb"
      />

      <el-tabs>
        <el-tab-pane label="Git 仓库">
          <el-form label-width="96px" @submit.prevent="submitGit">
            <el-form-item label="仓库 URL" required>
              <el-input v-model="gitForm.url" placeholder="https://gitee.com/org/repo.git" />
            </el-form-item>
            <el-form-item label="分支">
              <el-input v-model="gitForm.branch" placeholder="留空用默认分支" />
            </el-form-item>
            <el-form-item label="项目名">
              <el-input v-model="gitForm.name" placeholder="留空自动推导" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="submitGit">开始导入</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="ZIP 上传">
          <el-form label-width="96px">
            <el-form-item label="ZIP 文件" required>
              <input type="file" accept=".zip" @change="onZipSelected" />
            </el-form-item>
            <el-form-item label="项目名">
              <el-input v-model="zipName" placeholder="留空用文件名" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="submitZip">上传并导入</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <el-descriptions v-if="lastResult" title="最近一次导入" :column="2" border class="mt">
        <el-descriptions-item label="项目 ID">{{ lastResult.projectId }}</el-descriptions-item>
        <el-descriptions-item label="任务 ID">{{ lastResult.taskId }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ lastResult.status }}</el-descriptions-item>
        <el-descriptions-item label="文件数">{{ lastResult.fileCount }}</el-descriptions-item>
        <el-descriptions-item label="分支">{{ lastResult.branch || '-' }}</el-descriptions-item>
        <el-descriptions-item label="HEAD">{{ lastResult.headCommit || '-' }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ lastResult.message }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  max-width: 880px;
  margin: 0 auto;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.mb {
  margin-bottom: 16px;
}
.mt {
  margin-top: 24px;
}
</style>
