<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Connection,
  Document,
  FolderOpened,
  Link,
  UploadFilled,
} from '@element-plus/icons-vue'
import { importFromGit, importFromZip, pingGateway, type ImportResult } from '../api/import'
import { fetchProjectList, type ProjectListItem } from '../api/project'
import { useProjectStore } from '../stores/project'

const store = useProjectStore()
const router = useRouter()
const loading = ref(false)
const gatewayOk = ref<boolean | null>(null)

const gitForm = reactive({
  url: '',
  branch: '',
  name: '',
  exportEnabled: true,
})

const zipName = ref('')
const zipFile = ref<File | null>(null)
const lastResult = ref<ImportResult | null>(null)
const serverProjects = ref<ProjectListItem[]>([])
const listLoading = ref(false)

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
      exportEnabled: gitForm.exportEnabled,
    })
    lastResult.value = result
    const displayName = gitForm.name.trim() || deriveNameFromUrl(gitForm.url)
    store.setImportResult(result, displayName)
    ElMessage.success(`导入成功，项目 ID=${result.projectId}`)
    await loadServerProjects()
    await router.push(`/projects/${result.projectId}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导入失败')
  } finally {
    loading.value = false
  }
}

function deriveNameFromUrl(url: string) {
  const cleaned = url.trim().replace(/\.git$/i, '')
  const seg = cleaned.split('/').filter(Boolean).pop()
  return seg || `项目 #${lastResult.value?.projectId ?? ''}`
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
    const displayName = zipName.value.trim() || zipFile.value.name.replace(/\.zip$/i, '')
    store.setImportResult(result, displayName)
    ElMessage.success(`导入成功，项目 ID=${result.projectId}`)
    await loadServerProjects()
    await router.push(`/projects/${result.projectId}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导入失败')
  } finally {
    loading.value = false
  }
}

function openRecent(projectId: number) {
  router.push(`/projects/${projectId}`)
}

function removeRecent(projectId: number) {
  store.removeRecent(projectId)
}

async function loadServerProjects() {
  listLoading.value = true
  try {
    serverProjects.value = await fetchProjectList()
  } catch {
    serverProjects.value = []
  } finally {
    listLoading.value = false
  }
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

onMounted(() => {
  checkGateway()
  loadServerProjects()
})
</script>

<template>
  <div class="page">
    <header class="hero">
      <div class="hero__left">
        <span class="section-label">Project Import</span>
        <h1 class="gradient-text">项目导入中心</h1>
        <p class="hero__desc">从 Git 仓库或 ZIP 压缩包导入代码，自动扫描文件树并接入后续分析管线。</p>
      </div>
      <div class="hero__actions">
        <button
          type="button"
          class="status-pill"
          :class="{
            'status-pill--ok': gatewayOk === true,
            'status-pill--err': gatewayOk === false,
          }"
          @click="checkGateway"
        >
          <span class="status-pill__dot" />
          {{ gatewayOk === true ? '网关在线' : gatewayOk === false ? '网关离线' : '检测网关' }}
        </button>
      </div>
    </header>

    <el-alert
      v-if="gatewayOk === false"
      type="error"
      show-icon
      title="无法连接后端 API。请确认服务已启动，或联系管理员检查网关与 Nginx 反代配置。"
      class="alert-bar"
    />

    <section v-loading="listLoading" class="recent-section glass-panel" data-tour="my-projects">
      <div class="result-head">
        <span class="section-label">My Projects</span>
        <h2>我的项目</h2>
        <p class="recent-hint">账号下已导入且就绪的项目，可在任意设备登录后访问。</p>
      </div>
      <div v-if="serverProjects.length" class="recent-grid">
        <div v-for="item in serverProjects" :key="item.id" class="recent-card">
          <button type="button" class="recent-card__main" @click="openRecent(item.id)">
            <span class="recent-card__name">{{ item.name }}</span>
            <span class="recent-card__meta mono">
              #{{ item.id }} · {{ item.fileCount }} 文件 · {{ formatSize(item.totalSize) }}
            </span>
          </button>
        </div>
      </div>
      <p v-else class="recent-empty">暂无项目，请先完成一次导入。</p>
    </section>

    <section v-if="store.recentProjects.length" class="recent-section glass-panel">
      <div class="result-head">
        <span class="section-label">Recent Projects</span>
        <h2>最近打开的项目</h2>
        <p class="recent-hint">记录保存在本浏览器，换设备或清空缓存后需重新导入。</p>
      </div>
      <div class="recent-grid">
        <div v-for="item in store.recentProjects" :key="item.projectId" class="recent-card">
          <button type="button" class="recent-card__main" @click="openRecent(item.projectId)">
            <span class="recent-card__name">{{ item.name }}</span>
            <span class="recent-card__meta mono">#{{ item.projectId }} · {{ item.fileCount }} 文件</span>
          </button>
          <el-button link type="danger" size="small" @click="removeRecent(item.projectId)">移除</el-button>
        </div>
      </div>
    </section>

    <div class="import-grid glass-panel" data-tour="import-git">
      <el-tabs class="import-tabs">
        <el-tab-pane>
          <template #label>
            <span class="tab-label"><el-icon><Link /></el-icon> Git 仓库</span>
          </template>
          <el-form label-position="top" class="import-form" @submit.prevent="submitGit">
            <el-form-item label="仓库 URL" required>
              <el-input
                v-model="gitForm.url"
                placeholder="https://gitee.com/org/repo.git"
                size="large"
                :prefix-icon="Link"
              />
            </el-form-item>
            <div class="form-row">
              <el-form-item label="分支">
                <el-input v-model="gitForm.branch" placeholder="留空用默认分支" />
              </el-form-item>
              <el-form-item label="项目名">
                <el-input v-model="gitForm.name" placeholder="留空自动推导" />
              </el-form-item>
            </div>
            <el-form-item v-if="gitForm.url.trim()" label="导出选项">
              <div class="export-option">
                <el-switch v-model="gitForm.exportEnabled" />
                <span class="export-option__hint">
                  允许在工作台下载修改后的 ZIP（未开启则仅在线查看与编辑）
                </span>
              </div>
            </el-form-item>
            <el-button type="primary" size="large" :loading="loading" @click="submitGit">
              <el-icon class="btn-icon"><Connection /></el-icon>
              开始克隆导入
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane>
          <template #label>
            <span class="tab-label"><el-icon><UploadFilled /></el-icon> ZIP 上传</span>
          </template>
          <el-form label-position="top" class="import-form">
            <el-form-item label="ZIP 文件" required>
              <label class="upload-zone">
                <input type="file" accept=".zip" class="upload-zone__input" @change="onZipSelected" />
                <el-icon class="upload-zone__icon"><FolderOpened /></el-icon>
                <span class="upload-zone__title">
                  {{ zipFile ? zipFile.name : '点击或拖拽 ZIP 到此处' }}
                </span>
                <span class="upload-zone__hint">支持 .zip，自动解压并扫描文件树</span>
              </label>
            </el-form-item>
            <el-form-item label="项目名">
              <el-input v-model="zipName" placeholder="留空用文件名" />
            </el-form-item>
            <el-button type="primary" size="large" :loading="loading" @click="submitZip">
              <el-icon class="btn-icon"><UploadFilled /></el-icon>
              上传并导入
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>

    <section v-if="lastResult" class="result-section glass-panel">
      <div class="result-head">
        <span class="section-label">Latest Import</span>
        <h2>导入结果</h2>
      </div>
      <div class="metric-grid">
        <div class="metric-card">
          <span class="metric-card__label">项目 ID</span>
          <span class="metric-card__value mono">{{ lastResult.projectId }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-card__label">任务 ID</span>
          <span class="metric-card__value mono">{{ lastResult.taskId }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-card__label">状态</span>
          <span class="metric-card__value metric-card__value--accent">{{ lastResult.status }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-card__label">文件数</span>
          <span class="metric-card__value">{{ lastResult.fileCount }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-card__label">分支</span>
          <span class="metric-card__value mono">{{ lastResult.branch || '—' }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-card__label">HEAD</span>
          <span class="metric-card__value mono metric-card__value--sm">{{ lastResult.headCommit || '—' }}</span>
        </div>
      </div>
      <div v-if="lastResult.message" class="result-message">
        <el-icon><Document /></el-icon>
        {{ lastResult.message }}
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}

.hero__left {
  flex: 1;
  min-width: 280px;
}

.hero h1 {
  margin: 12px 0 12px;
  font-size: clamp(26px, 4vw, 34px);
  font-weight: 700;
}

.hero__desc {
  margin: 0;
  max-width: 520px;
  color: var(--cw-text-muted);
  font-size: 15px;
  line-height: 1.7;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  border-radius: 999px;
  border: 1px solid var(--cw-border);
  background: rgba(15, 23, 42, 0.6);
  color: var(--cw-text-muted);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.status-pill:hover {
  border-color: var(--cw-border-strong);
  box-shadow: 0 0 20px rgba(34, 211, 238, 0.1);
}

.status-pill__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--cw-text-muted);
}

.status-pill--ok {
  color: var(--cw-success);
  border-color: rgba(52, 211, 153, 0.35);
}

.status-pill--ok .status-pill__dot {
  background: var(--cw-success);
  box-shadow: 0 0 8px rgba(52, 211, 153, 0.6);
}

.status-pill--err {
  color: var(--cw-danger);
  border-color: rgba(248, 113, 113, 0.35);
}

.status-pill--err .status-pill__dot {
  background: var(--cw-danger);
}

.alert-bar {
  margin: 0;
}

.import-grid {
  padding: 8px 24px 28px;
}

.import-tabs :deep(.el-tabs__header) {
  margin-bottom: 24px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.import-form {
  max-width: 640px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.export-option {
  display: flex;
  align-items: center;
  gap: 12px;
}

.export-option__hint {
  font-size: 13px;
  color: var(--cw-text-muted);
  line-height: 1.5;
}

@media (max-width: 600px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}

.upload-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 36px 24px;
  border-radius: var(--cw-radius-sm);
  border: 2px dashed var(--cw-border);
  background: rgba(15, 23, 42, 0.4);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, box-shadow 0.2s;
}

.upload-zone:hover {
  border-color: var(--cw-border-strong);
  background: rgba(34, 211, 238, 0.05);
  box-shadow: 0 0 24px rgba(34, 211, 238, 0.08);
}

.upload-zone__input {
  display: none;
}

.upload-zone__icon {
  font-size: 36px;
  color: var(--cw-accent);
}

.upload-zone__title {
  font-weight: 600;
  font-size: 14px;
}

.upload-zone__hint {
  font-size: 12px;
  color: var(--cw-text-muted);
}

.btn-icon {
  margin-right: 6px;
}

.result-section {
  padding: 24px 28px 28px;
}

.result-head h2 {
  margin: 10px 0 0;
  font-size: 18px;
  font-weight: 600;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 14px;
  margin-top: 20px;
}

.metric-card {
  padding: 16px;
  border-radius: var(--cw-radius-sm);
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid var(--cw-border);
}

.metric-card__label {
  display: block;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--cw-text-muted);
  margin-bottom: 8px;
}

.metric-card__value {
  font-size: 20px;
  font-weight: 700;
  color: var(--cw-text);
}

.metric-card__value--accent {
  color: var(--cw-accent);
}

.metric-card__value--sm {
  font-size: 12px;
  word-break: break-all;
}

.result-message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 20px;
  padding: 14px 16px;
  border-radius: var(--cw-radius-sm);
  background: rgba(34, 211, 238, 0.06);
  border: 1px solid var(--cw-border);
  font-size: 14px;
  color: var(--cw-text-muted);
}

.recent-section {
  padding: 20px 24px 24px;
}

.recent-hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--cw-text-muted);
}

.recent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.recent-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--cw-radius-sm);
  border: 1px solid var(--cw-border);
  background: rgba(15, 23, 42, 0.55);
}

.recent-card__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
  padding: 0;
}

.recent-card__name {
  font-weight: 600;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.recent-card__meta {
  font-size: 11px;
  color: var(--cw-text-muted);
}

.recent-empty {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--cw-text-muted);
}
</style>
