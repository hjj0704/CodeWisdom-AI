<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, onUnmounted, ref, watch } from 'vue'

defineOptions({ name: 'ProjectWorkbench' })
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  DArrowLeft,
  DArrowRight,
  Download,
  FolderOpened,
  Refresh,
  Search,
} from '@element-plus/icons-vue'
import CodeEditor from '../components/CodeEditor.vue'
import {
  downloadProjectZip,
  fetchFileContent,
  fetchProjectMeta,
  fetchProjectStats,
  fetchProjectTree,
  saveFileContent,
  toTreeData,
  type TreeNode,
} from '../api/project'
import {
  createSession,
  deleteSession,
  listMessages,
  listSessions,
  onboardProject,
  sendChat,
  type AgentAction,
  type ChatMessage,
  type ChatSession,
  type ClarifyOption,
  type WorkbenchContext,
} from '../api/chat'
import { fetchProjectScore, runProjectAudit, type AuditIssueItem, type RiskLevel } from '../api/audit'
import { fetchProjectArchitecture } from '../api/architecture'
import {
  discoverDemoLink,
  fetchRunProfile,
  runSandboxDemo,
  validateSandboxCommand,
  type DemoLinkResult,
  type RunProfile,
  type SandboxCheckResult,
  type SandboxRunResult,
} from '../api/runProfile'
import { fetchEvalReport, type EvalReport } from '../api/evalReport'
import mermaid from 'mermaid'
import {
  fetchHitlState,
  listFixRecords,
  previewDiff,
  submitHitl,
  suggestFixes,
  type FixRecord,
  type HitlState,
  type StructuredDiff,
} from '../api/fix'
import { evalStatusLabel, hitlStatusLabel, runCapabilityLabel } from '../utils/labels'
import { applyCodeAtLine, extractSuggestedCode, tryApplySuggestion } from '../utils/fixApply'
import {
  auditIssueKey,
  formatAuditPath,
  isDependencyConfigIssue,
  isIgnorableAuditIssue,
  normalizeAuditIssueKey,
  shouldCollapseAuditText,
} from '../utils/audit'
import { riskIssueClass, riskLevelLabel, riskLevelShort, riskTagType } from '../utils/riskLevel'
import type { IssueLineMark } from '../components/CodeEditor.vue'
import InteractiveTour from '../components/InteractiveTour.vue'
import TaskProgressBar from '../components/TaskProgressBar.vue'
import { loadProjectHealth, saveProjectHealth } from '../utils/projectHealth'
import {
  diffAuditHistory,
  formatHistoryTime,
  listAuditHistory,
  pushAuditHistory,
  type AuditHistoryDiff,
} from '../utils/auditHistory'
import { workbenchTourSteps } from '../data/onboardingSteps'
import { completeWorkbenchTour, isWorkbenchTourPending } from '../utils/onboarding'
import { useProjectStore } from '../stores/project'
import { useSettingsStore } from '../stores/settings'
import type { DocGenScopeMode } from '../utils/javaDocExtract'
import { runJavadocForScope } from '../utils/workbenchJavadoc'
import { collectJavaFilePaths } from '../utils/javaFileIndex'
import { locateJavaSymbol } from '../utils/symbolNavigate'

const LAYOUT_STORAGE_KEY = 'codewisdom.workbench.layout'

interface WorkbenchLayoutPrefs {
  treeWidth: number
  chatWidth: number
  treeCollapsed: boolean
  hintCollapsed: boolean
}

function readLayoutPrefs(): Partial<WorkbenchLayoutPrefs> {
  try {
    return JSON.parse(localStorage.getItem(LAYOUT_STORAGE_KEY) ?? '{}') as Partial<WorkbenchLayoutPrefs>
  } catch {
    return {}
  }
}

function saveLayoutPrefs(prefs: WorkbenchLayoutPrefs) {
  localStorage.setItem(LAYOUT_STORAGE_KEY, JSON.stringify(prefs))
}

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const settingsStore = useSettingsStore()
/** 固定项目 ID，避免 KeepAlive 切到导入页后 route 无 projectId 导致 NaN 请求 */
const projectId = ref(0)

function routeProjectId(): number | null {
  if (route.name !== 'project-workbench') return null
  const id = Number(route.params.projectId)
  return Number.isFinite(id) && id > 0 ? id : null
}

const loading = ref(true)
const projectName = ref('')
const sourceUrl = ref<string | null>(null)
const exportEnabled = ref(true)
const statsText = ref('')
const statFileCount = ref(0)
const statTotalSize = ref(0)
const statMaxDepth = ref(0)
const treeData = ref<TreeNode[]>([])
const treeFilter = ref('')
const currentPath = ref('')
const savedContent = ref('')
const editorContent = ref('')
const fileLanguage = ref<string | null>(null)
const fileBinary = ref(false)
const saveLoading = ref(false)

const fixRecords = ref<FixRecord[]>([])
const activeFixId = ref<number | null>(null)
const dismissedFixIds = ref<Set<number>>(new Set())
const fixLoading = ref(false)
const diffLoading = ref(false)
const diffPreview = ref<StructuredDiff | null>(null)
const hitlState = ref<HitlState | null>(null)
const hitlLoading = ref(false)

const sessions = ref<ChatSession[]>([])
const activeSessionId = ref<number | null>(null)
const messages = ref<ChatMessage[]>([])
const chatInput = ref('')
const chatAttachFile = ref(false)
const chatAttachIssue = ref<AuditIssueItem | null>(null)
const chatLoading = ref(false)
const chatBox = ref<HTMLElement | null>(null)
const pendingAiFixByMessageId = ref<Map<number, AuditIssueItem>>(new Map())
const pendingAgentActionsByMessageId = ref<Map<number, AgentAction[]>>(new Map())
const fixStepApplied = ref(false)

const auditIssues = ref<AuditIssueItem[]>([])
const auditScanNote = ref('')
const auditScanTruncated = ref(false)
const auditRefreshing = ref(false)
let diffPreviewTimer: ReturnType<typeof setTimeout> | null = null
const auditRiskFilter = ref<'ALL' | RiskLevel>('ALL')
const hideIgnorableCatch = ref(true)
const hideDependencyIssues = ref(true)
const ignoredIssueKeys = ref<string[]>([])
const expandedIssueKeys = ref<Set<string>>(new Set())
const activeIssueKey = ref<string | null>(null)
const activeFocusLine = ref<number | null>(null)
const pendingJumpLine = ref<number | null>(null)
const auditLoading = ref(false)
const scoreLoading = ref(false)
const scoreSummary = ref('')
const scoreOverall = ref<number | null>(null)

const archLoading = ref(false)
const archTab = ref<'layer' | 'package'>('layer')
const archLayerDiagram = ref('')
const archPackageDiagram = ref('')
const archSvg = ref('')
const archStats = ref('')

const runProfileLoading = ref(false)
const runProfile = ref<RunProfile | null>(null)
const sandboxCommand = ref('mvn -q test')
const sandboxLoading = ref(false)
const sandboxResult = ref<SandboxCheckResult | null>(null)

const evalReportLoading = ref(false)
const evalReport = ref<EvalReport | null>(null)
const runPanelCollapsed = ref(false)
const evalPanelCollapsed = ref(false)
const sandboxRunLoading = ref(false)
const sandboxRunOutput = ref<SandboxRunResult | null>(null)
const demoLinkHint = ref<DemoLinkResult | null>(null)

const isNarrow = ref(false)
const mobilePane = ref<'tree' | 'editor' | 'chat'>('editor')
const archZoom = ref(1)
const showChatPanel = ref(false)
const editorHeight = ref(420)
const showWorkbenchTour = ref(false)
const workbenchTourTriggered = ref(false)
const savedLayout = readLayoutPrefs()
const treeRef = ref<{ setCurrentKey?: (key: string) => void } | null>(null)
const codeEditorRef = ref<{
  scrollToLine?: (line: number) => void
  getSelectionRange?: () => { startLine: number; endLine: number; text: string } | null
  getVisibleLineRange?: () => { startLine: number; endLine: number } | null
} | null>(null)
const treeCollapsed = ref(savedLayout.treeCollapsed ?? false)
const archCollapsed = ref(false)
const detailCollapsed = ref(false)
const hintCollapsed = ref(savedLayout.hintCollapsed ?? false)
const treeWidth = ref(savedLayout.treeWidth ?? 280)
const chatWidth = ref(savedLayout.chatWidth ?? 380)

const treeAsideStyle = computed(() => {
  if (isNarrow.value) return undefined
  const width = treeCollapsed.value ? 52 : treeWidth.value
  return { width: `${width}px`, flex: `0 0 ${width}px`, minWidth: `${width}px` }
})

const chatAsideStyle = computed(() => {
  if (isNarrow.value || !showChatPanel.value) return undefined
  return { width: `${chatWidth.value}px`, flex: `0 0 ${chatWidth.value}px` }
})

watch([treeWidth, chatWidth, treeCollapsed, hintCollapsed], () => {
  saveLayoutPrefs({
    treeWidth: treeWidth.value,
    chatWidth: chatWidth.value,
    treeCollapsed: treeCollapsed.value,
    hintCollapsed: hintCollapsed.value,
  })
})

function isIssueIgnored(issue: AuditIssueItem): boolean {
  return ignoredIssueKeys.value.includes(auditIssueKey(issue))
}

const visibleAuditIssues = computed(() => {
  return auditIssues.value.filter((issue) => {
    if (isIssueIgnored(issue)) return false
    if (hideIgnorableCatch.value && isIgnorableAuditIssue(issue)) return false
    if (hideDependencyIssues.value && isDependencyConfigIssue(issue)) return false
    return true
  })
})

const hiddenDependencyCount = computed(() =>
  auditIssues.value.filter((i) => !isIssueIgnored(i) && isDependencyConfigIssue(i)).length,
)

const treeIssueStats = computed(() => {
  const counts: Record<string, number> = {}
  const risks: Record<string, RiskLevel> = {}
  for (const issue of visibleAuditIssues.value) {
    const segments = issue.filePath.split('/')
    for (let i = segments.length; i >= 1; i--) {
      const path = segments.slice(0, i).join('/')
      counts[path] = (counts[path] ?? 0) + 1
      const cur = risks[path]
      if (!cur || riskWeight(issue.riskLevel) > riskWeight(cur)) {
        risks[path] = issue.riskLevel
      }
    }
  }
  return { counts, risks }
})

const maxTreeIssueCount = computed(() => {
  const vals = Object.values(treeIssueStats.value.counts)
  return vals.length ? Math.max(...vals) : 1
})

const issueLineMarks = computed((): IssueLineMark[] => {
  if (!currentPath.value || fileBinary.value) return []
  const maxLine = Math.max(1, editorContent.value.split('\n').length)
  return visibleAuditIssues.value
    .filter((i) => {
      if (i.filePath !== currentPath.value || i.line < 1 || i.line > maxLine) return false
      if (hideDependencyIssues.value && isDependencyConfigIssue(i)) return false
      return true
    })
    .map((i) => ({ line: i.line, risk: i.riskLevel }))
})

const worstRiskByPath = computed(() => treeIssueStats.value.risks)

const pathBreadcrumbs = computed(() => (currentPath.value ? currentPath.value.split('/') : []))

const statusBarChips = computed(() => {
  const chips: Array<{ key: string; label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = []
  if (auditLoading.value) chips.push({ key: 'audit', label: '检查代码中…', type: 'info' })
  if (fixLoading.value) chips.push({ key: 'fix', label: '生成修复建议中…', type: 'info' })
  if (diffLoading.value) chips.push({ key: 'diff', label: '对比修改中…', type: 'info' })
  if (saveLoading.value) chips.push({ key: 'save', label: '保存中…', type: 'info' })
  if (fileDirty.value) chips.push({ key: 'dirty', label: '未保存', type: 'warning' })
  if (visibleAuditIssues.value.length) {
    const high = visibleAuditIssues.value.filter((i) => i.riskLevel === 'HIGH').length
    chips.push({
      key: 'issues',
      label: high
        ? `${visibleAuditIssues.value.length} 问题 · ${high} 严重`
        : `${visibleAuditIssues.value.length} 个问题`,
      type: high > 0 ? 'danger' : 'warning',
    })
  } else if (auditIssues.value.length > 0) {
    chips.push({ key: 'filtered', label: '可见问题已过滤', type: 'success' })
  }
  if (hitlState.value?.lastStatus === 'REJECTED') {
    chips.push({ key: 'hitl', label: '修复已驳回', type: 'warning' })
  }
  return chips
})

const lastActivityLabel = ref('')
const lastActivityAt = ref<Date | null>(null)

const taskProgress = ref({
  visible: false,
  title: '',
  percent: 0,
  stage: '',
})
let taskProgressTimer: ReturnType<typeof setInterval> | null = null

const AUDIT_PROGRESS_STAGES = ['准备扫描环境', '解析 Java 源码', '执行规则检查', '汇总问题报告']
const FIX_PROGRESS_STAGES = ['分析问题上下文', '匹配修复模板', '生成改法建议', '整理输出结果']

function touchActivity(label: string) {
  lastActivityLabel.value = label
  lastActivityAt.value = new Date()
}

function formatActivityTime(date: Date): string {
  const diff = Date.now() - date.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function treeIssueCount(path: string): number {
  return treeIssueStats.value.counts[path] ?? 0
}

function treeDensityWidth(path: string): string {
  const count = treeIssueCount(path)
  if (!count) return '0%'
  const pct = Math.round((count / maxTreeIssueCount.value) * 100)
  return `${Math.max(18, Math.min(100, pct))}%`
}

function stopTaskProgressTimer() {
  if (taskProgressTimer) {
    clearInterval(taskProgressTimer)
    taskProgressTimer = null
  }
}

function startTaskProgress(title: string, stages: string[]) {
  stopTaskProgressTimer()
  taskProgress.value = {
    visible: true,
    title,
    percent: 5,
    stage: stages[0] ?? '处理中…',
  }
  taskProgressTimer = setInterval(() => {
    if (taskProgress.value.percent < 92) {
      taskProgress.value.percent = Math.min(92, taskProgress.value.percent + 2 + Math.random() * 4)
      const idx = Math.min(stages.length - 1, Math.floor((taskProgress.value.percent / 94) * stages.length))
      taskProgress.value.stage = stages[idx] ?? taskProgress.value.stage
    }
  }, 650)
}

function finishTaskProgress(ok = true) {
  stopTaskProgressTimer()
  taskProgress.value.percent = ok ? 100 : taskProgress.value.percent
  taskProgress.value.stage = ok ? '完成' : '已中断'
  window.setTimeout(() => {
    taskProgress.value.visible = false
    taskProgress.value.percent = 0
    taskProgress.value.stage = ''
  }, ok ? 900 : 1200)
}

function persistProjectHealthFromAudit(report: {
  total: number
  highCount: number
  mediumCount: number
  lowCount: number
}) {
  saveProjectHealth({
    projectId: projectId.value,
    totalIssues: report.total,
    highCount: report.highCount,
    mediumCount: report.mediumCount,
    lowCount: report.lowCount,
    overallScore: scoreOverall.value ?? undefined,
    auditedAt: new Date().toISOString(),
  })
}

const fixWorkflowSteps = computed(() => {
  const hasAudit = auditIssues.value.length > 0
  const hasSuggest = fixRecords.value.length > 0
  const hasEdit = fileDirty.value
  const hasDiff = !!diffPreview.value?.changed
  const hitlDone =
    hitlState.value?.lastStatus === 'APPROVED' ||
    hitlState.value?.lastStatus === 'MODIFIED' ||
    hitlState.value?.terminated
  return [
    { key: 'audit', label: '① 检查代码', done: hasAudit },
    { key: 'suggest', label: '② 获取改法建议', done: hasSuggest },
    { key: 'apply', label: '③ 应用或让 AI 改', done: hasEdit },
    { key: 'diff', label: '④ 对比修改', done: hasDiff },
    { key: 'hitl', label: '⑤ 保存并确认', done: hitlDone },
  ]
})

const fixWorkflowCurrentKey = computed(() => {
  const steps = fixWorkflowSteps.value
  const pending = steps.find((s) => !s.done)
  return pending?.key ?? steps[steps.length - 1]?.key
})

const fileDirty = computed(
  () => !fileBinary.value && !!currentPath.value && editorContent.value !== savedContent.value,
)

const filteredAuditIssues = computed(() => {
  const base = visibleAuditIssues.value
  if (auditRiskFilter.value === 'ALL') return base
  return base.filter((i) => i.riskLevel === auditRiskFilter.value)
})

const fixByIssueKey = computed(() => {
  const map = new Map<string, FixRecord>()
  for (const record of visibleFixRecords.value) {
    map.set(record.issueKey, record)
  }
  return map
})

const hitlSessionKey = computed(() => {
  if (activeFixId.value == null) return undefined
  return `project-${projectId.value}-fix-${activeFixId.value}`
})

const fixBarFixRecord = computed(() => {
  const record = activeFixRecord.value
  if (!record || record.filePath !== currentPath.value) return null
  return record
})

const ignorableCatchCount = computed(() =>
  auditIssues.value.filter((i) => isIgnorableAuditIssue(i) && !isIssueIgnored(i)).length,
)

const suggestibleIssues = computed(() => {
  const pool = auditRiskFilter.value === 'ALL' ? visibleAuditIssues.value : filteredAuditIssues.value
  return pool.filter((issue) => !fixByIssueKey.value.has(auditIssueKey(issue)))
})

const suggestibleCount = computed(() => suggestibleIssues.value.length)

const batchFixRuleGroups = computed(() => {
  const map = new Map<string, AuditIssueItem[]>()
  for (const issue of suggestibleIssues.value) {
    const list = map.get(issue.ruleId) ?? []
    list.push(issue)
    map.set(issue.ruleId, list)
  }
  return [...map.entries()]
    .filter(([, issues]) => issues.length >= 2)
    .sort((a, b) => b[1].length - a[1].length)
})

const auditHistoryVisible = ref(false)
const docGenLoading = ref(false)
const auditHistoryEntries = computed(() => listAuditHistory(projectId.value))

const isJavaFile = computed(
  () => !!currentPath.value && /\.java$/i.test(currentPath.value) && !fileBinary.value,
)

const auditHistoryCompare = computed((): AuditHistoryDiff | null => {
  const list = auditHistoryEntries.value
  if (list.length < 2) return null
  return diffAuditHistory(list[1], list[0].issueKeys)
})

const showLiveDiffPanel = computed(
  () => !!currentPath.value && fileDirty.value && !fileBinary.value,
)

function isIssueExpanded(key: string): boolean {
  return expandedIssueKeys.value.has(key)
}

function toggleIssueExpanded(key: string) {
  const next = new Set(expandedIssueKeys.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expandedIssueKeys.value = next
}

function issueNeedsCollapse(issue: AuditIssueItem): boolean {
  return (
    shouldCollapseAuditText(issue.description, 88) ||
    shouldCollapseAuditText(issue.filePath, 40) ||
    shouldCollapseAuditText(issue.triggerSnippet ?? '', 100)
  )
}
const filteredTree = computed(() => {
  if (!treeFilter.value.trim()) return treeData.value
  const kw = treeFilter.value.trim().toLowerCase()
  function match(nodes: TreeNode[]): TreeNode[] {
    return nodes
      .map((n) => {
        const children = n.children ? match(n.children) : []
        if (n.label.toLowerCase().includes(kw) || children.length) {
          return { ...n, children: children.length ? children : n.children }
        }
        return null
      })
      .filter(Boolean) as TreeNode[]
  }
  return match(treeData.value)
})

const visibleFixRecords = computed(() =>
  fixRecords.value.filter((r) => !dismissedFixIds.value.has(r.id)),
)

const activeFixRecord = computed(
  () => visibleFixRecords.value.find((r) => r.id === activeFixId.value) ?? null,
)

const fixHighlightLine = computed(() => {
  if (!activeFixRecord.value || activeFixRecord.value.filePath !== currentPath.value) return null
  return activeFixRecord.value.lineNo > 0 ? activeFixRecord.value.lineNo : null
})

function dismissedStorageKey() {
  return `cw_dismissed_fix_${projectId.value}`
}

function loadDismissedFixes() {
  try {
    const raw = localStorage.getItem(dismissedStorageKey())
    if (!raw) return
    dismissedFixIds.value = new Set(JSON.parse(raw) as number[])
  } catch {
    dismissedFixIds.value = new Set()
  }
}

function persistDismissedFixes() {
  localStorage.setItem(dismissedStorageKey(), JSON.stringify([...dismissedFixIds.value]))
}

function ignoredIssuesStorageKey() {
  return `cw_ignored_audit_${projectId.value}`
}

function loadIgnoredIssues() {
  try {
    const raw = localStorage.getItem(ignoredIssuesStorageKey())
    if (!raw) return
    const parsed = JSON.parse(raw) as string[]
    ignoredIssueKeys.value = Array.isArray(parsed) ? parsed.map(normalizeAuditIssueKey) : []
  } catch {
    ignoredIssueKeys.value = []
  }
}

function persistIgnoredIssues() {
  localStorage.setItem(ignoredIssuesStorageKey(), JSON.stringify(ignoredIssueKeys.value))
}

function ignoreIssuesAtLine(line: number) {
  const issues = auditIssues.value.filter(
    (i) => i.filePath === currentPath.value && i.line === line && !isIssueIgnored(i),
  )
  if (!issues.length) {
    ElMessage.info('此行没有可忽略的问题')
    return
  }
  for (const issue of issues) {
    ignoreIssue(issue, false)
  }
  ElMessage.success(`已忽略第 ${line} 行的 ${issues.length} 个问题`)
}

function ignoreIssue(issue: AuditIssueItem, showToast = true) {
  const key = auditIssueKey(issue)
  if (!ignoredIssueKeys.value.includes(key)) {
    ignoredIssueKeys.value = [...ignoredIssueKeys.value, key]
  }
  persistIgnoredIssues()
  const fix = fixRecords.value.find((r) => r.issueKey === key)
  if (fix) {
    dismissedFixIds.value = new Set([...dismissedFixIds.value, fix.id])
    persistDismissedFixes()
    if (activeFixId.value === fix.id) {
      activeFixId.value = null
    }
  }
  if (activeIssueKey.value === key) {
    activeIssueKey.value = null
    activeFocusLine.value = null
  }
  if (showToast) ElMessage.success('已忽略该问题')
}

function clearIgnoredIssues() {
  ignoredIssueKeys.value = []
  persistIgnoredIssues()
  ElMessage.success('已恢复显示被忽略的问题')
}

function ignoreAllCatchIssues() {
  const keys = auditIssues.value
    .filter((i) => isIgnorableAuditIssue(i) && !isIssueIgnored(i))
    .map(auditIssueKey)
  if (!keys.length) {
    ElMessage.info('没有可忽略的空 catch 项')
    return
  }
  ignoredIssueKeys.value = [...ignoredIssueKeys.value, ...keys.filter((k) => !ignoredIssueKeys.value.includes(k))]
  persistIgnoredIssues()
  ElMessage.success(`已忽略 ${keys.length} 条空 catch 问题`)
}

async function focusEditorLine(line: number) {
  activeFocusLine.value = line
  await nextTick()
  codeEditorRef.value?.scrollToLine?.(line)
}

async function loadProject() {
  loading.value = true
  try {
    const [tree, stats, meta] = await Promise.all([
      fetchProjectTree(projectId.value),
      fetchProjectStats(projectId.value),
      fetchProjectMeta(projectId.value),
    ])
    projectName.value = tree.name
    sourceUrl.value = meta.sourceUrl
    exportEnabled.value = meta.exportEnabled
    treeData.value = toTreeData(tree)
    statsText.value = `${stats.fileCount} 文件 · ${formatSize(stats.totalSize)} · 深度 ${stats.maxDepth}`
    statFileCount.value = stats.fileCount
    statTotalSize.value = stats.totalSize
    statMaxDepth.value = stats.maxDepth
    touchActivity('刷新项目')
  } catch (e) {
    const msg = e instanceof Error ? e.message : '加载项目失败'
    ElMessage.error(msg)
    if (msg.includes('未就绪') || msg.includes('不存在') || msg.includes('无权')) {
      projectStore.removeRecent(projectId.value)
    }
  } finally {
    loading.value = false
  }
}

async function loadSessions() {
  try {
    sessions.value = await listSessions(projectId.value)
    if (!activeSessionId.value && sessions.value.length) {
      activeSessionId.value = sessions.value[0].id
    }
  } catch {
    sessions.value = []
  }
}

async function loadMessages() {
  if (!activeSessionId.value) {
    messages.value = []
    return
  }
  messages.value = await listMessages(activeSessionId.value)
  await nextTick()
  chatBox.value?.scrollTo({ top: chatBox.value.scrollHeight, behavior: 'smooth' })
}

async function onTreeClick(node: TreeNode) {
  if (!node.isLeaf) return
  if (fileDirty.value) {
    try {
      await ElMessageBox.confirm('当前文件有未保存修改，切换将丢失，继续？', '未保存', {
        confirmButtonText: '继续切换',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }
  }
  currentPath.value = node.path
  diffPreview.value = null
  try {
    const file = await fetchFileContent(projectId.value, node.path)
    fileLanguage.value = file.language
    fileBinary.value = file.binary
    const text = file.binary ? '// 二进制文件，请下载 ZIP 查看' : file.content ?? ''
    savedContent.value = text
    editorContent.value = text
    treeRef.value?.setCurrentKey?.(node.path)
    if (pendingJumpLine.value) {
      await focusEditorLine(pendingJumpLine.value)
      pendingJumpLine.value = null
    }
    touchActivity(`打开 ${node.path}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '读取文件失败')
  }
}

function startEditorResize(event: MouseEvent) {
  event.preventDefault()
  const startY = event.clientY
  const startHeight = editorHeight.value
  function onMove(e: MouseEvent) {
    editorHeight.value = Math.min(720, Math.max(220, startHeight + e.clientY - startY))
  }
  function onUp() {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

function startTreeResize(event: MouseEvent) {
  if (treeCollapsed.value || isNarrow.value) return
  event.preventDefault()
  const startX = event.clientX
  const startWidth = treeWidth.value
  function onMove(e: MouseEvent) {
    treeWidth.value = Math.min(420, Math.max(200, startWidth + e.clientX - startX))
  }
  function onUp() {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

function startChatResize(event: MouseEvent) {
  if (!showChatPanel.value || isNarrow.value) return
  event.preventDefault()
  const startX = event.clientX
  const startWidth = chatWidth.value
  function onMove(e: MouseEvent) {
    chatWidth.value = Math.min(560, Math.max(280, startWidth - (e.clientX - startX)))
  }
  function onUp() {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

async function openChatPanel() {
  showChatPanel.value = true
  if (isNarrow.value) mobilePane.value = 'chat'
  if (activeSessionId.value) {
    await loadMessages()
  }
}

function onRevertEditorChanges() {
  if (!fileDirty.value || fileBinary.value) return
  editorContent.value = savedContent.value
  diffPreview.value = null
  fixStepApplied.value = false
  ElMessage.info('已撤销未保存的修改，恢复为上次保存内容')
}

async function onSaveFile(): Promise<boolean> {
  if (!currentPath.value || fileBinary.value || !fileDirty.value) return false
  const issueKeyToVerify =
    fixStepApplied.value
      ? (activeFixRecord.value?.issueKey ?? activeIssueKey.value)
      : null
  saveLoading.value = true
  try {
    await saveFileContent(projectId.value, currentPath.value, editorContent.value)
    savedContent.value = editorContent.value
    diffPreview.value = null
    ElMessage.success('已保存')
    touchActivity(`保存 ${currentPath.value}`)
    if (issueKeyToVerify) {
      fixStepApplied.value = false
      await refreshAuditAfterFix(issueKeyToVerify)
    }
    return true
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
    return false
  } finally {
    saveLoading.value = false
  }
}

async function refreshAuditAfterFix(issueKey?: string | null) {
  auditRefreshing.value = true
  startTaskProgress('保存后重新检查', AUDIT_PROGRESS_STAGES)
  try {
    const report = await runProjectAudit(projectId.value)
    const prevTotal = auditIssues.value.length
    auditIssues.value = report.issues
    auditScanNote.value = report.scanNote ?? ''
    auditScanTruncated.value = report.scanTruncated ?? false
    if (issueKey) {
      const stillThere = report.issues.some((i) => auditIssueKey(i) === issueKey)
      clearFixStateIfResolved(issueKey)
      if (!stillThere) {
        ElMessage.success('该问题已消除，列表已更新')
      } else {
        ElMessage.warning('已保存，但规则仍命中该行，请继续修改或点「忽略」')
      }
    } else if (report.issues.length < prevTotal) {
      ElMessage.success(`检查已更新：${prevTotal} → ${report.issues.length} 个问题`)
    }
    persistProjectHealthFromAudit(report)
    pushAuditHistory(projectId.value, report.issues)
    finishTaskProgress(true)
  } catch (e) {
    finishTaskProgress(false)
    ElMessage.error(e instanceof Error ? e.message : '保存后重新检查失败，请手动点「检查代码」')
  } finally {
    auditRefreshing.value = false
  }
}

function clearFixStateIfResolved(issueKey: string) {
  if (!auditIssues.value.some((i) => auditIssueKey(i) === issueKey)) {
    if (activeIssueKey.value === issueKey) {
      activeIssueKey.value = null
      activeFocusLine.value = null
    }
    const fix = fixRecords.value.find((r) => r.issueKey === issueKey)
    if (fix && activeFixId.value === fix.id) {
      activeFixId.value = null
    }
  }
}

function scheduleAutoDiffPreview() {
  if (route.name !== 'project-workbench' || projectId.value <= 0) return
  if (diffPreviewTimer) clearTimeout(diffPreviewTimer)
  if (!currentPath.value || fileBinary.value || !fileDirty.value) {
    diffPreview.value = null
    return
  }
  diffPreviewTimer = setTimeout(() => {
    void onPreviewDiff(true)
  }, 350)
}

async function loadFixRecords() {
  try {
    fixRecords.value = await listFixRecords(projectId.value)
  } catch {
    fixRecords.value = []
  }
}

async function loadHitlState() {
  try {
    hitlState.value = await fetchHitlState(projectId.value, hitlSessionKey.value)
  } catch {
    hitlState.value = null
  }
}

async function confirmIfDirty(actionLabel: string): Promise<boolean> {
  if (!fileDirty.value) return true
  try {
    await ElMessageBox.confirm(
      `当前文件有未保存修改，${actionLabel}可能覆盖其它行的编辑，继续？`,
      '未保存',
      { confirmButtonText: '继续', cancelButtonText: '取消', type: 'warning' },
    )
    return true
  } catch {
    return false
  }
}

async function runSuggestFixesPool(pool: AuditIssueItem[], progressTitle = '生成改法建议') {
  if (!pool.length) {
    ElMessage.warning('没有可生成建议的问题')
    return
  }
  fixLoading.value = true
  detailCollapsed.value = false
  startTaskProgress(progressTitle, FIX_PROGRESS_STAGES)
  try {
    const picked = [...pool].sort((a, b) => riskWeight(b.riskLevel) - riskWeight(a.riskLevel)).slice(0, 8)
    await suggestFixes(projectId.value, picked)
    await loadFixRecords()
    if (!fixRecords.value.length) {
      finishTaskProgress(false)
      ElMessage.warning('还没有改法建议')
      return
    }
    finishTaskProgress(true)
    touchActivity(`生成 ${fixRecords.value.length} 条改法建议`)
    ElMessage.success(`已生成改法建议，共 ${fixRecords.value.length} 条`)
    activeFixId.value = fixRecords.value[0]?.id ?? null
    fixStepApplied.value = false
    await loadHitlState()
  } catch (e) {
    finishTaskProgress(false)
    ElMessage.error(e instanceof Error ? e.message : '获取改法建议失败')
  } finally {
    fixLoading.value = false
  }
}

async function onSuggestFixes() {
  const pool = suggestibleIssues.value
  if (!pool.length) {
    ElMessage.warning('没有可生成建议的问题（可能已全部忽略、已有建议或当前筛选无结果）')
    return
  }
  await runSuggestFixesPool(pool)
}

async function onBatchSuggestFixes(ruleId: string) {
  const pool = suggestibleIssues.value.filter((i) => i.ruleId === ruleId)
  if (pool.length < 2) {
    ElMessage.info('该规则下可建议的问题不足 2 条')
    return
  }
  await runSuggestFixesPool(pool, `批量生成 · ${ruleId}`)
}

function openAuditHistoryDialog() {
  auditHistoryVisible.value = true
}

function applyWorkbenchSettingsDefaults() {
  hideDependencyIssues.value = settingsStore.settings.workbenchHideDependency
  hideIgnorableCatch.value = settingsStore.settings.workbenchHideCatch
  if (settingsStore.settings.workbenchCompactPanels) {
    hintCollapsed.value = true
  }
}

function getEditorLineRanges() {
  const selection = codeEditorRef.value?.getSelectionRange?.() ?? null
  const viewport = codeEditorRef.value?.getVisibleLineRange?.() ?? null
  return {
    selection: selection ? { startLine: selection.startLine, endLine: selection.endLine } : null,
    viewport,
    selectionText: selection?.text ?? '',
  }
}

function buildWorkbenchContext(): WorkbenchContext {
  const ranges = getEditorLineRanges()
  const ctx: WorkbenchContext = {}
  if (currentPath.value) ctx.filePath = currentPath.value
  if (ranges.selection) {
    ctx.selectionStartLine = ranges.selection.startLine
    ctx.selectionEndLine = ranges.selection.endLine
    if (ranges.selectionText.trim()) {
      ctx.selectionSnippet = ranges.selectionText.slice(0, 2000)
    }
  }
  if (ranges.viewport) {
    ctx.viewportStartLine = ranges.viewport.startLine
    ctx.viewportEndLine = ranges.viewport.endLine
  }
  if (chatAttachFile.value && currentPath.value && !fileBinary.value) {
    ctx.fileContent = editorContent.value.slice(0, 12000)
  }
  const javaPaths = collectJavaFilePaths(treeData.value, 200)
  if (javaPaths.length) ctx.javaFilePaths = javaPaths
  return ctx
}

async function openFileAtLine(filePath: string, line: number) {
  const safeLine = Math.max(1, line)
  showChatPanel.value = true
  if (filePath === currentPath.value) {
    await focusEditorLine(safeLine)
    return
  }
  pendingJumpLine.value = safeLine
  await onTreeClick({
    label: filePath.split('/').pop() ?? filePath,
    path: filePath,
    isLeaf: true,
  })
}

async function navigateByAgentAction(action: AgentAction) {
  const javaPaths = collectJavaFilePaths(treeData.value, 200)
  if (action.filePath && !action.symbol) {
    await openFileAtLine(action.filePath, action.line ?? 1)
    return
  }
  const symbol = action.symbol?.trim()
  if (!symbol) {
    throw new Error('缺少要定位的方法或类名')
  }
  const loc = await locateJavaSymbol({
    projectId: projectId.value,
    javaPaths,
    symbol,
    hintFilePath: action.filePath ?? undefined,
    currentFilePath: currentPath.value || undefined,
    currentFileContent: !fileBinary.value ? editorContent.value : undefined,
    lineHint: action.line ?? null,
  })
  if (!loc) {
    throw new Error(`未在项目中找到「${symbol}」，可尝试 @文件 或说明完整类名`)
  }
  await openFileAtLine(loc.filePath, loc.line)
}

function stashAgentActions(messageId: number, actions?: AgentAction[]) {
  if (!actions?.length) return
  pendingAgentActionsByMessageId.value = new Map(pendingAgentActionsByMessageId.value).set(
    messageId,
    actions,
  )
}

function agentActionsForMessage(messageId: number) {
  return pendingAgentActionsByMessageId.value.get(messageId) ?? []
}

function agentScopeLabel(scope: string) {
  if (scope === 'viewport') return '当前可见区域'
  if (scope === 'selection') return '选中代码'
  return '全文件'
}

function clearAgentActions(messageId: number) {
  const next = new Map(pendingAgentActionsByMessageId.value)
  next.delete(messageId)
  pendingAgentActionsByMessageId.value = next
}

async function applyJavadocWithScope(scope: DocGenScopeMode, skipConfirm = false) {
  if (!isJavaFile.value || !currentPath.value || fileBinary.value) {
    ElMessage.warning('请先打开可编辑的 Java 源文件')
    return
  }
  const scopeText =
    scope === 'file' ? '全文件' : scope === 'viewport' ? '当前可见区域' : '选中代码'
  if (!skipConfirm) {
    try {
      await ElMessageBox.confirm(
        `范围：${scopeText}。将调用 AI 生成 Javadoc 并写入编辑器（已有注释跳过），写入后请保存。`,
        '一键补充注释',
        { confirmButtonText: '开始生成', cancelButtonText: '取消', type: 'info' },
      )
    } catch {
      return
    }
  }
  docGenLoading.value = true
  try {
    const { applied, scopeLabel } = await runJavadocForScope({
      projectId: projectId.value,
      source: editorContent.value,
      filePath: currentPath.value,
      scope,
      editorRanges: getEditorLineRanges(),
    })
    if (!applied.insertedType && !applied.insertedMethods.length) {
      const skipHint = applied.skippedExisting.length
        ? `（已跳过：${applied.skippedExisting.join('、')}）`
        : ''
      ElMessage.info(`「${scopeLabel}」没有可插入的位置${skipHint}`)
      return
    }
    editorContent.value = applied.content
    touchActivity(`补充 Javadoc · ${scopeLabel}`)
    scheduleAutoDiffPreview()
    const parts: string[] = []
    if (applied.insertedType) parts.push('类型 1 处')
    if (applied.insertedMethods.length) parts.push(`方法 ${applied.insertedMethods.length} 处`)
    const skipHint = applied.skippedExisting.length
      ? `；已跳过已有注释：${applied.skippedExisting.join('、')}`
      : ''
    ElMessage.success(`「${scopeLabel}」已写入 ${parts.join('、')}${skipHint}，请保存文件`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : 'Javadoc 生成失败')
  } finally {
    docGenLoading.value = false
  }
}

async function onJavadocCommand(command: string | number) {
  await applyJavadocWithScope(String(command) as DocGenScopeMode)
}

async function onExecuteAgentAction(msg: ChatMessage, action: AgentAction) {
  if (action.type === 'JAVADOC') {
    try {
      await ElMessageBox.confirm(
        `${action.summary}\n范围：${agentScopeLabel(action.scope ?? 'file')}\n\n将调用文档生成接口写入当前编辑器（不会自动保存）。`,
        '确认 Agent 操作',
        { type: 'warning', confirmButtonText: '执行', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    const scope = (action.scope || 'file') as DocGenScopeMode
    await applyJavadocWithScope(scope, true)
    clearAgentActions(msg.id)
    return
  }
  if (action.type === 'NAVIGATE') {
    try {
      await ElMessageBox.confirm(
        `${action.summary}\n\n将在工作台打开文件并定位到对应行（不修改代码）。`,
        '确认跳转',
        { type: 'info', confirmButtonText: '跳转', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    try {
      await navigateByAgentAction(action)
      touchActivity(`Agent 跳转 · ${action.symbol ?? action.filePath ?? ''}`)
      ElMessage.success('已跳转到目标位置')
      clearAgentActions(msg.id)
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : '跳转失败')
    }
    return
  }
  ElMessage.warning('该操作类型不在安全白名单内')
}

async function onClarifyOption(msg: ChatMessage, option: ClarifyOption) {
  if (!activeSessionId.value) {
    await onNewSession()
  }
  if (!activeSessionId.value) return
  const text = `我选择：${option.label}（选项 ${option.id}）`
  chatLoading.value = true
  try {
    const reply = await sendChat(activeSessionId.value, text, buildWorkbenchContext())
    messages.value.push(reply.userMessage, reply.assistantMessage)
    stashAgentActions(reply.assistantMessage.id, reply.actions)
    clearAgentActions(msg.id)
    await loadSessions()
    await nextTick()
    chatBox.value?.scrollTo({ top: chatBox.value.scrollHeight, behavior: 'smooth' })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '发送失败')
  } finally {
    chatLoading.value = false
  }
}

async function onSelectFixRecord(record: FixRecord) {
  activeFixId.value = record.id
  diffPreview.value = null
  detailCollapsed.value = false
  if (record.filePath !== currentPath.value) {
    await onTreeClick({
      label: record.filePath.split('/').pop() ?? record.filePath,
      path: record.filePath,
      isLeaf: true,
    })
  }
  await nextTick()
  if (record.lineNo > 0) {
    codeEditorRef.value?.scrollToLine?.(record.lineNo)
  }
}

async function onApplyFix() {
  const record = activeFixRecord.value
  if (!record || !currentPath.value || fileBinary.value) return
  if (record.filePath !== currentPath.value) {
    ElMessage.warning(`请先打开文件 ${record.filePath} 再应用该建议`)
    return
  }
  if (!(await confirmIfDirty('应用建议'))) return
  const result = tryApplySuggestion(editorContent.value, record.lineNo, record.suggestion)
  if (!result.applied) {
    ElMessage.warning(result.message)
    return
  }
  editorContent.value = result.content
  activeIssueKey.value = record.issueKey
  fixStepApplied.value = true
  ElMessage.success(result.message)
  scheduleAutoDiffPreview()
}

function onDismissFix() {
  const record = activeFixRecord.value
  if (!record) return
  dismissedFixIds.value = new Set([...dismissedFixIds.value, record.id])
  persistDismissedFixes()
  diffPreview.value = null
  const next = visibleFixRecords.value.find((r) => r.id !== record.id)
  activeFixId.value = next?.id ?? null
  ElMessage.info('已放弃该建议，可继续处理下一条')
}

function diffLineClass(line: string) {
  if (line.startsWith('+')) return 'diff-line--add'
  if (line.startsWith('-')) return 'diff-line--del'
  return 'diff-line--ctx'
}

async function onPreviewDiff(silent = false) {
  if (!currentPath.value || fileBinary.value) return
  if (!fileDirty.value) {
    diffPreview.value = null
    if (!silent) ElMessage.info('内容无变化')
    return
  }
  diffLoading.value = true
  try {
    diffPreview.value = await previewDiff(projectId.value, {
      filePath: currentPath.value,
      beforeContent: savedContent.value,
      afterContent: editorContent.value,
      fixRecordId: activeFixId.value ?? undefined,
    })
    if (!diffPreview.value.changed && !silent) {
      ElMessage.info('内容无变化')
    }
    await loadFixRecords()
  } catch (e) {
    if (!silent) {
      ElMessage.error(e instanceof Error ? e.message : '对比修改失败')
    }
  } finally {
    diffLoading.value = false
  }
}

async function onHitl(status: 'APPROVED' | 'MODIFIED' | 'REJECTED') {
  if (!activeFixId.value) {
    ElMessage.warning('请先选择一条改法建议再确认')
    return
  }
  if ((status === 'APPROVED' || status === 'MODIFIED') && !fileDirty.value) {
    ElMessage.warning('当前没有未保存的修改，请先应用建议或让 AI 改，再点「对比修改」')
    return
  }
  if (hitlState.value?.terminated) {
    ElMessage.info('这条建议已经确认过了，请选其他建议或重新获取')
    return
  }
  hitlLoading.value = true
  try {
    hitlState.value = await submitHitl(projectId.value, {
      status,
      fixRecordId: activeFixId.value,
      sessionKey: hitlSessionKey.value,
    })
    if (hitlState.value.terminated && status === 'REJECTED') {
      ElMessage.warning(`已驳回（第 ${hitlState.value.round} 轮）`)
    } else {
      ElMessage.success(`已确认：${status}`)
    }
    if (status === 'APPROVED' || status === 'MODIFIED') {
      const saved = await onSaveFile()
      if (saved) fixStepApplied.value = false
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '确认提交失败')
  } finally {
    hitlLoading.value = false
  }
}

function riskWeight(level: string) {
  if (level === 'HIGH') return 3
  if (level === 'MEDIUM') return 2
  return 1
}

async function onDownload() {
  if (!exportEnabled.value) {
    ElMessage.warning('导入时未开启 ZIP 导出')
    return
  }
  try {
    const resp = await downloadProjectZip(projectId.value)
    const blob = resp.data as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `project-${projectId.value}.zip`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '下载失败')
  }
}

async function onRunAudit() {
  auditLoading.value = true
  touchActivity('检查代码中')
  startTaskProgress('检查代码', AUDIT_PROGRESS_STAGES)
  try {
    const report = await runProjectAudit(projectId.value)
    auditIssues.value = report.issues
    auditScanNote.value = report.scanNote ?? ''
    auditScanTruncated.value = report.scanTruncated ?? false
    fixRecords.value = []
    activeFixId.value = null
    diffPreview.value = null
    fixStepApplied.value = false
    pendingAiFixByMessageId.value = new Map()
    await loadHitlState()
    persistProjectHealthFromAudit(report)
    pushAuditHistory(projectId.value, report.issues)
    finishTaskProgress(true)
    touchActivity(`检查完成 · ${report.total} 个问题`)
    ElMessage.success(`检查完成：发现 ${report.total} 个问题（严重 ${report.highCount} 个）— 可点「获取改法建议」`)
  } catch (e) {
    finishTaskProgress(false)
    ElMessage.error(e instanceof Error ? e.message : '检查代码失败')
  } finally {
    auditLoading.value = false
  }
}

async function onLoadScore() {
  scoreLoading.value = true
  try {
    const score = await fetchProjectScore(projectId.value)
    scoreOverall.value = score.overall
    scoreSummary.value = score.summary
    const cached = loadProjectHealth(projectId.value)
    saveProjectHealth({
      projectId: projectId.value,
      totalIssues: cached?.totalIssues ?? visibleAuditIssues.value.length,
      highCount: cached?.highCount ?? auditIssues.value.filter((i) => i.riskLevel === 'HIGH').length,
      mediumCount: cached?.mediumCount ?? auditIssues.value.filter((i) => i.riskLevel === 'MEDIUM').length,
      lowCount: cached?.lowCount ?? auditIssues.value.filter((i) => i.riskLevel === 'LOW').length,
      overallScore: score.overall,
      auditedAt: cached?.auditedAt ?? new Date().toISOString(),
    })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '评分失败')
  } finally {
    scoreLoading.value = false
  }
}

async function onNewSession() {
  const session = await createSession(projectId.value, '新会话')
  sessions.value.unshift(session)
  activeSessionId.value = session.id
  await loadMessages()
}

async function onOnboard() {
  chatLoading.value = true
  try {
    showChatPanel.value = true
    if (isNarrow.value) mobilePane.value = 'chat'
    const reply = await onboardProject(projectId.value)
    await loadSessions()
    activeSessionId.value = reply.userMessage.sessionId
    messages.value = [reply.userMessage, reply.assistantMessage]
    await nextTick()
    chatBox.value?.scrollTo({ top: chatBox.value.scrollHeight, behavior: 'smooth' })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '分析失败')
  } finally {
    chatLoading.value = false
  }
}

function toggleChatAttachFile() {
  if (!currentPath.value) {
    ElMessage.info('请先打开一个文件')
    return
  }
  chatAttachFile.value = !chatAttachFile.value
}

function attachChatActiveIssue() {
  const issue =
    activeIssueKey.value != null
      ? visibleAuditIssues.value.find((i) => auditIssueKey(i) === activeIssueKey.value)
      : null
  if (!issue) {
    ElMessage.info('请先在「发现的问题」中点击一条问题')
    return
  }
  chatAttachIssue.value = issue
}

function clearChatAttachIssue() {
  chatAttachIssue.value = null
}

function buildChatMessage(): string {
  const parts: string[] = []
  if (chatAttachFile.value && currentPath.value) {
    parts.push(`【@文件 ${currentPath.value}】`)
    if (!fileBinary.value && editorContent.value) {
      const lines = editorContent.value.split('\n')
      const snippet = lines.slice(0, 100).join('\n')
      parts.push('```\n' + snippet + (lines.length > 100 ? '\n// …' : '') + '\n```')
    }
  }
  if (chatAttachIssue.value) {
    const issue = chatAttachIssue.value
    parts.push(
      `【@问题 ${issue.filePath}:${issue.line}】${issue.description}（${riskLevelLabel(issue.riskLevel)}）`,
    )
    if (issue.triggerSnippet) parts.push(`触发片段：\n${issue.triggerSnippet}`)
  }
  const body = chatInput.value.trim()
  if (!parts.length) return body
  return `${parts.join('\n\n')}\n\n${body}`
}

async function onSendChat() {
  if (!chatInput.value.trim() || !activeSessionId.value) return
  chatLoading.value = true
  try {
    const payload = buildChatMessage()
    const reply = await sendChat(activeSessionId.value, payload, buildWorkbenchContext())
    messages.value.push(reply.userMessage, reply.assistantMessage)
    stashAgentActions(reply.assistantMessage.id, reply.actions)
    chatInput.value = ''
    chatAttachFile.value = false
    chatAttachIssue.value = null
    await loadSessions()
    await nextTick()
    chatBox.value?.scrollTo({ top: chatBox.value.scrollHeight, behavior: 'smooth' })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '发送失败')
  } finally {
    chatLoading.value = false
  }
}

async function jumpToIssue(issue: AuditIssueItem) {
  activeIssueKey.value = auditIssueKey(issue)
  detailCollapsed.value = false
  const line = issue.line
  const fix = fixByIssueKey.value.get(auditIssueKey(issue))
  if (fix) activeFixId.value = fix.id

  if (issue.filePath !== currentPath.value) {
    pendingJumpLine.value = line
    await onTreeClick({
      label: issue.filePath.split('/').pop() ?? issue.filePath,
      path: issue.filePath,
      isLeaf: true,
    })
  } else {
    await focusEditorLine(line)
  }
}

async function onAiFixIssue(issue: AuditIssueItem) {
  if (fileBinary.value && issue.filePath === currentPath.value) {
    ElMessage.warning('二进制文件无法在此编辑，请下载 ZIP 后本地修改')
    return
  }
  await jumpToIssue(issue)
  showChatPanel.value = true
  if (isNarrow.value) mobilePane.value = 'chat'
  if (!activeSessionId.value) {
    await onNewSession()
  }
  const fix = fixByIssueKey.value.get(auditIssueKey(issue))
  const prompt =
    `请分析 ${issue.filePath}:${issue.line} 的问题「${issue.description}」（${riskLevelLabel(issue.riskLevel)}）。` +
    '先 1~3 句说明原因与改法，再用 ```java 代码块``` 给出可直接替换的代码行或片段。' +
    (issue.triggerSnippet ? `\n当前代码：\n${issue.triggerSnippet}` : '') +
    (fix ? `\n系统模板建议：${fix.suggestion}` : '')

  chatLoading.value = true
  try {
    const reply = await sendChat(activeSessionId.value!, prompt, buildWorkbenchContext())
    messages.value.push(reply.userMessage, reply.assistantMessage)
    stashAgentActions(reply.assistantMessage.id, reply.actions)
    chatInput.value = ''
    pendingAiFixByMessageId.value = new Map(pendingAiFixByMessageId.value).set(
      reply.assistantMessage.id,
      issue,
    )
    await loadSessions()
    await nextTick()
    chatBox.value?.scrollTo({ top: chatBox.value.scrollHeight, behavior: 'smooth' })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : 'AI 分析失败')
  } finally {
    chatLoading.value = false
  }
}

function isAiFixPendingMessage(messageId: number) {
  return pendingAiFixByMessageId.value.has(messageId)
}

function clearAiFixPending(messageId: number) {
  const next = new Map(pendingAiFixByMessageId.value)
  next.delete(messageId)
  pendingAiFixByMessageId.value = next
}

function getAiFixIssue(messageId: number) {
  return pendingAiFixByMessageId.value.get(messageId)
}

async function ensureIssueFileOpen(issue: AuditIssueItem) {
  if (issue.filePath === currentPath.value) return
  await onTreeClick({
    label: issue.filePath.split('/').pop() ?? issue.filePath,
    path: issue.filePath,
    isLeaf: true,
  })
  await focusEditorLine(issue.line)
}

async function onAdoptAiFix(msg: ChatMessage) {
  const issue = getAiFixIssue(msg.id)
  if (!issue) return
  activeIssueKey.value = auditIssueKey(issue)
  if (fileBinary.value) {
    ElMessage.warning('当前为二进制文件，无法写入修改')
    return
  }
  const code = extractSuggestedCode(msg.content)
  if (!code) {
    ElMessage.warning('回复中未找到 ``` 代码块，请让 AI 用 java 代码块给出修改后再点「采用」')
    return
  }
  if (!(await confirmIfDirty('采用 AI 修改'))) return
  await ensureIssueFileOpen(issue)
  const result = applyCodeAtLine(editorContent.value, issue.line, code)
  if (!result.applied) {
    ElMessage.warning(result.message)
    return
  }
  editorContent.value = result.content
  fixStepApplied.value = true
  clearAiFixPending(msg.id)
  ElMessage.success('已写入编辑器，先看「对比修改」，满意再点「保存并重新检查」')
  detailCollapsed.value = false
  scheduleAutoDiffPreview()
}

function onSkipAiFix(msg: ChatMessage) {
  if (!getAiFixIssue(msg.id)) return
  clearAiFixPending(msg.id)
  ElMessage.info('已暂不采用 AI 修改，可继续用手动或模板建议')
}

function onIgnoreWarningFromAi(msg: ChatMessage) {
  const issue = getAiFixIssue(msg.id)
  if (!issue) return
  ignoreIssue(issue)
  clearAiFixPending(msg.id)
}

async function onSandboxDemo() {
  sandboxRunLoading.value = true
  sandboxRunOutput.value = null
  try {
    const link = await discoverDemoLink(projectId.value, sourceUrl.value)
    demoLinkHint.value = link
    if (link.found && link.url) {
      window.open(link.url, '_blank', 'noopener,noreferrer')
      ElMessage.success(link.message || `已打开演示：${link.label || link.url}`)
      return
    }

    if (!runProfile.value || runProfile.value.capability === 'HEAVY') {
      ElMessage.warning(link.message || '未找到文档中的演示链接；重型项目不支持沙箱编译演示')
      return
    }

    const cmd = sandboxCommand.value.trim() || 'mvn -q -DskipTests compile'
    sandboxRunOutput.value = await runSandboxDemo(projectId.value, cmd)
    if (sandboxRunOutput.value.success) {
      ElMessage.success(sandboxRunOutput.value.message)
    } else {
      ElMessage.warning(sandboxRunOutput.value.message)
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '演示运行失败')
  } finally {
    sandboxRunLoading.value = false
  }
}

async function renderArchDiagram() {
  const diagram = archTab.value === 'layer' ? archLayerDiagram.value : archPackageDiagram.value
  if (!diagram.trim()) {
    archSvg.value = ''
    return
  }
  mermaid.initialize({ startOnLoad: false, theme: 'dark', securityLevel: 'loose' })
  const id = `cw-arch-${Date.now()}`
  const { svg } = await mermaid.render(id, diagram)
  archSvg.value = svg
  archZoom.value = 1
}

function zoomArch(delta: number) {
  archZoom.value = Math.min(2.5, Math.max(0.5, +(archZoom.value + delta).toFixed(2)))
}

function downloadArchSvg() {
  if (!archSvg.value) {
    ElMessage.warning('请先生成架构图')
    return
  }
  const blob = new Blob([archSvg.value], { type: 'image/svg+xml;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `architecture-${projectId.value}-${archTab.value}.svg`
  a.click()
  URL.revokeObjectURL(url)
}

function downloadArchSource() {
  const diagram = archTab.value === 'layer' ? archLayerDiagram.value : archPackageDiagram.value
  if (!diagram.trim()) {
    ElMessage.warning('请先生成架构图')
    return
  }
  const blob = new Blob([diagram], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `architecture-${projectId.value}-${archTab.value}.mmd`
  a.click()
  URL.revokeObjectURL(url)
}

async function onDeleteSession(sessionId: number) {
  try {
    await ElMessageBox.confirm('删除后无法恢复，确定继续？', '删除会话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteSession(sessionId)
    sessions.value = sessions.value.filter((s) => s.id !== sessionId)
    if (activeSessionId.value === sessionId) {
      activeSessionId.value = sessions.value[0]?.id ?? null
      messages.value = []
      if (activeSessionId.value) await loadMessages()
    }
    ElMessage.success('会话已删除')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function updateLayout() {
  isNarrow.value = window.innerWidth < 1100
}

function onResize() {
  updateLayout()
}

async function onLoadArchitecture() {
  archLoading.value = true
  try {
    const arch = await fetchProjectArchitecture(projectId.value)
    archLayerDiagram.value = arch.layerDiagram
    archPackageDiagram.value = arch.packageDiagram
    archStats.value = `${arch.typeCount} 个类型 · ${arch.callEdgeCount} 条调用边`
    await renderArchDiagram()
    ElMessage.success('架构图已生成')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '架构分析失败')
  } finally {
    archLoading.value = false
  }
}

async function onLoadRunProfile() {
  runProfileLoading.value = true
  sandboxResult.value = null
  try {
    runProfile.value = await fetchRunProfile(projectId.value)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '运行判定失败')
  } finally {
    runProfileLoading.value = false
  }
}

async function onValidateSandbox() {
  if (!sandboxCommand.value.trim()) {
    ElMessage.warning('请输入待校验命令')
    return
  }
  sandboxLoading.value = true
  try {
    sandboxResult.value = await validateSandboxCommand(projectId.value, sandboxCommand.value.trim())
    if (sandboxResult.value.allowed) {
      ElMessage.success(sandboxResult.value.message)
    } else {
      ElMessage.warning(sandboxResult.value.message)
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '沙箱校验失败')
  } finally {
    sandboxLoading.value = false
  }
}

async function onLoadEvalReport() {
  evalReportLoading.value = true
  try {
    evalReport.value = await fetchEvalReport(projectId.value)
    ElMessage.success('评测报告已生成')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '评测报告生成失败')
  } finally {
    evalReportLoading.value = false
  }
}

watch(archTab, () => {
  renderArchDiagram()
})

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

watch(activeSessionId, () => {
  if (showChatPanel.value) loadMessages()
})

watch(activeFixId, () => {
  loadHitlState()
})

watch(editorContent, () => {
  scheduleAutoDiffPreview()
})

watch(savedContent, () => {
  scheduleAutoDiffPreview()
})

function tryStartWorkbenchTour() {
  if (workbenchTourTriggered.value || loading.value || !isWorkbenchTourPending()) return
  workbenchTourTriggered.value = true
  void nextTick().then(() => {
    setTimeout(() => {
      showWorkbenchTour.value = true
    }, 500)
  })
}

async function resetWorkbenchForProject() {
  auditIssues.value = []
  auditScanNote.value = ''
  auditScanTruncated.value = false
  fixRecords.value = []
  activeFixId.value = null
  diffPreview.value = null
  hitlState.value = null
  fixStepApplied.value = false
  pendingAiFixByMessageId.value = new Map()
  pendingAgentActionsByMessageId.value = new Map()
  activeIssueKey.value = null
  activeFocusLine.value = null
  currentPath.value = ''
  savedContent.value = ''
  editorContent.value = ''
  fileLanguage.value = null
  fileBinary.value = false
  messages.value = []
  activeSessionId.value = null
  sessions.value = []
  loadDismissedFixes()
  loadIgnoredIssues()
}

watch(
  () => routeProjectId(),
  async (id, prev) => {
    if (id == null) return
    const prevId = prev ?? 0
    const switching = prevId > 0 && prevId !== id
    projectId.value = id
    if (!switching) return
    await resetWorkbenchForProject()
    await loadProject()
    await loadSessions()
    await loadFixRecords()
    await loadHitlState()
  },
)

watch(chatLoading, async (loading) => {
  if (!loading) return
  await nextTick()
  chatBox.value?.scrollTo({ top: chatBox.value.scrollHeight, behavior: 'smooth' })
})

onMounted(async () => {
  updateLayout()
  window.addEventListener('resize', onResize)
  const id = routeProjectId()
  if (id == null) {
    router.replace('/import')
    return
  }
  projectId.value = id
  applyWorkbenchSettingsDefaults()
  await loadProject()
  loadDismissedFixes()
  loadIgnoredIssues()
  await loadSessions()
  await loadFixRecords()
  await loadHitlState()
  tryStartWorkbenchTour()
})

onActivated(() => {
  updateLayout()
  const id = routeProjectId()
  if (id != null) projectId.value = id
  tryStartWorkbenchTour()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  stopTaskProgressTimer()
})
</script>

<template>
  <div v-loading="loading" class="workbench">
    <header class="wb-header glass-panel">
      <div>
        <span class="section-label">Project Workspace</span>
        <h1 class="gradient-text">{{ projectName || `项目 #${projectId}` }}</h1>
        <div v-if="statFileCount" class="wb-stats">
          <span class="wb-stat-chip">{{ statFileCount }} 文件</span>
          <span class="wb-stat-chip">{{ formatSize(statTotalSize) }}</span>
          <span class="wb-stat-chip">深度 {{ statMaxDepth }}</span>
        </div>
        <p v-if="sourceUrl" class="wb-repo mono">
          仓库：<a :href="sourceUrl" target="_blank" rel="noopener">{{ sourceUrl }}</a>
        </p>
      </div>
      <div class="wb-actions">
        <el-button data-tour="wb-audit" :icon="Search" type="primary" :loading="auditLoading" @click="onRunAudit">
          检查代码
        </el-button>
        <el-button :loading="archLoading" @click="onLoadArchitecture">架构图</el-button>
        <el-button
          data-tour="wb-ai"
          type="success"
          plain
          :icon="ChatDotRound"
          @click="openChatPanel"
        >
          AI 助手
        </el-button>
        <el-dropdown trigger="click">
          <el-button>
            更多分析
            <span class="dropdown-caret">▾</span>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :disabled="runProfileLoading" @click="onLoadRunProfile">
                运行判定
              </el-dropdown-item>
              <el-dropdown-item :disabled="scoreLoading" @click="onLoadScore">
                项目评分
              </el-dropdown-item>
              <el-dropdown-item :disabled="evalReportLoading" @click="onLoadEvalReport">
                评测报告
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button
          v-if="exportEnabled"
          :icon="Download"
          @click="onDownload"
        >
          下载 ZIP
        </el-button>
        <el-tooltip v-else content="导入 Git 仓库时可选择开启 ZIP 导出">
          <el-button :icon="Download" disabled>导出已关闭</el-button>
        </el-tooltip>
        <el-button :icon="Refresh" @click="loadProject">刷新</el-button>
      </div>
    </header>

    <div class="wb-status-bar glass-panel">
      <nav class="wb-status-bar__crumb" aria-label="当前位置">
        <span class="wb-status-bar__project">{{ projectName || `项目 #${projectId}` }}</span>
        <template v-if="pathBreadcrumbs.length">
          <span v-for="(seg, idx) in pathBreadcrumbs" :key="`${seg}-${idx}`" class="wb-status-bar__seg">
            <span class="wb-status-bar__sep">/</span>{{ seg }}
          </span>
        </template>
        <span v-else class="wb-status-bar__placeholder">未选择文件</span>
      </nav>
      <div class="wb-status-bar__meta">
        <el-tag
          v-for="chip in statusBarChips"
          :key="chip.key"
          size="small"
          :type="chip.type"
          effect="plain"
          class="wb-status-chip"
        >
          {{ chip.label }}
        </el-tag>
        <span v-if="lastActivityAt" class="wb-status-bar__time" :title="lastActivityLabel">
          {{ lastActivityLabel }} · {{ formatActivityTime(lastActivityAt) }}
        </span>
      </div>
    </div>

    <Transition name="cw-collapse">
      <div v-if="!hintCollapsed" class="workflow-hint glass-panel">
        <span>推荐流程：① 检查代码 → ② 看架构图 → ③ 获取改法建议 → ④ 你确认后再保存 → ⑤ 下载或继续和 AI 聊</span>
        <el-button size="small" class="wb-btn-text" @click="hintCollapsed = true">收起</el-button>
      </div>
    </Transition>
    <button v-if="hintCollapsed" type="button" class="workflow-hint-toggle glass-panel" @click="hintCollapsed = false">
      显示推荐流程
    </button>

    <div v-if="scoreOverall != null" class="score-bar glass-panel">
      <span>综合评分</span>
      <strong class="score-value">{{ scoreOverall.toFixed(1) }}</strong>
      <span class="score-summary">{{ scoreSummary }}</span>
    </div>

    <div v-if="archLayerDiagram || archPackageDiagram" class="arch-panel glass-panel">
      <div class="panel-head panel-head--split">
        <span>架构图（辅助分析，需人工确认）</span>
        <div class="panel-head-actions">
          <span class="arch-stats">{{ archStats }}</span>
          <el-button size="small" class="wb-btn-text" @click="archCollapsed = !archCollapsed">
            {{ archCollapsed ? '展开' : '收起' }}
          </el-button>
        </div>
      </div>
      <template v-if="!archCollapsed">
        <el-radio-group v-model="archTab" size="small" class="arch-tabs">
          <el-radio-button label="layer">分层图</el-radio-button>
          <el-radio-button label="package">包拓扑</el-radio-button>
        </el-radio-group>
        <div class="arch-toolbar">
          <el-button size="small" @click="zoomArch(-0.15)">缩小</el-button>
          <span class="arch-zoom-label">{{ Math.round(archZoom * 100) }}%</span>
          <el-button size="small" @click="zoomArch(0.15)">放大</el-button>
          <el-button size="small" class="wb-btn-text" @click="archZoom = 1">重置</el-button>
          <el-button size="small" :icon="Download" @click="downloadArchSvg">下载 SVG</el-button>
          <el-button size="small" class="wb-btn-text" @click="downloadArchSource">下载 Mermaid</el-button>
        </div>
        <div class="arch-svg-wrap">
          <div class="arch-svg-inner" :style="{ transform: `scale(${archZoom})` }" v-html="archSvg" />
        </div>
      </template>
    </div>

    <div v-if="runProfile" class="run-panel glass-panel">
      <div class="panel-head panel-head--split">
        <div>
          <span>运行判定 / 沙箱演示</span>
          <el-tag :type="runProfile.capability === 'HEAVY' ? 'warning' : 'success'" size="small">
            {{ runCapabilityLabel(runProfile.capability) }}
          </el-tag>
        </div>
        <el-button size="small" class="wb-btn-text" @click="runPanelCollapsed = !runPanelCollapsed">
          {{ runPanelCollapsed ? '展开' : '收起' }}
        </el-button>
      </div>
      <template v-if="!runPanelCollapsed">
        <p class="run-summary">{{ runProfile.summary }}</p>
        <div class="sandbox-check">
          <template v-if="runProfile.capability !== 'HEAVY'">
            <el-input
              v-model="sandboxCommand"
              size="small"
              placeholder="如 mvn -q -DskipTests compile"
              class="sandbox-check__input"
            />
            <el-button size="small" :loading="sandboxLoading" @click="onValidateSandbox">校验</el-button>
          </template>
          <el-button size="small" type="primary" :loading="sandboxRunLoading" @click="onSandboxDemo">
            在线演示
          </el-button>
          <el-tag v-if="sandboxResult" size="small" :type="sandboxResult.allowed ? 'success' : 'danger'">
            {{ sandboxResult.allowed ? '通过' : '拒绝' }}
          </el-tag>
        </div>
        <p v-if="demoLinkHint?.found && demoLinkHint.url" class="sandbox-msg">
          文档演示：
          <a :href="demoLinkHint.url" target="_blank" rel="noopener">{{ demoLinkHint.label || demoLinkHint.url }}</a>
          <span class="sandbox-msg__source">（{{ demoLinkHint.sourceFile }}）</span>
        </p>
        <p v-if="sandboxResult" class="sandbox-msg">{{ sandboxResult.message }}</p>
        <pre v-if="sandboxRunOutput" class="sandbox-output">{{ sandboxRunOutput.stdout || sandboxRunOutput.stderr || sandboxRunOutput.message }}</pre>
        <pre class="deploy-guide">{{ runProfile.deployGuideMarkdown }}</pre>
      </template>
    </div>

    <div v-if="evalReport" class="eval-panel glass-panel">
      <div class="panel-head panel-head--split">
        <div>
          <span>量化评测报告</span>
          <el-tag size="small" :type="evalReport.status === 'COMPLETED' ? 'success' : 'warning'">
            {{ evalStatusLabel(evalReport.status) }}
          </el-tag>
          <span class="eval-score">综合 {{ evalReport.overallScore.toFixed(1) }}</span>
        </div>
        <el-button size="small" class="wb-btn-text" @click="evalPanelCollapsed = !evalPanelCollapsed">
          {{ evalPanelCollapsed ? '展开' : '收起' }}
        </el-button>
      </div>
      <pre v-if="!evalPanelCollapsed" class="eval-markdown">{{ evalReport.reportMarkdown }}</pre>
    </div>

    <div v-if="isNarrow" class="mobile-pane-bar glass-panel">
      <el-radio-group v-model="mobilePane" size="small">
        <el-radio-button label="tree">文件树</el-radio-button>
        <el-radio-button label="editor">代码</el-radio-button>
        <el-radio-button v-if="showChatPanel" label="chat">AI 助手</el-radio-button>
      </el-radio-group>
    </div>

    <div
      class="wb-grid"
      :class="{ 'wb-grid--stacked': isNarrow, 'wb-grid--row': !isNarrow }"
    >
      <aside
        v-show="!isNarrow || mobilePane === 'tree'"
        class="panel glass-panel tree-panel"
        :class="{ 'tree-panel--collapsed': treeCollapsed }"
        :style="treeAsideStyle"
        data-tour="wb-tree"
      >
        <div class="panel-head panel-head--tree">
          <span v-if="!treeCollapsed" class="panel-head__title">
            <el-icon><FolderOpened /></el-icon> 文件树
          </span>
          <el-tooltip :content="treeCollapsed ? '展开文件树' : '收起文件树'" placement="right">
            <el-button
              size="small"
              circle
              class="tree-toggle-btn"
              :icon="treeCollapsed ? DArrowRight : DArrowLeft"
              @click="treeCollapsed = !treeCollapsed"
            />
          </el-tooltip>
        </div>
        <template v-if="!treeCollapsed">
          <el-input v-model="treeFilter" placeholder="过滤文件..." size="small" class="tree-filter" clearable />
          <div class="tree-scroll">
            <el-tree
              ref="treeRef"
              :data="filteredTree"
              node-key="path"
              highlight-current
              :expand-on-click-node="true"
              @node-click="onTreeClick"
            >
              <template #default="{ data }">
                <span class="tree-node-row" :class="{ 'tree-node-row--has-issue': treeIssueCount(data.path) > 0 }">
                  <span
                    v-if="treeIssueCount(data.path)"
                    class="tree-density-bar"
                    :class="riskIssueClass(worstRiskByPath[data.path] ?? 'LOW')"
                    :style="{ width: treeDensityWidth(data.path) }"
                    :title="`${treeIssueCount(data.path)} 个问题`"
                  />
                  <span class="tree-node-label">{{ data.label }}</span>
                  <span
                    v-if="treeIssueCount(data.path)"
                    class="tree-issue-badge"
                    :class="riskIssueClass(worstRiskByPath[data.path] ?? 'LOW')"
                    :title="`${treeIssueCount(data.path)} 个问题`"
                  >
                    {{ treeIssueCount(data.path) }}
                  </span>
                </span>
              </template>
            </el-tree>
          </div>
        </template>
      </aside>

      <div
        v-if="!isNarrow && !treeCollapsed"
        class="col-resize-handle"
        title="拖动调整文件树宽度"
        @mousedown="startTreeResize"
      />

      <main
        v-show="!isNarrow || mobilePane === 'editor'"
        class="panel glass-panel editor-panel"
        data-tour="wb-editor"
      >
        <div class="panel-head editor-head">
          <span class="mono editor-path">{{ currentPath || '选择左侧文件查看源码' }}</span>
          <div class="editor-actions">
            <el-tag v-if="fileDirty" size="small" type="warning">未保存</el-tag>
            <el-button
              v-if="fileDirty"
              size="small"
              @click="onRevertEditorChanges"
            >
              撤销修改
            </el-button>
            <el-button
              size="small"
              type="primary"
              :loading="saveLoading"
              :disabled="!fileDirty || fileBinary"
              @click="onSaveFile"
            >
              保存
            </el-button>
            <el-button
              size="small"
              :loading="diffLoading"
              :disabled="!currentPath || fileBinary"
              @click="onPreviewDiff"
            >
              对比修改
            </el-button>
            <el-tooltip content="对比「已保存版本」和「你现在改的内容」" placement="bottom">
              <span class="diff-help-icon">?</span>
            </el-tooltip>
            <el-button
              data-tour="wb-fix"
              size="small"
              :loading="fixLoading"
              :disabled="!suggestibleCount"
              @click="onSuggestFixes"
            >
              获取改法建议{{ suggestibleCount ? ` (${suggestibleCount})` : '' }}
            </el-button>
            <el-dropdown
              v-if="isJavaFile"
              trigger="click"
              @command="onJavadocCommand"
            >
              <el-button size="small" :loading="docGenLoading">
                一键补充注释
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="file">全文件</el-dropdown-item>
                  <el-dropdown-item command="viewport">当前可见区域</el-dropdown-item>
                  <el-dropdown-item command="selection">选中代码</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <button
              v-if="visibleAuditIssues.length || fixRecords.length"
              type="button"
              class="detail-toggle-btn"
              @click="detailCollapsed = !detailCollapsed"
            >
              {{ detailCollapsed ? '展开详情' : '收起详情' }}
            </button>
          </div>
        </div>
        <el-alert
          v-if="auditScanTruncated && auditScanNote"
          class="audit-scan-alert"
          type="warning"
          :closable="false"
          show-icon
          :title="auditScanNote"
        />

        <div v-if="visibleAuditIssues.length || fixRecords.length" class="fix-workflow">
          <span
            v-for="step in fixWorkflowSteps"
            :key="step.key"
            class="fix-workflow__step"
            :class="{
              'fix-workflow__step--done': step.done,
              'fix-workflow__step--current': step.key === fixWorkflowCurrentKey && !step.done,
            }"
          >
            {{ step.label }}
          </span>
        </div>
        <div v-if="fixBarFixRecord" class="fix-action-bar">
          <div class="fix-action-bar__head">
            <span class="fix-action-bar__step">改法建议 · 步骤 ③</span>
            <el-tag size="small" type="warning">{{ fixBarFixRecord.ruleId }}</el-tag>
            <span class="mono">第 {{ fixBarFixRecord.lineNo }} 行 · {{ fixBarFixRecord.filePath }}</span>
          </div>
          <p class="fix-action-bar__text">{{ fixBarFixRecord.suggestion }}</p>
          <p v-if="fixBarFixRecord.rationale" class="fix-action-bar__rationale">{{ fixBarFixRecord.rationale }}</p>
          <div class="fix-action-bar__actions">
            <el-button type="primary" size="small" @click="onApplyFix">一键写入编辑器</el-button>
            <el-button size="small" :loading="diffLoading" @click="onPreviewDiff">对比修改</el-button>
            <el-button size="small" link type="info" @click="onDismissFix">放弃该条建议</el-button>
            <router-link to="/help#diff" class="fix-action-bar__link">对比说明</router-link>
          </div>
          <p class="fix-action-bar__hint">流程：应用或 AI 改 → 下方看改了什么 → 保存（会自动再检查一遍）→ 你确认</p>
        </div>
        <div v-if="currentPath" class="editor-wrap" :style="{ height: `${editorHeight}px` }">
          <CodeEditor
            ref="codeEditorRef"
            v-model="editorContent"
            :language="fileLanguage ?? undefined"
            :read-only="fileBinary"
            :issue-line-marks="issueLineMarks"
            :fix-highlight-line="fixHighlightLine"
            :focus-line="activeFocusLine"
            @ignore-issue-line="ignoreIssuesAtLine"
          />
        </div>
        <div v-else class="editor-empty">
          <p>在左侧文件树点击文件，在此查看源码</p>
          <p class="editor-empty__hint">点击文件夹名称可展开/收起；检查代码后，有问题的行会在编辑器里标出来</p>
        </div>
        <div
          v-if="currentPath"
          class="editor-resize-handle"
          title="拖动调整代码区高度"
          @mousedown="startEditorResize"
        />
        <Transition name="cw-collapse">
        <div v-if="showLiveDiffPanel" class="diff-preview diff-preview--live">
          <div class="panel-head panel-head--split">
            <span>改了什么（实时对比）</span>
            <span class="diff-preview__hint">
              {{ diffLoading ? '对比中…' : diffPreview?.changed ? '红色=已保存删除 · 绿色=当前新增' : '与已保存版本一致' }}
            </span>
          </div>
          <p v-if="!diffLoading && diffPreview && !diffPreview.changed" class="diff-preview__empty">
            编辑器内容与已保存版本相同，修改代码后将自动显示差异。
          </p>
          <template v-else-if="diffPreview?.changed">
            <pre v-for="(hunk, idx) in diffPreview.hunks" :key="idx" class="diff-hunk">
              <span
                v-for="(line, lineIdx) in hunk.lines"
                :key="lineIdx"
                class="diff-line"
                :class="diffLineClass(line)"
              >{{ line }}
</span>
            </pre>
          </template>
          <div class="diff-preview__actions">
            <el-button size="small" @click="onRevertEditorChanges">撤销修改</el-button>
            <el-button size="small" type="primary" :loading="saveLoading || auditRefreshing" @click="onSaveFile">
              保存并重新检查
            </el-button>
          </div>
        </div>
        </Transition>
        <div v-if="!detailCollapsed && auditIssues.length && !visibleAuditIssues.length" class="audit-fix-zone audit-fix-zone--empty">
          <p class="fix-hint">
            当前问题均已忽略或隐藏。
            <el-button size="small" class="wb-btn-text" @click="clearIgnoredIssues">恢复显示</el-button>
          </p>
        </div>
        <div v-else-if="!detailCollapsed && visibleAuditIssues.length" class="audit-fix-zone">
          <div class="panel-head issue-head">
            <div class="issue-head__title">
              <span>发现的问题 ↔ 改法建议</span>
              <el-button
                v-if="auditHistoryEntries.length >= 2"
                size="small"
                class="wb-btn-text"
                @click="openAuditHistoryDialog"
              >
                与上次对比
              </el-button>
            </div>
            <div class="issue-head__actions">
              <el-switch v-model="hideDependencyIssues" size="small" active-text="隐藏pom依赖提示" />
              <el-switch v-model="hideIgnorableCatch" size="small" active-text="隐藏空catch" />
              <el-button
                v-if="ignorableCatchCount"
                size="small"
                link
                type="warning"
                @click="ignoreAllCatchIssues"
              >
                忽略全部空catch ({{ ignorableCatchCount }})
              </el-button>
              <el-radio-group v-model="auditRiskFilter" size="small">
                <el-radio-button label="ALL">全部</el-radio-button>
                <el-radio-button label="HIGH">高</el-radio-button>
                <el-radio-button label="MEDIUM">中</el-radio-button>
                <el-radio-button label="LOW">低</el-radio-button>
              </el-radio-group>
            </div>
          </div>
          <p v-if="hideDependencyIssues && hiddenDependencyCount" class="fix-hint">
            已隐藏 {{ hiddenDependencyCount }} 条 pom 依赖类提示（多为配置建议，通常不影响运行）。可关闭上方开关查看。
          </p>
          <p v-if="!fixRecords.length" class="fix-hint">
            先点「获取改法建议」，再点「一键写入编辑器」；也可对单条问题点「AI 帮我改」
          </p>
          <div v-if="batchFixRuleGroups.length" class="batch-fix-bar">
            <span class="batch-fix-bar__label">同类批量建议</span>
            <el-button
              v-for="[ruleId, issues] in batchFixRuleGroups.slice(0, 5)"
              :key="ruleId"
              size="small"
              :loading="fixLoading"
              @click="onBatchSuggestFixes(ruleId)"
            >
              {{ ruleId }} · {{ issues.length }} 条
            </el-button>
          </div>
          <div
            v-for="issue in filteredAuditIssues.slice(0, 20)"
            :key="auditIssueKey(issue)"
            class="audit-fix-pair"
            :class="{ 'audit-fix-pair--active': activeIssueKey === auditIssueKey(issue) }"
          >
            <div class="audit-fix-pair__issue" @click="jumpToIssue(issue)">
              <div class="audit-fix-pair__issue-head">
                <el-tag size="small" :type="riskTagType(issue.riskLevel)" :class="riskIssueClass(issue.riskLevel)">
                  {{ riskLevelShort(issue.riskLevel) }}
                </el-tag>
                <el-tag v-if="isDependencyConfigIssue(issue)" size="small" type="info">配置</el-tag>
                <span class="mono audit-fix-pair__path" :title="`${issue.filePath}:${issue.line}`">
                  {{
                    isIssueExpanded(auditIssueKey(issue)) || !issueNeedsCollapse(issue)
                      ? `${issue.filePath}:${issue.line}`
                      : `${formatAuditPath(issue.filePath)}:${issue.line}`
                  }}
                </span>
                <button
                  v-if="issueNeedsCollapse(issue)"
                  type="button"
                  class="audit-action-btn audit-action-btn--toggle"
                  @click.stop="toggleIssueExpanded(auditIssueKey(issue))"
                >
                  {{ isIssueExpanded(auditIssueKey(issue)) ? '收起' : '展开' }}
                </button>
                <button
                  type="button"
                  class="audit-action-btn audit-action-btn--ignore"
                  @click.stop="ignoreIssue(issue)"
                >
                  忽略
                </button>
              </div>
              <p class="audit-fix-pair__desc">
                {{
                  isIssueExpanded(auditIssueKey(issue)) || !shouldCollapseAuditText(issue.description, 88)
                    ? issue.description
                    : `${issue.description.slice(0, 88)}…`
                }}
              </p>
              <p
                v-if="issue.triggerSnippet && (isIssueExpanded(auditIssueKey(issue)) || !shouldCollapseAuditText(issue.triggerSnippet, 100))"
                class="audit-fix-pair__snippet mono"
              >
                {{ issue.triggerSnippet }}
              </p>
              <p
                v-else-if="issue.triggerSnippet && shouldCollapseAuditText(issue.triggerSnippet, 100)"
                class="audit-fix-pair__snippet audit-fix-pair__snippet--folded mono"
              >
                {{ issue.triggerSnippet.slice(0, 100) }}…
                <button
                  type="button"
                  class="audit-action-btn audit-action-btn--toggle audit-action-btn--inline"
                  @click.stop="toggleIssueExpanded(auditIssueKey(issue))"
                >
                  查看代码片段
                </button>
              </p>
            </div>
            <div v-if="fixByIssueKey.get(auditIssueKey(issue))" class="audit-fix-pair__fix">
              <div class="audit-fix-pair__fix-label">对应改法建议</div>
              <p>{{ fixByIssueKey.get(auditIssueKey(issue))!.suggestion }}</p>
              <div class="audit-fix-pair__fix-actions">
                <el-button size="small" type="primary" @click="onSelectFixRecord(fixByIssueKey.get(auditIssueKey(issue))!)">
                  一键写入编辑器
                </el-button>
                <el-button size="small" @click="onAiFixIssue(issue)">AI 帮我改</el-button>
              </div>
            </div>
            <div v-else class="audit-fix-pair__fix audit-fix-pair__fix--empty">
              暂无改法建议 · <el-button size="small" class="wb-btn-text" @click="onAiFixIssue(issue)">让 AI 分析</el-button>
            </div>
          </div>
          <p v-if="filteredAuditIssues.length > 20" class="fix-hint">
            仅展示前 20 条，还有 {{ filteredAuditIssues.length - 20 }} 条（可调整风险筛选或忽略低优先级项）
          </p>
        </div>

        <div v-if="!detailCollapsed && activeFixId && fixRecords.length" class="fix-panel">
          <div class="panel-head">请你确认（当前改法建议）</div>
          <div v-if="hitlState" class="hitl-bar">
            <span>轮次 {{ hitlState.round }}</span>
            <span>状态 {{ hitlStatusLabel(hitlState.lastStatus) }}</span>
            <el-tag v-if="hitlState.terminated" size="small" type="info">已终止</el-tag>
          </div>
          <div class="hitl-actions">
            <el-button size="small" type="success" :loading="hitlLoading" :disabled="hitlState?.terminated" @click="onHitl('APPROVED')">
              确认
            </el-button>
            <el-button size="small" type="warning" :loading="hitlLoading" :disabled="hitlState?.terminated" @click="onHitl('MODIFIED')">
              修改后确认
            </el-button>
            <el-button size="small" type="danger" :loading="hitlLoading" :disabled="hitlState?.terminated" @click="onHitl('REJECTED')">
              驳回
            </el-button>
          </div>
        </div>
      </main>

      <div
        v-if="!isNarrow && showChatPanel"
        class="col-resize-handle"
        title="拖动调整 AI 面板宽度"
        @mousedown="startChatResize"
      />

      <Transition name="cw-slide-x">
      <aside
        v-show="showChatPanel && (!isNarrow || mobilePane === 'chat')"
        class="panel glass-panel chat-panel"
        :style="chatAsideStyle"
      >
        <div class="panel-head chat-head">
          <span><el-icon><ChatDotRound /></el-icon> AI 助手</span>
          <div class="chat-head-actions">
            <el-button size="small" class="wb-btn-text" @click="onNewSession">新建</el-button>
            <el-button size="small" class="wb-btn-text" @click="showChatPanel = false">收起</el-button>
          </div>
        </div>
        <div class="session-tabs">
          <button
            v-for="s in sessions"
            :key="s.id"
            type="button"
            class="session-tab"
            :class="{ active: s.id === activeSessionId }"
            @click="activeSessionId = s.id"
          >
            <span class="session-tab__title">{{ s.title }}</span>
            <span
              class="session-tab__close"
              title="删除会话"
              @click.stop="onDeleteSession(s.id)"
            >×</span>
          </button>
        </div>
        <div ref="chatBox" class="chat-messages">
          <div v-if="!messages.length && !chatLoading" class="chat-empty">
            <p>可问风险与改法；也可说「给选中方法加 Javadoc」，确认后由 Agent 调用接口写入编辑器。</p>
            <el-button type="primary" :loading="chatLoading" @click="onOnboard">
              快速评审
            </el-button>
          </div>
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="chat-bubble"
            :class="`chat-bubble--${msg.role}`"
          >
            <span class="chat-role">{{ msg.role === 'user' ? '你' : msg.role === 'assistant' ? '分析师' : '系统' }}</span>
            <div class="chat-content">{{ msg.content }}</div>
            <div
              v-if="msg.role === 'assistant' && agentActionsForMessage(msg.id).length"
              class="chat-agent-actions"
            >
              <p class="chat-agent-actions__hint">Agent 提议（需你确认后才会改编辑器，不会自动保存）</p>
              <div
                v-for="(action, idx) in agentActionsForMessage(msg.id)"
                :key="`${msg.id}-${idx}`"
                class="chat-agent-action-row"
              >
                <template v-if="action.type === 'CLARIFY'">
                  <p class="chat-agent-clarify-q">{{ action.clarifyQuestion || action.summary }}</p>
                  <div class="chat-agent-clarify-options">
                    <el-button
                      v-for="opt in action.options ?? []"
                      :key="opt.id"
                      size="small"
                      @click="onClarifyOption(msg, opt)"
                    >
                      {{ opt.label }}
                    </el-button>
                  </div>
                </template>
                <template v-else>
                  <span class="chat-agent-action-summary">{{ action.summary }}</span>
                  <span v-if="action.type === 'JAVADOC'" class="chat-agent-action-meta">
                    {{ agentScopeLabel(action.scope ?? 'file') }}
                  </span>
                  <span v-else-if="action.type === 'NAVIGATE'" class="chat-agent-action-meta">
                    {{ action.symbol || action.filePath }}
                  </span>
                  <el-button size="small" type="primary" @click="onExecuteAgentAction(msg, action)">
                    {{ action.type === 'NAVIGATE' ? '跳转' : '执行' }}
                  </el-button>
                </template>
              </div>
              <el-button
                v-if="agentActionsForMessage(msg.id).length"
                class="chat-agent-dismiss"
                size="small"
                link
                @click="clearAgentActions(msg.id)"
              >
                忽略全部提议
              </el-button>
            </div>
            <div
              v-if="msg.role === 'assistant' && isAiFixPendingMessage(msg.id)"
              class="chat-ai-fix-actions"
            >
              <p class="chat-ai-fix-actions__hint">请确认是否采用 AI 的修改（写入后可对比、保存、再请你确认）</p>
              <el-button size="small" type="primary" @click="onAdoptAiFix(msg)">采用 AI 修改</el-button>
              <el-button size="small" link type="warning" @click="onIgnoreWarningFromAi(msg)">忽略此警告</el-button>
              <el-button size="small" link @click="onSkipAiFix(msg)">暂不处理</el-button>
            </div>
          </div>
          <div v-if="chatLoading" class="chat-bubble chat-bubble--assistant chat-bubble--loading">
            <span class="chat-role">分析师</span>
            <div class="chat-typing" aria-hidden="true">
              <span /><span /><span />
            </div>
            <p class="chat-loading-hint">正在阅读…</p>
          </div>
        </div>
        <div class="chat-input">
          <div class="chat-ref-bar">
            <el-button
              size="small"
              :type="chatAttachFile ? 'primary' : 'default'"
              plain
              @click="toggleChatAttachFile"
            >
              @文件
            </el-button>
            <el-button
              size="small"
              :type="chatAttachIssue ? 'primary' : 'default'"
              plain
              @click="attachChatActiveIssue"
            >
              @问题
            </el-button>
            <span v-if="chatAttachFile && currentPath" class="chat-ref-chip mono">📄 {{ currentPath }}</span>
            <span v-if="chatAttachIssue" class="chat-ref-chip">
              ⚠ {{ chatAttachIssue.filePath }}:{{ chatAttachIssue.line }}
              <button type="button" class="chat-ref-chip__clear" @click="clearChatAttachIssue">×</button>
            </span>
          </div>
          <el-input
            v-model="chatInput"
            type="textarea"
            :rows="3"
            placeholder="问改法、加注释、解释代码；@文件 附带当前文件；选中行后可说「给这几行加注释」"
            @keydown.ctrl.enter="onSendChat"
          />
          <el-button type="primary" :loading="chatLoading" @click="onSendChat">发送 (Ctrl+Enter)</el-button>
        </div>
      </aside>
      </Transition>
    </div>

    <InteractiveTour
      v-model="showWorkbenchTour"
      :steps="workbenchTourSteps"
      :on-complete="completeWorkbenchTour"
    />

    <TaskProgressBar
      :visible="taskProgress.visible"
      :title="taskProgress.title"
      :percent="taskProgress.percent"
      :stage="taskProgress.stage"
    />

    <el-dialog v-model="auditHistoryVisible" title="审计历史对比" width="560px" class="audit-history-dialog">
      <template v-if="auditHistoryEntries.length >= 2 && auditHistoryCompare">
        <p class="audit-history-meta">
          对比
          <strong>{{ formatHistoryTime(auditHistoryEntries[1].capturedAt) }}</strong>
          （{{ auditHistoryEntries[1].total }} 条）
          →
          <strong>{{ formatHistoryTime(auditHistoryEntries[0].capturedAt) }}</strong>
          （{{ auditHistoryEntries[0].total }} 条）
        </p>
        <div class="audit-history-stats">
          <el-tag type="success" effect="plain">已消除 {{ auditHistoryCompare.removedKeys.length }}</el-tag>
          <el-tag type="danger" effect="plain">新增 {{ auditHistoryCompare.addedKeys.length }}</el-tag>
          <el-tag type="info" effect="plain">仍存在 {{ auditHistoryCompare.unchangedKeys.length }}</el-tag>
        </div>
        <div v-if="auditHistoryCompare.removedKeys.length" class="audit-history-block">
          <p class="audit-history-block__title">已消除（最多展示 12 条）</p>
          <ul class="audit-history-list">
            <li v-for="k in auditHistoryCompare.removedKeys.slice(0, 12)" :key="'r-' + k">{{ k }}</li>
          </ul>
        </div>
        <div v-if="auditHistoryCompare.addedKeys.length" class="audit-history-block">
          <p class="audit-history-block__title">新增（最多展示 12 条）</p>
          <ul class="audit-history-list">
            <li v-for="k in auditHistoryCompare.addedKeys.slice(0, 12)" :key="'a-' + k">{{ k }}</li>
          </ul>
        </div>
        <p v-if="!auditHistoryCompare.addedKeys.length && !auditHistoryCompare.removedKeys.length" class="fix-hint">
          两次检查结果的问题集合完全一致。
        </p>
      </template>
      <p v-else class="fix-hint">至少完成两次「检查代码」后，可在此查看与上次的差异（记录保存在本机浏览器）。</p>
    </el-dialog>

  </div>
</template>

<style scoped>
.workbench {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 120px);
}

.wb-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 20px 24px;
  flex-wrap: wrap;
}

.wb-header h1 {
  margin: 8px 0 4px;
  font-size: 24px;
}

.wb-sub {
  margin: 0;
  color: var(--cw-text-muted);
  font-size: 13px;
}

.wb-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.wb-stat-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--cw-border);
  background: var(--cw-surface-subtle);
  font-size: 12px;
  color: var(--cw-text-muted);
}

.wb-repo {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--cw-text-muted);
}

.wb-repo a {
  color: var(--cw-primary);
  text-decoration: none;
}

.wb-repo a:hover {
  text-decoration: underline;
}

.wb-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.wb-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 16px;
  flex-wrap: wrap;
  font-size: 12px;
}

.wb-status-bar__crumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0;
  min-width: 0;
  color: var(--cw-text-secondary);
}

.wb-status-bar__project {
  font-weight: 600;
  color: var(--cw-text);
  margin-right: 4px;
}

.wb-status-bar__seg {
  color: var(--cw-text-muted);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wb-status-bar__sep {
  margin: 0 4px;
  opacity: 0.45;
}

.wb-status-bar__placeholder {
  color: var(--cw-text-muted);
  margin-left: 4px;
}

.wb-status-bar__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}

.wb-status-chip {
  font-weight: 500;
}

.wb-status-bar__time {
  color: var(--cw-text-muted);
  white-space: nowrap;
}

.dropdown-caret {
  margin-left: 4px;
  font-size: 10px;
  opacity: 0.7;
}

.workflow-hint {
  margin: 0;
  padding: 10px 16px;
  font-size: 13px;
  color: var(--cw-text-muted);
  line-height: 1.6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.workflow-hint-toggle {
  margin: 0;
  width: 100%;
  padding: 8px 16px;
  border: none;
  text-align: left;
  font-size: 12px;
  color: var(--cw-text-muted);
  cursor: pointer;
}

.workflow-hint-toggle:hover {
  color: var(--cw-primary);
}

.score-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  font-size: 14px;
}

.score-value {
  font-size: 22px;
  color: var(--cw-primary);
}

.score-summary {
  color: var(--cw-text-muted);
}

.arch-panel,
.run-panel,
.eval-panel {
  padding: 16px 20px;
}

.arch-stats {
  font-size: 12px;
  color: var(--cw-text-muted);
}

.panel-head--split {
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.panel-head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

.arch-tabs {
  margin-bottom: 12px;
}

.arch-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.arch-zoom-label {
  font-size: 12px;
  color: var(--cw-text-muted);
  min-width: 42px;
  text-align: center;
}

.arch-svg-wrap {
  overflow: auto;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--cw-border);
  background: var(--cw-surface-subtle);
  max-height: 420px;
}

.arch-svg-inner {
  transform-origin: top left;
  min-width: min-content;
}

.mobile-pane-bar {
  padding: 10px 14px;
}

.wb-grid--stacked {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.run-summary {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--cw-text-muted);
}

.sandbox-check {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.sandbox-check__input {
  flex: 1;
  min-width: 180px;
}

.sandbox-msg {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--cw-text-muted);
}

.deploy-guide,
.eval-markdown {
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--cw-border);
  background: var(--cw-surface-subtle);
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  max-height: 200px;
  overflow: auto;
}

.eval-score {
  margin-left: auto;
  font-size: 13px;
  color: var(--cw-primary);
}

.issue-head {
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 8px;
  margin-bottom: 4px;
  border-bottom: 1px dashed var(--cw-border);
}

.issue-head__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.issue-head__title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-weight: 600;
}

.batch-fix-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 8px 0 10px;
  padding: 8px 10px;
  border-radius: var(--cw-radius-sm);
  background: var(--cw-surface-subtle);
  border: 1px dashed var(--cw-border);
}

.batch-fix-bar__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--cw-text-secondary);
}

.audit-history-meta {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--cw-text-secondary);
  line-height: 1.6;
}

.audit-history-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.audit-history-block__title {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--cw-text-muted);
}

.audit-history-list {
  margin: 0 0 12px;
  padding-left: 18px;
  font-size: 11px;
  font-family: var(--cw-mono);
  color: var(--cw-text-secondary);
  line-height: 1.55;
  max-height: 140px;
  overflow: auto;
}

.audit-fix-zone {
  margin-top: 12px;
  max-height: 420px;
  overflow: auto;
  padding: 12px;
  border-radius: var(--cw-radius-sm);
  border: 1px solid var(--cw-border);
  background: var(--cw-bg-elevated);
}

.audit-fix-pair {
  margin-bottom: 12px;
  border-radius: 10px;
  border: 1px solid var(--cw-border-strong);
  overflow: hidden;
  background: var(--cw-bg-elevated);
  box-shadow: var(--cw-shadow-sm);
}

.audit-fix-pair--active {
  border-color: rgba(251, 191, 36, 0.55);
  box-shadow: 0 0 0 1px rgba(251, 191, 36, 0.2);
}

.audit-fix-zone--empty {
  padding: 12px 14px;
}

.audit-fix-pair__issue {
  padding: 10px 12px;
  background: var(--cw-surface-subtle);
  cursor: pointer;
}

.audit-action-btn {
  flex-shrink: 0;
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  border-radius: 4px;
  cursor: pointer;
  font-family: inherit;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.audit-action-btn--toggle {
  color: #434343;
  background: #fff;
  border: 1px solid #d9d9d9;
}

.audit-action-btn--toggle:hover {
  color: var(--cw-primary-active);
  border-color: var(--cw-primary);
  background: var(--cw-primary-bg);
}

.audit-action-btn--ignore {
  color: #ad4e00;
  background: #fff;
  border: 1px solid #ffd591;
}

.audit-action-btn--ignore:hover {
  color: #873800;
  border-color: #ffa940;
  background: #fff7e6;
}

.audit-action-btn--inline {
  margin-left: 6px;
  vertical-align: middle;
}

.detail-toggle-btn {
  flex-shrink: 0;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #434343;
  background: #fff;
  border: 1px solid #d9d9d9;
  border-radius: var(--cw-radius-sm);
  cursor: pointer;
  font-family: inherit;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.detail-toggle-btn:hover {
  color: var(--cw-primary-active);
  border-color: var(--cw-primary);
  background: var(--cw-primary-bg);
}

html.dark .detail-toggle-btn {
  color: rgba(255, 255, 255, 0.85);
  background: #262626;
  border-color: #424242;
}

html.dark .audit-action-btn--toggle {
  color: rgba(255, 255, 255, 0.85);
  background: #262626;
  border-color: #424242;
}

html.dark .audit-action-btn--toggle:hover {
  color: #69b1ff;
  border-color: #177ddc;
  background: rgba(22, 119, 255, 0.15);
}

html.dark .audit-action-btn--ignore {
  color: #ffa940;
  background: #262626;
  border-color: rgba(250, 173, 20, 0.45);
}

html.dark .audit-action-btn--ignore:hover {
  background: rgba(250, 173, 20, 0.12);
}

.audit-fix-pair__issue-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.audit-fix-pair__path {
  flex: 1 1 160px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.audit-fix-pair__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
}

.audit-fix-pair__snippet {
  margin: 8px 0 0;
  padding: 8px;
  border-radius: 6px;
  background: var(--cw-surface-code);
  font-size: 11px;
  line-height: 1.45;
  color: var(--cw-diff-del);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow: auto;
}

.audit-fix-pair__snippet--folded {
  color: var(--cw-text-muted);
}

.audit-fix-pair__fix {
  padding: 10px 12px;
  background: var(--cw-fix-surface);
  border-top: 1px solid var(--cw-fix-border);
  font-size: 13px;
  line-height: 1.55;
}

.audit-fix-pair__fix--empty {
  color: var(--cw-text-muted);
  font-size: 12px;
}

.audit-fix-pair__fix-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--cw-success);
  margin-bottom: 4px;
}

.audit-fix-pair__fix-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.sandbox-output {
  margin: 0 0 10px;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid var(--cw-border);
  background: var(--cw-surface-panel-strong);
  font-size: 11px;
  line-height: 1.45;
  max-height: 160px;
  overflow: auto;
  white-space: pre-wrap;
}

.wb-grid {
  width: 100%;
  min-width: 0;
}

.wb-grid--row {
  display: flex;
  flex-direction: row;
  align-items: stretch;
  gap: 0 8px;
  min-width: 0;
  overflow: hidden;
}

.wb-grid--row > .editor-panel {
  flex: 1 1 0;
  min-width: 0;
  max-width: 100%;
}

.wb-grid--row > .tree-panel,
.wb-grid--row > .chat-panel {
  min-width: 0;
  max-width: 100%;
}

.wb-grid--row > .col-resize-handle {
  flex: 0 0 5px;
  width: 5px;
}

.col-resize-handle {
  width: 5px;
  margin: 8px 0;
  border-radius: 999px;
  cursor: col-resize;
  background: transparent;
  transition: background 0.15s;
}

.col-resize-handle:hover,
.col-resize-handle:active {
  background: var(--cw-primary-border);
}

.panel-head--tree {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 32px;
  flex-shrink: 0;
}

.panel-head__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-toggle-btn {
  flex-shrink: 0;
}

.tree-panel--collapsed {
  padding: 8px 4px;
  overflow: hidden;
}

.tree-panel--collapsed .panel-head--tree {
  flex-direction: column;
  justify-content: flex-start;
  align-items: center;
  margin-bottom: 0;
  gap: 0;
}

.panel {
  padding: 14px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.tree-panel {
  max-height: calc(100vh - 220px);
}

.tree-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  overflow-x: auto;
  padding-right: 4px;
}

.tree-scroll :deep(.el-tree) {
  min-width: max-content;
}

.tree-scroll :deep(.el-tree-node__content) {
  min-width: max-content;
}

.tree-node-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: calc(100% - 4px);
  padding: 2px 0;
}

.tree-node-row--has-issue .tree-node-label {
  font-weight: 500;
}

.tree-density-bar {
  flex-shrink: 0;
  max-width: 28px;
  height: 12px;
  border-radius: 2px;
  opacity: 0.85;
}

.tree-density-bar.risk-high {
  background: linear-gradient(90deg, var(--cw-risk-high), rgba(207, 19, 34, 0.35));
}

.tree-density-bar.risk-medium {
  background: linear-gradient(90deg, var(--cw-risk-medium), rgba(212, 107, 8, 0.35));
}

.tree-density-bar.risk-low {
  background: linear-gradient(90deg, var(--cw-risk-low), rgba(212, 177, 6, 0.3));
}

.tree-node-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-issue-badge {
  flex-shrink: 0;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--cw-risk-high-bg);
  color: var(--cw-risk-high);
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.tree-issue-badge.risk-medium {
  background: var(--cw-risk-medium-bg);
  color: var(--cw-risk-medium);
}

.tree-issue-badge.risk-low {
  background: var(--cw-risk-low-bg);
  color: var(--cw-risk-low);
}

.audit-scan-alert {
  margin-bottom: 8px;
}

.audit-scan-alert :deep(.el-alert__title) {
  font-size: 12px;
  line-height: 1.5;
}

.wb-btn-text {
  --el-button-text-color: var(--cw-primary);
  --el-button-hover-text-color: var(--cw-primary-hover);
  --el-button-hover-bg-color: var(--cw-primary-bg);
  --el-button-active-text-color: var(--cw-primary-active);
  font-weight: 500;
}

.fix-workflow {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.fix-workflow__step {
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid var(--cw-workflow-border);
  background: var(--cw-workflow-bg);
  color: var(--cw-text-muted);
}

.fix-workflow__step--done {
  border-color: #b7eb8f;
  color: var(--cw-success);
  background: #f6ffed;
}

.fix-workflow__step--current {
  border-color: var(--cw-primary-border);
  color: var(--cw-primary);
  background: var(--cw-primary-bg);
}

.fix-action-bar__step {
  font-size: 11px;
  font-weight: 600;
  color: var(--cw-primary);
  margin-right: 6px;
}

.fix-action-bar__hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--cw-text-muted);
}

.chat-agent-actions {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--cw-border);
}

.chat-agent-actions__hint {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--cw-text-muted);
}

.chat-agent-action-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.chat-agent-action-summary {
  font-size: 13px;
  font-weight: 500;
}

.chat-agent-action-meta {
  font-size: 12px;
  color: var(--cw-text-muted);
}

.chat-agent-clarify-q {
  width: 100%;
  margin: 0 0 6px;
  font-size: 13px;
}

.chat-agent-clarify-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chat-agent-dismiss {
  margin-top: 4px;
}

.chat-ai-fix-actions {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--cw-primary-border);
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.chat-ai-fix-actions__hint {
  width: 100%;
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--cw-text-muted);
}

:deep(.el-tag.risk-high) {
  --el-tag-bg-color: var(--cw-risk-high-bg);
  --el-tag-border-color: var(--cw-risk-high);
  --el-tag-text-color: var(--cw-risk-high);
}

:deep(.el-tag.risk-medium) {
  --el-tag-bg-color: var(--cw-risk-medium-bg);
  --el-tag-border-color: var(--cw-risk-medium);
  --el-tag-text-color: var(--cw-risk-medium);
}

:deep(.el-tag.risk-low) {
  --el-tag-bg-color: var(--cw-risk-low-bg);
  --el-tag-border-color: var(--cw-risk-low);
  --el-tag-text-color: var(--cw-risk-low);
}

:deep(.el-tree-node__content) {
  height: 32px;
  border-radius: 8px;
}

:deep(.el-tree-node__content:hover) {
  background: var(--cw-primary-bg);
}

.editor-panel {
  min-height: 0;
}

.editor-wrap {
  flex-shrink: 0;
  min-height: 220px;
  max-height: 720px;
  overflow: hidden;
}

.editor-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 220px;
  padding: 24px;
  border-radius: 10px;
  border: 1px dashed var(--cw-border);
  color: var(--cw-text-muted);
  text-align: center;
}

.editor-empty__hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
}

.editor-resize-handle {
  height: 10px;
  margin: 8px 0 4px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.18);
  cursor: row-resize;
  flex-shrink: 0;
}

.editor-resize-handle:hover {
  background: var(--cw-primary-border);
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
  color: var(--cw-text-muted);
}

.tree-filter {
  margin-bottom: 10px;
}

.editor-head {
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.editor-path {
  flex: 1 1 180px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1 1 320px;
  justify-content: flex-end;
  max-width: 100%;
}

.fix-panel {
  margin-top: 12px;
  max-height: 280px;
  overflow: auto;
}

.fix-hint {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--cw-text-muted);
}

.hitl-bar {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--cw-text-muted);
  margin-bottom: 8px;
}

.hitl-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.fix-item--active {
  border-color: var(--cw-primary) !important;
}

.fix-rationale {
  color: var(--cw-text-muted);
  font-size: 11px;
}

.diff-preview {
  margin-top: 10px;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid var(--cw-border);
  background: var(--cw-surface-panel);
}

.diff-preview--live {
  border-color: var(--cw-primary-border);
}

.diff-preview__empty {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--cw-text-muted);
}

.diff-preview__hint {
  font-size: 11px;
  font-weight: 400;
  color: var(--cw-text-muted);
  margin-left: 8px;
}

.diff-preview__actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.diff-hunk {
  margin: 0 0 8px;
  padding: 8px;
  border-radius: 8px;
  background: var(--cw-surface-panel-strong);
  border: 1px solid var(--cw-border);
  font-size: 11px;
  line-height: 1.45;
  overflow: auto;
  max-height: 160px;
}

.diff-line {
  display: block;
  white-space: pre-wrap;
  font-family: var(--cw-mono);
}

.diff-line--add {
  color: var(--cw-diff-add);
  background: var(--cw-diff-add-bg);
}

.diff-line--del {
  color: var(--cw-diff-del);
  background: var(--cw-diff-del-bg);
}

.diff-line--ctx {
  color: var(--cw-text-muted);
}

.diff-help-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1px solid var(--cw-border);
  font-size: 12px;
  color: var(--cw-text-muted);
  cursor: help;
}

.fix-action-bar {
  margin-bottom: 10px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--cw-fix-border);
  background: var(--cw-fix-surface);
}

.fix-action-bar__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--cw-text-muted);
}

.fix-action-bar__text {
  margin: 0 0 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--cw-text);
}

.fix-action-bar__rationale {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--cw-text-muted);
}

.fix-action-bar__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.fix-action-bar__link {
  margin-left: auto;
  font-size: 12px;
  color: var(--cw-primary);
  text-decoration: none;
}

.fix-action-bar__link:hover {
  text-decoration: underline;
}

.issue-list {
  margin-top: 12px;
  max-height: 160px;
  overflow: auto;
}

.issue-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  width: 100%;
  text-align: left;
  padding: 8px 10px;
  margin-bottom: 6px;
  border-radius: 8px;
  border: 1px solid var(--cw-border);
  background: var(--cw-surface-subtle);
  color: var(--cw-text);
  cursor: pointer;
  font-size: 12px;
}

.issue-item--high {
  border-color: rgba(248, 113, 113, 0.4);
}

.issue-item--medium {
  border-color: rgba(251, 191, 36, 0.35);
}

.chat-panel {
  max-height: calc(100vh - 220px);
}

.chat-head {
  justify-content: space-between;
}

.chat-head-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.session-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.session-tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--cw-border);
  background: transparent;
  color: var(--cw-text-muted);
  border-radius: 999px;
  padding: 4px 8px 4px 10px;
  font-size: 12px;
  cursor: pointer;
  max-width: 160px;
}

.session-tab__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-tab__close {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  line-height: 16px;
  text-align: center;
  border-radius: 50%;
  font-size: 14px;
  opacity: 0.55;
}

.session-tab__close:hover {
  opacity: 1;
  background: var(--cw-risk-high-bg);
  color: var(--cw-risk-high);
}

.session-tab.active {
  color: var(--cw-primary);
  border-color: var(--cw-primary-border);
  background: var(--cw-primary-bg);
}

.chat-messages {
  flex: 1;
  overflow: auto;
  padding-right: 4px;
  margin-bottom: 10px;
}

.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 180px;
  padding: 16px;
  text-align: center;
  color: var(--cw-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.chat-bubble {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--cw-border);
  background: var(--cw-bg-elevated);
}

.chat-bubble--assistant {
  border-color: var(--cw-primary-border);
  background: var(--cw-primary-bg);
}

.chat-bubble--user {
  border-color: var(--cw-border);
  background: var(--cw-surface-subtle);
}

.chat-role {
  display: block;
  font-size: 11px;
  color: var(--cw-text-muted);
  margin-bottom: 6px;
}

.chat-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.65;
  color: var(--cw-text);
}

.chat-sections {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat-section {
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--cw-surface-subtle);
  border: 1px solid var(--cw-border);
}

.chat-section__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--cw-primary);
  margin-bottom: 4px;
}

.chat-section__body {
  font-size: 13px;
  line-height: 1.6;
  color: var(--cw-text);
}

.chat-bubble--loading {
  opacity: 0.92;
}

.chat-typing {
  display: flex;
  gap: 6px;
  margin: 4px 0 8px;
}

.chat-typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--cw-primary);
  animation: chat-dot 1.2s infinite ease-in-out;
}

.chat-typing span:nth-child(2) {
  animation-delay: 0.15s;
}

.chat-typing span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes chat-dot {
  0%,
  80%,
  100% {
    opacity: 0.35;
    transform: scale(0.85);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}

.chat-loading-hint {
  margin: 0;
  font-size: 12px;
  color: var(--cw-text-muted);
}

.chat-ref-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.chat-ref-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--cw-primary-bg);
  border: 1px solid var(--cw-primary-border);
  font-size: 11px;
  color: var(--cw-primary-active);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-ref-chip__clear {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 0 2px;
  font-size: 14px;
  line-height: 1;
}

.chat-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

</style>
