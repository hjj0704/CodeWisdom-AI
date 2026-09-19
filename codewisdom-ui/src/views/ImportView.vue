<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Clock,
  Connection,
  Document,
  FolderOpened,
  Link,
  UploadFilled,
} from '@element-plus/icons-vue'
import { importFromGit, importFromZip, pingGateway, type ImportResult } from '../api/import'
import { fetchProjectList, type ProjectListItem } from '../api/project'
import { useAuthStore } from '../stores/auth'
import { useProjectStore } from '../stores/project'
import { loadAllProjectHealth, type ProjectHealthSnapshot } from '../utils/projectHealth'

const store = useProjectStore()
const auth = useAuthStore()
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
const zipInputRef = ref<HTMLInputElement | null>(null)
const zipDragActive = ref(false)
let zipDragDepth = 0
const lastResult = ref<ImportResult | null>(null)
const serverProjects = ref<ProjectListItem[]>([])
const listLoading = ref(false)
const projectHealthMap = ref<Map<number, ProjectHealthSnapshot>>(new Map())

function refreshProjectHealth() {
  projectHealthMap.value = loadAllProjectHealth()
}

function healthFor(projectId: number): ProjectHealthSnapshot | null {
  return projectHealthMap.value.get(projectId) ?? null
}

function formatHealthTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const diff = Date.now() - date.getTime()
  if (diff < 86_400_000) return '今天检查'
  if (diff < 172_800_000) return '昨天检查'
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }) + ' 检查'
}

function goLogin(reason: string) {
  ElMessage.info('请先登录后再导入项目')
  router.push({
    name: 'login',
    query: { redirect: '/import', reason },
  })
}

function requireLogin(reason: string): boolean {
  if (auth.isLoggedIn) return true
  goLogin(reason)
  return false
}

async function checkGateway(showToast = true) {
  try {
    const data = await pingGateway()
    gatewayOk.value = true
    if (showToast) ElMessage.success(`服务正常：${data}`)
  } catch (e) {
    gatewayOk.value = false
    if (showToast) {
      ElMessage.error(e instanceof Error ? e.message : '暂时连不上服务器，请稍后再试')
    }
  }
}

async function submitGit() {
  if (!requireLogin('import-git')) return
  if (!gitForm.url.trim()) {
    ElMessage.warning('请填写项目链接')
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

function isZipFile(file: File): boolean {
  const name = file.name.toLowerCase()
  if (name.endsWith('.zip')) return true
  const type = file.type.toLowerCase()
  return (
    type === 'application/zip' ||
    type === 'application/x-zip-compressed' ||
    type === 'application/x-zip'
  )
}

function setZipFile(file: File | null) {
  if (!file) {
    zipFile.value = null
    return false
  }
  if (!isZipFile(file)) {
    ElMessage.warning('请选择或拖入 .zip 压缩包（文件夹请先打成 zip）')
    return false
  }
  zipFile.value = file
  return true
}

function onZipSelected(event: Event) {
  const input = event.target as HTMLInputElement
  setZipFile(input.files?.[0] ?? null)
}

function onZipDragEnter(event: DragEvent) {
  event.preventDefault()
  event.stopPropagation()
  zipDragDepth++
  zipDragActive.value = true
}

function onZipDragOver(event: DragEvent) {
  event.preventDefault()
  event.stopPropagation()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy'
  }
}

function onZipDragLeave(event: DragEvent) {
  event.preventDefault()
  event.stopPropagation()
  zipDragDepth--
  if (zipDragDepth <= 0) {
    zipDragDepth = 0
    zipDragActive.value = false
  }
}

function onZipDrop(event: DragEvent) {
  event.preventDefault()
  event.stopPropagation()
  zipDragDepth = 0
  zipDragActive.value = false
  const file = event.dataTransfer?.files?.[0]
  if (!file) {
    ElMessage.warning('未检测到文件，请拖入单个 .zip 文件')
    return
  }
  if (!setZipFile(file)) {
    return
  }
  if (zipInputRef.value) {
    zipInputRef.value.value = ''
  }
}

function openZipPicker() {
  zipInputRef.value?.click()
}

function preventBrowserFileDrop(event: DragEvent) {
  event.preventDefault()
}

async function submitZip() {
  if (!requireLogin('import-zip')) return
  if (!zipFile.value) {
    ElMessage.warning('请选择 ZIP 压缩包')
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

function openMyProject(projectId: number) {
  if (!requireLogin('open-project')) return
  router.push(`/projects/${projectId}`)
}

function removeRecent(projectId: number) {
  store.removeRecent(projectId)
}

async function loadServerProjects() {
  if (!auth.isLoggedIn) {
    serverProjects.value = []
    return
  }
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

watch(
  () => auth.isLoggedIn,
  () => {
    loadServerProjects()
  },
)

onMounted(() => {
  checkGateway(false)
  refreshProjectHealth()
  loadServerProjects()
  window.addEventListener('dragover', preventBrowserFileDrop)
  window.addEventListener('drop', preventBrowserFileDrop)
})

onUnmounted(() => {
  window.removeEventListener('dragover', preventBrowserFileDrop)
  window.removeEventListener('drop', preventBrowserFileDrop)
})
</script>

<template>
  <div class="page">
    <header class="hero">
      <div class="hero__left">
        <span class="section-label">从这里开始</span>
        <h1 class="gradient-text">导入中心</h1>
        <p class="hero__desc">
          把代码放进平台里，就能在线查看、检查问题、尝试修改。你可以先浏览本页；真正导入时需要登录账号。
        </p>
      </div>
      <div class="hero__actions">
        <button
          type="button"
          class="status-pill"
          :class="{
            'status-pill--ok': gatewayOk === true,
            'status-pill--err': gatewayOk === false,
          }"
          @click="checkGateway(true)"
        >
          <span class="status-pill__dot" />
          {{ gatewayOk === true ? '服务正常' : gatewayOk === false ? '服务异常' : '检测服务' }}
        </button>
        <el-button v-if="!auth.isLoggedIn" type="primary" round @click="goLogin('hero')">登录后导入</el-button>
      </div>
    </header>

    <el-alert
      class="security-banner"
      type="info"
      :closable="false"
      show-icon
      title="隐私与安全说明"
      description="导入的代码仅保存在你的账号下，用于你在本平台的查看与分析。我们不会把代码用于训练对外模型，也不会擅自公开或转卖给第三方。"
    />

    <el-alert
      v-if="!auth.isLoggedIn"
      type="warning"
      show-icon
      :closable="false"
      title="尚未登录"
      description="你可以先了解页面功能。点击「开始导入」或「上传并导入」时，会引导你登录或注册。"
      class="alert-bar"
    />

    <el-alert
      v-if="gatewayOk === false"
      type="error"
      show-icon
      title="暂时连不上服务器，请稍后再试，或联系管理员。"
      class="alert-bar"
    />

    <section v-loading="listLoading" class="recent-section glass-panel" data-tour="my-projects">
      <div class="result-head result-head--row">
        <div class="result-head__main">
          <span class="section-label">我的项目</span>
          <h2>已导入的项目</h2>
          <p class="recent-hint">
            {{ auth.isLoggedIn ? '点卡片进入工作台继续编辑。' : '登录后显示你的项目列表。' }}
          </p>
        </div>
        <el-popover placement="bottom-end" :width="300" trigger="click" popper-class="local-history-popover">
          <template #reference>
            <el-button
              class="local-history-btn"
              circle
              :icon="Clock"
              :title="'本机以往编辑记录'"
              aria-label="本机以往编辑记录"
            />
          </template>
          <div class="local-history-pop">
            <p class="local-history-pop__title">本机以往编辑记录</p>
            <p class="local-history-pop__hint">仅保存在当前浏览器，不能直接打开；请在下方「我的项目」中进入。</p>
            <ul v-if="store.recentProjects.length" class="local-history-list">
              <li v-for="item in store.recentProjects" :key="item.projectId" class="local-history-item">
                <div class="local-history-item__text">
                  <span class="local-history-item__name">{{ item.name }}</span>
                  <span class="local-history-item__meta mono">#{{ item.projectId }} · {{ item.fileCount }} 文件</span>
                </div>
                <el-button link type="danger" size="small" @click="removeRecent(item.projectId)">移除</el-button>
              </li>
            </ul>
            <p v-else class="local-history-empty">暂无记录，打开过的工作台项目会出现在这里。</p>
          </div>
        </el-popover>
      </div>
      <div v-if="serverProjects.length" class="recent-grid">
        <div v-for="item in serverProjects" :key="item.id" class="recent-card">
          <button type="button" class="recent-card__main" @click="openMyProject(item.id)">
            <span class="recent-card__name">{{ item.name }}</span>
            <span class="recent-card__meta mono">
              #{{ item.id }} · {{ item.fileCount }} 文件 · {{ formatSize(item.totalSize) }}
            </span>
            <span v-if="healthFor(item.id)" class="recent-card__health">
              <span
                v-if="healthFor(item.id)!.overallScore != null"
                class="health-score"
              >
                评分 {{ healthFor(item.id)!.overallScore!.toFixed(1) }}
              </span>
              <span class="health-issues">
                <span v-if="healthFor(item.id)!.highCount" class="health-pill health-pill--high">
                  严重 {{ healthFor(item.id)!.highCount }}
                </span>
                <span v-if="healthFor(item.id)!.mediumCount" class="health-pill health-pill--medium">
                  中 {{ healthFor(item.id)!.mediumCount }}
                </span>
                <span v-if="healthFor(item.id)!.lowCount" class="health-pill health-pill--low">
                  低 {{ healthFor(item.id)!.lowCount }}
                </span>
                <span
                  v-if="!healthFor(item.id)!.highCount && !healthFor(item.id)!.mediumCount && !healthFor(item.id)!.lowCount"
                  class="health-pill health-pill--ok"
                >
                  无问题
                </span>
              </span>
              <span class="health-time">{{ formatHealthTime(healthFor(item.id)!.auditedAt) }}</span>
            </span>
            <span v-else class="recent-card__health recent-card__health--empty">尚未检查 · 进入后点「检查代码」</span>
          </button>
        </div>
      </div>
      <p v-else class="recent-empty">
        {{ auth.isLoggedIn ? '还没有项目，请在下方导入第一个。' : '请先登录，再导入你的第一个项目。' }}
      </p>
    </section>

    <div class="import-grid glass-panel" data-tour="import-git">
      <el-tabs class="import-tabs">
        <el-tab-pane>
          <template #label>
            <span class="tab-label"><el-icon><Link /></el-icon> 网上链接导入</span>
          </template>
          <el-form label-position="top" class="import-form" @submit.prevent="submitGit">
            <el-form-item label="项目链接（Git 地址）" required>
              <el-input
                v-model="gitForm.url"
                placeholder="例如 https://gitee.com/某人/某项目.git"
                size="large"
                :prefix-icon="Link"
              />
            </el-form-item>
            <div class="form-row">
              <el-form-item label="版本分支（可不填）">
                <el-input v-model="gitForm.branch" placeholder="不填则用默认分支" />
              </el-form-item>
              <el-form-item label="显示名称（可不填）">
                <el-input v-model="gitForm.name" placeholder="不填则从链接自动取名" />
              </el-form-item>
            </div>
            <el-form-item v-if="gitForm.url.trim()" label="下载选项">
              <div class="export-option">
                <el-switch v-model="gitForm.exportEnabled" />
                <span class="export-option__hint">
                  允许之后把改过的代码打包下载成 ZIP（关闭则只能在线看和改）
                </span>
              </div>
            </el-form-item>
            <el-button type="primary" size="large" :loading="loading" @click="submitGit">
              <el-icon class="btn-icon"><Connection /></el-icon>
              {{ auth.isLoggedIn ? '开始导入' : '登录并开始导入' }}
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane>
          <template #label>
            <span class="tab-label"><el-icon><UploadFilled /></el-icon> 压缩包上传</span>
          </template>
          <el-form label-position="top" class="import-form">
            <el-form-item label="选择 ZIP 压缩包" required>
              <div
                class="upload-zone"
                :class="{ 'upload-zone--active': zipDragActive }"
                role="button"
                tabindex="0"
                @click="openZipPicker"
                @keydown.enter.prevent="openZipPicker"
                @dragenter="onZipDragEnter"
                @dragover="onZipDragOver"
                @dragleave="onZipDragLeave"
                @drop="onZipDrop"
              >
                <input
                  ref="zipInputRef"
                  type="file"
                  accept=".zip,application/zip,application/x-zip-compressed"
                  class="upload-zone__input"
                  @change="onZipSelected"
                  @click.stop
                />
                <el-icon class="upload-zone__icon"><FolderOpened /></el-icon>
                <span class="upload-zone__title">
                  {{ zipFile ? zipFile.name : '点击或拖入 .zip 文件' }}
                </span>
                <span class="upload-zone__hint">会把压缩包解开并列出文件；请拖入 zip，不要直接拖文件夹</span>
              </div>
            </el-form-item>
            <el-form-item label="显示名称（可不填）">
              <el-input v-model="zipName" placeholder="不填则用压缩包文件名" />
            </el-form-item>
            <el-button type="primary" size="large" :loading="loading" @click="submitZip">
              <el-icon class="btn-icon"><UploadFilled /></el-icon>
              {{ auth.isLoggedIn ? '上传并导入' : '登录并上传导入' }}
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>

    <section v-if="lastResult" class="result-section glass-panel">
      <div class="result-head">
        <span class="section-label">导入完成</span>
        <h2>导入结果</h2>
      </div>
      <div class="metric-grid">
        <div class="metric-card">
          <span class="metric-card__label">项目编号</span>
          <span class="metric-card__value mono">{{ lastResult.projectId }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-card__label">状态</span>
          <span class="metric-card__value metric-card__value--accent">{{ lastResult.status }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-card__label">文件数量</span>
          <span class="metric-card__value">{{ lastResult.fileCount }}</span>
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
  padding: 8px 16px;
  border-radius: 999px;
  border: 1px solid var(--cw-border);
  background: var(--cw-bg-elevated);
  color: var(--cw-text-muted);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-shadow: var(--cw-shadow-sm);
}

.status-pill:hover {
  border-color: var(--cw-primary-border);
  box-shadow: var(--cw-shadow-md);
}

.status-pill__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--cw-text-muted);
}

.status-pill--ok {
  color: var(--cw-success);
  border-color: #b7eb8f;
  background: #f6ffed;
}

.status-pill--ok .status-pill__dot {
  background: var(--cw-success);
}

.status-pill--err {
  color: var(--cw-danger);
  border-color: rgba(248, 113, 113, 0.35);
}

.status-pill--err .status-pill__dot {
  background: var(--cw-danger);
}

.alert-bar,
.security-banner {
  margin: 0;
}

.security-banner :deep(.el-alert__description) {
  line-height: 1.65;
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
  padding: 40px 24px;
  border-radius: var(--cw-radius-sm);
  border: 2px dashed var(--cw-border-strong);
  background: var(--cw-surface-subtle);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, box-shadow 0.2s;
}

.upload-zone:hover,
.upload-zone--active {
  border-color: var(--cw-primary-border);
  background: var(--cw-primary-bg);
  box-shadow: var(--cw-shadow-sm);
}

.upload-zone--active {
  border-color: var(--cw-primary);
  background: var(--cw-primary-bg);
}

.upload-zone__input {
  display: none;
}

.upload-zone__icon {
  font-size: 40px;
  color: var(--cw-primary);
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
  background: var(--cw-surface-subtle);
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
  color: var(--cw-primary);
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
  background: var(--cw-primary-bg);
  border: 1px solid var(--cw-primary-border);
  font-size: 14px;
  color: var(--cw-text-secondary);
}

.recent-section {
  padding: 20px 24px 24px;
}

.result-head--row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.result-head__main {
  flex: 1;
  min-width: 0;
}

.local-history-btn {
  flex-shrink: 0;
  margin-top: 4px;
}

.local-history-pop__title {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
}

.local-history-pop__hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--cw-text-muted);
  line-height: 1.5;
}

.local-history-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.local-history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--cw-radius-sm);
  border: 1px dashed var(--cw-border);
  background: var(--cw-surface-subtle);
}

.local-history-item__text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.local-history-item__name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.local-history-item__meta {
  font-size: 11px;
  color: var(--cw-text-muted);
}

.local-history-empty {
  margin: 0;
  font-size: 13px;
  color: var(--cw-text-muted);
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
  padding: 12px 14px;
  border-radius: var(--cw-radius-sm);
  border: 1px solid var(--cw-border);
  background: var(--cw-bg-elevated);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.recent-card:hover {
  border-color: var(--cw-primary-border);
  box-shadow: var(--cw-shadow-sm);
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

.recent-card__health {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  width: 100%;
}

.recent-card__health--empty {
  font-size: 11px;
  color: var(--cw-text-muted);
}

.health-score {
  font-size: 12px;
  font-weight: 700;
  color: var(--cw-primary);
}

.health-issues {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.health-pill {
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
}

.health-pill--high {
  background: var(--cw-risk-high-bg);
  color: var(--cw-risk-high);
}

.health-pill--medium {
  background: var(--cw-risk-medium-bg);
  color: var(--cw-risk-medium);
}

.health-pill--low {
  background: var(--cw-risk-low-bg);
  color: var(--cw-risk-low);
}

.health-pill--ok {
  background: var(--cw-diff-add-bg);
  color: var(--cw-diff-add);
}

.health-time {
  font-size: 10px;
  color: var(--cw-text-muted);
}

.recent-empty {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--cw-text-muted);
}
</style>
