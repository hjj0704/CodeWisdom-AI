<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
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
  type ChatMessage,
  type ChatSession,
} from '../api/chat'
import { fetchProjectScore, runProjectAudit, type AuditIssueItem, type RiskLevel } from '../api/audit'
import { fetchProjectArchitecture } from '../api/architecture'
import {
  fetchRunProfile,
  runSandboxDemo,
  validateSandboxCommand,
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
import { parseAssistantSections } from '../utils/chatFormat'
import { applyCodeAtLine, extractSuggestedCode, tryApplySuggestion } from '../utils/fixApply'
import { auditIssueKey, isIgnorableAuditIssue } from '../utils/audit'
import { riskIssueClass, riskLevelLabel, riskLevelShort, riskTagType } from '../utils/riskLevel'
import type { IssueLineMark } from '../components/CodeEditor.vue'
import InteractiveTour from '../components/InteractiveTour.vue'
import { workbenchTourSteps } from '../data/onboardingSteps'
import { completeWorkbenchTour, isWorkbenchTourPending } from '../utils/onboarding'

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
const projectId = computed(() => Number(route.params.projectId))

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
const chatLoading = ref(false)
const chatBox = ref<HTMLElement | null>(null)
const pendingAiFixByMessageId = ref<Map<number, AuditIssueItem>>(new Map())
const fixStepApplied = ref(false)

const auditIssues = ref<AuditIssueItem[]>([])
const auditScanNote = ref('')
const auditRefreshing = ref(false)
let diffPreviewTimer: ReturnType<typeof setTimeout> | null = null
const auditRiskFilter = ref<'ALL' | RiskLevel>('ALL')
const hideIgnorableCatch = ref(true)
const ignoredIssueKeys = ref<Set<string>>(new Set())
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

const isNarrow = ref(false)
const mobilePane = ref<'tree' | 'editor' | 'chat'>('editor')
const archZoom = ref(1)
const showChatPanel = ref(false)
const editorHeight = ref(420)
const showWorkbenchTour = ref(false)
const workbenchTourTriggered = ref(false)
const savedLayout = readLayoutPrefs()
const treeRef = ref<{ setCurrentKey?: (key: string) => void } | null>(null)
const codeEditorRef = ref<{ scrollToLine?: (line: number) => void } | null>(null)
const treeCollapsed = ref(savedLayout.treeCollapsed ?? false)
const archCollapsed = ref(false)
const detailCollapsed = ref(false)
const hintCollapsed = ref(savedLayout.hintCollapsed ?? false)
const treeWidth = ref(savedLayout.treeWidth ?? 280)
const chatWidth = ref(savedLayout.chatWidth ?? 380)

const gridStyle = computed(() => {
  if (isNarrow.value) return {}
  const treeCol = treeCollapsed.value ? '48px' : `${treeWidth.value}px`
  const chatCol = showChatPanel.value ? `${chatWidth.value}px` : '0px'
  return { gridTemplateColumns: `${treeCol} 5px minmax(0, 1fr) 5px ${chatCol}` }
})

watch([treeWidth, chatWidth, treeCollapsed, hintCollapsed], () => {
  saveLayoutPrefs({
    treeWidth: treeWidth.value,
    chatWidth: chatWidth.value,
    treeCollapsed: treeCollapsed.value,
    hintCollapsed: hintCollapsed.value,
  })
})

const visibleAuditIssues = computed(() => {
  return auditIssues.value.filter((issue) => {
    const key = auditIssueKey(issue)
    if (ignoredIssueKeys.value.has(key)) return false
    if (hideIgnorableCatch.value && isIgnorableAuditIssue(issue)) return false
    return true
  })
})

const issueCountByPath = computed(() => {
  const map: Record<string, number> = {}
  for (const issue of visibleAuditIssues.value) {
    map[issue.filePath] = (map[issue.filePath] ?? 0) + 1
  }
  return map
})

const issueLineMarks = computed((): IssueLineMark[] => {
  if (!currentPath.value || fileBinary.value) return []
  const maxLine = Math.max(1, editorContent.value.split('\n').length)
  return visibleAuditIssues.value
    .filter((i) => i.filePath === currentPath.value && i.line >= 1 && i.line <= maxLine)
    .map((i) => ({ line: i.line, risk: i.riskLevel }))
})

const worstRiskByPath = computed(() => {
  const map: Record<string, RiskLevel> = {}
  for (const issue of visibleAuditIssues.value) {
    const cur = map[issue.filePath]
    if (!cur || riskWeight(issue.riskLevel) > riskWeight(cur)) {
      map[issue.filePath] = issue.riskLevel
    }
  }
  return map
})

const fixWorkflowSteps = computed(() => {
  const hasAudit = auditIssues.value.length > 0
  const hasSuggest = fixRecords.value.length > 0
  const hasEdit = fixStepApplied.value && fileDirty.value
  const hasDiff = !!diffPreview.value?.changed
  const hitlDone =
    hitlState.value?.lastStatus === 'APPROVED' ||
    hitlState.value?.lastStatus === 'MODIFIED' ||
    hitlState.value?.terminated
  return [
    { key: 'audit', label: '① 运行审计', done: hasAudit },
    { key: 'suggest', label: '② 生成修复建议', done: hasSuggest },
    { key: 'apply', label: '③ 应用 / AI 改代码', done: hasEdit },
    { key: 'diff', label: '④ 预览 Diff', done: hasDiff },
    { key: 'hitl', label: '⑤ 保存并人工确认', done: hitlDone },
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
  auditIssues.value.filter((i) => isIgnorableAuditIssue(i) && !ignoredIssueKeys.value.has(auditIssueKey(i))).length,
)
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

const displayMessages = computed(() =>
  messages.value.map((m) => ({
    ...m,
    sections: m.role === 'assistant' ? parseAssistantSections(m.content) : null,
  })),
)

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
    ignoredIssueKeys.value = new Set(JSON.parse(raw) as string[])
  } catch {
    ignoredIssueKeys.value = new Set()
  }
}

function persistIgnoredIssues() {
  localStorage.setItem(ignoredIssuesStorageKey(), JSON.stringify([...ignoredIssueKeys.value]))
}

function ignoreIssue(issue: AuditIssueItem) {
  ignoredIssueKeys.value = new Set([...ignoredIssueKeys.value, auditIssueKey(issue)])
  persistIgnoredIssues()
  if (activeIssueKey.value === auditIssueKey(issue)) {
    activeIssueKey.value = null
    activeFocusLine.value = null
  }
  ElMessage.success('已忽略该审计项')
}

function ignoreAllCatchIssues() {
  const keys = auditIssues.value
    .filter((i) => isIgnorableAuditIssue(i) && !ignoredIssueKeys.value.has(auditIssueKey(i)))
    .map(auditIssueKey)
  if (!keys.length) {
    ElMessage.info('没有可忽略的空 catch 项')
    return
  }
  ignoredIssueKeys.value = new Set([...ignoredIssueKeys.value, ...keys])
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
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载项目失败')
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
    if (!workbenchTourTriggered.value && isWorkbenchTourPending()) {
      workbenchTourTriggered.value = true
      await nextTick()
      setTimeout(() => {
        showWorkbenchTour.value = true
      }, 400)
    }
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
  try {
    const report = await runProjectAudit(projectId.value)
    const prevTotal = auditIssues.value.length
    auditIssues.value = report.issues
    auditScanNote.value = report.scanNote ?? ''
    if (issueKey) {
      const stillThere = report.issues.some((i) => auditIssueKey(i) === issueKey)
      clearFixStateIfResolved(issueKey)
      if (!stillThere) {
        ElMessage.success('该审计项已消除，列表已更新')
      } else {
        ElMessage.warning('已保存，但规则仍命中该行，请继续修改或点「忽略」')
      }
    } else if (report.issues.length < prevTotal) {
      ElMessage.success(`审计已更新：${prevTotal} → ${report.issues.length} 个问题`)
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存后刷新审计失败，请手动运行审计')
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
  if (diffPreviewTimer) clearTimeout(diffPreviewTimer)
  if (!currentPath.value || fileBinary.value || !fileDirty.value) {
    diffPreview.value = null
    return
  }
  diffPreviewTimer = setTimeout(() => {
    onPreviewDiff(true)
  }, 400)
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

async function onSuggestFixes() {
  const pool =
    auditRiskFilter.value === 'ALL' ? visibleAuditIssues.value : filteredAuditIssues.value
  if (!pool.length) {
    ElMessage.warning('没有可处理的问题（可能已全部忽略、隐藏或当前筛选无结果）')
    return
  }
  fixLoading.value = true
  try {
    const picked = [...pool].sort((a, b) => riskWeight(b.riskLevel) - riskWeight(a.riskLevel)).slice(0, 8)
    await suggestFixes(projectId.value, picked)
    await loadFixRecords()
    if (!fixRecords.value.length) {
      ElMessage.warning('未生成修复建议')
      return
    }
    ElMessage.success(`已生成/更新修复建议，共 ${fixRecords.value.length} 条记录`)
    activeFixId.value = fixRecords.value[0]?.id ?? null
    fixStepApplied.value = false
    await loadHitlState()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '生成修复建议失败')
  } finally {
    fixLoading.value = false
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
      ElMessage.error(e instanceof Error ? e.message : 'Diff 预览失败')
    }
  } finally {
    diffLoading.value = false
  }
}

async function onHitl(status: 'APPROVED' | 'MODIFIED' | 'REJECTED') {
  if (!activeFixId.value) {
    ElMessage.warning('请先选择一条修复建议再确认 HITL')
    return
  }
  if ((status === 'APPROVED' || status === 'MODIFIED') && !fileDirty.value) {
    ElMessage.warning('当前无未保存修改，请先应用建议或 AI 修改并预览 Diff')
    return
  }
  if (hitlState.value?.terminated) {
    ElMessage.info('本条建议的 HITL 已结束，请选择其他建议或重新生成')
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
      ElMessage.warning(`HITL：已驳回（轮次 ${hitlState.value.round}）`)
    } else {
      ElMessage.success(`HITL：${status}`)
    }
    if (status === 'APPROVED' || status === 'MODIFIED') {
      const saved = await onSaveFile()
      if (saved) fixStepApplied.value = false
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : 'HITL 提交失败')
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
  try {
    const report = await runProjectAudit(projectId.value)
    auditIssues.value = report.issues
    auditScanNote.value = report.scanNote ?? ''
    fixRecords.value = []
    activeFixId.value = null
    diffPreview.value = null
    fixStepApplied.value = false
    pendingAiFixByMessageId.value = new Map()
    await loadHitlState()
    ElMessage.success(`审计完成：${report.total} 个问题（高 ${report.highCount}）— 请重新生成修复建议`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '审计失败')
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

async function onSendChat() {
  if (!chatInput.value.trim() || !activeSessionId.value) return
  chatLoading.value = true
  try {
    const reply = await sendChat(activeSessionId.value, chatInput.value.trim())
    messages.value.push(reply.userMessage, reply.assistantMessage)
    chatInput.value = ''
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
    const reply = await sendChat(activeSessionId.value!, prompt)
    messages.value.push(reply.userMessage, reply.assistantMessage)
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
  ElMessage.success('已写入编辑器，请确认 Diff 后点「保存并刷新审计」')
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
  if (!runProfile.value || runProfile.value.capability === 'HEAVY') return
  const cmd = sandboxCommand.value.trim() || 'mvn -q -DskipTests compile'
  sandboxRunLoading.value = true
  sandboxRunOutput.value = null
  try {
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

async function resetWorkbenchForProject() {
  auditIssues.value = []
  auditScanNote.value = ''
  fixRecords.value = []
  activeFixId.value = null
  diffPreview.value = null
  hitlState.value = null
  fixStepApplied.value = false
  pendingAiFixByMessageId.value = new Map()
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

watch(projectId, async (id, prev) => {
  if (prev === undefined || id === prev) return
  await resetWorkbenchForProject()
  await loadProject()
  await loadSessions()
  await loadFixRecords()
  await loadHitlState()
})

watch(chatLoading, async (loading) => {
  if (!loading) return
  await nextTick()
  chatBox.value?.scrollTo({ top: chatBox.value.scrollHeight, behavior: 'smooth' })
})

onMounted(async () => {
  updateLayout()
  window.addEventListener('resize', onResize)
  if (!Number.isFinite(projectId.value) || projectId.value <= 0) {
    router.replace('/import')
    return
  }
  await loadProject()
  loadDismissedFixes()
  loadIgnoredIssues()
  await loadSessions()
  await loadFixRecords()
  await loadHitlState()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
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
          运行审计
        </el-button>
        <el-button :loading="archLoading" @click="onLoadArchitecture">架构图</el-button>
        <el-button
          data-tour="wb-ai"
          type="success"
          plain
          :icon="ChatDotRound"
          @click="openChatPanel"
        >
          AI 分析
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

    <div v-if="!hintCollapsed" class="workflow-hint glass-panel">
      <span>推荐流程：① 运行审计 → ② 查看架构图 → ③ 生成修复建议 → ④ 人工确认（HITL）→ ⑤ 下载或继续对话</span>
      <el-button size="small" link type="primary" @click="hintCollapsed = true">收起</el-button>
    </div>
    <button v-else type="button" class="workflow-hint-toggle glass-panel" @click="hintCollapsed = false">
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
          <el-button size="small" link @click="archCollapsed = !archCollapsed">
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
          <el-button size="small" link type="primary" @click="archZoom = 1">重置</el-button>
          <el-button size="small" :icon="Download" @click="downloadArchSvg">下载 SVG</el-button>
          <el-button size="small" link type="primary" @click="downloadArchSource">下载 Mermaid</el-button>
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
        <el-button size="small" link type="primary" @click="runPanelCollapsed = !runPanelCollapsed">
          {{ runPanelCollapsed ? '展开' : '收起' }}
        </el-button>
      </div>
      <template v-if="!runPanelCollapsed">
        <p class="run-summary">{{ runProfile.summary }}</p>
        <div v-if="runProfile.capability !== 'HEAVY'" class="sandbox-check">
          <el-input
            v-model="sandboxCommand"
            size="small"
            placeholder="如 mvn -q -DskipTests compile"
            class="sandbox-check__input"
          />
          <el-button size="small" :loading="sandboxLoading" @click="onValidateSandbox">校验</el-button>
          <el-button size="small" type="primary" :loading="sandboxRunLoading" @click="onSandboxDemo">
            在线演示
          </el-button>
          <el-tag v-if="sandboxResult" size="small" :type="sandboxResult.allowed ? 'success' : 'danger'">
            {{ sandboxResult.allowed ? '通过' : '拒绝' }}
          </el-tag>
        </div>
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
        <el-button size="small" link type="primary" @click="evalPanelCollapsed = !evalPanelCollapsed">
          {{ evalPanelCollapsed ? '展开' : '收起' }}
        </el-button>
      </div>
      <pre v-if="!evalPanelCollapsed" class="eval-markdown">{{ evalReport.reportMarkdown }}</pre>
    </div>

    <div v-if="isNarrow" class="mobile-pane-bar glass-panel">
      <el-radio-group v-model="mobilePane" size="small">
        <el-radio-button label="tree">文件树</el-radio-button>
        <el-radio-button label="editor">代码</el-radio-button>
        <el-radio-button v-if="showChatPanel" label="chat">AI 分析</el-radio-button>
      </el-radio-group>
    </div>

    <div
      class="wb-grid"
      :class="{ 'wb-grid--stacked': isNarrow }"
      :style="gridStyle"
    >
      <aside
        v-show="!isNarrow || mobilePane === 'tree'"
        class="panel glass-panel tree-panel"
        :class="{ 'tree-panel--collapsed': treeCollapsed }"
        data-tour="wb-tree"
      >
        <div class="panel-head panel-head--split">
          <span v-if="!treeCollapsed"><el-icon><FolderOpened /></el-icon> 文件树</span>
          <span v-else class="tree-collapsed-label">文件</span>
          <el-button size="small" link @click="treeCollapsed = !treeCollapsed">
            {{ treeCollapsed ? '→' : '← 收起' }}
          </el-button>
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
                <span class="tree-node-row">
                  <span class="tree-node-label">{{ data.label }}</span>
                  <span
                    v-if="data.isLeaf && issueCountByPath[data.path]"
                    class="tree-issue-badge"
                    :class="riskIssueClass(worstRiskByPath[data.path] ?? 'LOW')"
                    :title="`${issueCountByPath[data.path]} 个审计问题`"
                  >
                    {{ issueCountByPath[data.path] }}
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
          <span class="mono">{{ currentPath || '选择左侧文件查看源码' }}</span>
          <div class="editor-actions">
            <el-tag v-if="fileDirty" size="small" type="warning">未保存</el-tag>
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
              预览 Diff
            </el-button>
            <el-tooltip content="对比「已保存版本」与「编辑器当前内容」，见帮助 → 预览 Diff" placement="bottom">
              <span class="diff-help-icon">?</span>
            </el-tooltip>
            <el-button
              data-tour="wb-fix"
              size="small"
              :loading="fixLoading"
              :disabled="!auditIssues.length"
              @click="onSuggestFixes"
            >
              生成修复建议
            </el-button>
            <el-button
              v-if="auditIssues.length || fixRecords.length"
              size="small"
              link
              @click="detailCollapsed = !detailCollapsed"
            >
              {{ detailCollapsed ? '展开详情' : '收起详情' }}
            </el-button>
          </div>
        </div>
        <div v-if="auditScanNote" class="audit-scan-note glass-panel">
          <el-icon><Search /></el-icon>
          <span>{{ auditScanNote }}</span>
        </div>

        <div v-if="auditIssues.length" class="risk-legend">
          <span class="risk-legend__title">行内标记：</span>
          <span class="risk-legend__item risk-high">红 · 高（影响业务逻辑）</span>
          <span class="risk-legend__item risk-medium">橙 · 中（质量/预发布风险）</span>
          <span class="risk-legend__item risk-low">黄 · 低（规范性问题）</span>
          <span class="risk-legend__item risk-fix">绿 · 当前修复建议行</span>
        </div>
        <div v-if="auditIssues.length || fixRecords.length" class="fix-workflow">
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
            <span class="fix-action-bar__step">修复建议 · 步骤 ③</span>
            <el-tag size="small" type="warning">{{ fixBarFixRecord.ruleId }}</el-tag>
            <span class="mono">第 {{ fixBarFixRecord.lineNo }} 行 · {{ fixBarFixRecord.filePath }}</span>
          </div>
          <p class="fix-action-bar__text">{{ fixBarFixRecord.suggestion }}</p>
          <p v-if="fixBarFixRecord.rationale" class="fix-action-bar__rationale">{{ fixBarFixRecord.rationale }}</p>
          <div class="fix-action-bar__actions">
            <el-button type="primary" size="small" @click="onApplyFix">写入编辑器（应用建议）</el-button>
            <el-button size="small" :loading="diffLoading" @click="onPreviewDiff">预览 Diff</el-button>
            <el-button size="small" link type="info" @click="onDismissFix">放弃该条建议</el-button>
            <router-link to="/help#diff" class="fix-action-bar__link">Diff 说明</router-link>
          </div>
          <p class="fix-action-bar__hint">流程：应用或 AI 改 → 下方实时 Diff → 保存（自动刷新审计）→ HITL 确认</p>
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
          />
        </div>
        <div v-else class="editor-empty">
          <p>在左侧文件树点击文件，在此查看源码</p>
          <p class="editor-empty__hint">点击文件夹名称可展开/收起；运行审计后，问题行会在编辑器中高亮</p>
        </div>
        <div
          v-if="currentPath"
          class="editor-resize-handle"
          title="拖动调整代码区高度"
          @mousedown="startEditorResize"
        />
        <div v-if="currentPath && fileDirty" class="diff-preview diff-preview--live">
          <div class="panel-head panel-head--split">
            <span>实时 Diff</span>
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
            <el-button size="small" type="primary" :loading="saveLoading || auditRefreshing" @click="onSaveFile">
              保存并刷新审计
            </el-button>
          </div>
        </div>
        <div v-if="!detailCollapsed && auditIssues.length" class="audit-fix-zone">
          <div class="panel-head issue-head">
            <span>审计问题 ↔ 修复建议</span>
            <div class="issue-head__actions">
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
          <p v-if="!fixRecords.length" class="fix-hint">
            先点编辑器上方「生成修复建议」；也可对单条问题用「AI 帮我改」，在右侧会话里确认后再写入编辑器
          </p>
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
                <span class="mono">{{ issue.filePath }}:{{ issue.line }}</span>
                <el-button size="small" link type="warning" @click.stop="ignoreIssue(issue)">忽略</el-button>
              </div>
              <p class="audit-fix-pair__desc">{{ issue.description }}</p>
              <p v-if="issue.triggerSnippet" class="audit-fix-pair__snippet mono">{{ issue.triggerSnippet }}</p>
            </div>
            <div v-if="fixByIssueKey.get(auditIssueKey(issue))" class="audit-fix-pair__fix">
              <div class="audit-fix-pair__fix-label">对应修复建议</div>
              <p>{{ fixByIssueKey.get(auditIssueKey(issue))!.suggestion }}</p>
              <div class="audit-fix-pair__fix-actions">
                <el-button size="small" type="primary" @click="onSelectFixRecord(fixByIssueKey.get(auditIssueKey(issue))!)">
                  定位并应用
                </el-button>
                <el-button size="small" @click="onAiFixIssue(issue)">AI 帮我改</el-button>
              </div>
            </div>
            <div v-else class="audit-fix-pair__fix audit-fix-pair__fix--empty">
              暂无修复建议 · <el-button size="small" link type="primary" @click="onAiFixIssue(issue)">让 AI 分析</el-button>
            </div>
          </div>
          <p v-if="filteredAuditIssues.length > 20" class="fix-hint">
            仅展示前 20 条，还有 {{ filteredAuditIssues.length - 20 }} 条（可调整风险筛选或忽略低优先级项）
          </p>
        </div>

        <div v-if="!detailCollapsed && activeFixId && fixRecords.length" class="fix-panel">
          <div class="panel-head">人工确认 HITL（当前修复建议）</div>
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

      <aside
        v-show="showChatPanel && (!isNarrow || mobilePane === 'chat')"
        class="panel glass-panel chat-panel"
      >
        <div class="panel-head chat-head">
          <span><el-icon><ChatDotRound /></el-icon> AI 分析</span>
          <div class="chat-head-actions">
            <el-button size="small" link type="primary" @click="onNewSession">新建</el-button>
            <el-button size="small" link @click="showChatPanel = false">收起</el-button>
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
            <p>AI 会先读 README、pom 等文档，再结合源码判断项目定位（游戏/工具/系统等）。</p>
            <el-button type="primary" :loading="chatLoading" @click="onOnboard">
              一键项目分析
            </el-button>
          </div>
          <div
            v-for="msg in displayMessages"
            :key="msg.id"
            class="chat-bubble"
            :class="`chat-bubble--${msg.role}`"
          >
            <span class="chat-role">{{ msg.role === 'user' ? '你' : msg.role === 'assistant' ? 'AI 分析' : '系统' }}</span>
            <div v-if="msg.sections && !isAiFixPendingMessage(msg.id)" class="chat-sections">
              <div v-for="(sec, idx) in msg.sections" :key="idx" class="chat-section">
                <div class="chat-section__title">{{ sec.title }}</div>
                <div class="chat-section__body">{{ sec.body }}</div>
              </div>
            </div>
            <div v-else class="chat-content">{{ msg.content }}</div>
            <div
              v-if="msg.role === 'assistant' && isAiFixPendingMessage(msg.id)"
              class="chat-ai-fix-actions"
            >
              <p class="chat-ai-fix-actions__hint">请确认是否采用 AI 修改（写入后可预览 Diff / 保存 / HITL）</p>
              <el-button size="small" type="primary" @click="onAdoptAiFix(msg)">采用 AI 修改</el-button>
              <el-button size="small" link type="warning" @click="onIgnoreWarningFromAi(msg)">忽略此警告</el-button>
              <el-button size="small" link @click="onSkipAiFix(msg)">暂不处理</el-button>
            </div>
          </div>
          <div v-if="chatLoading" class="chat-bubble chat-bubble--assistant chat-bubble--loading">
            <span class="chat-role">AI 分析</span>
            <div class="chat-typing" aria-hidden="true">
              <span /><span /><span />
            </div>
            <p class="chat-loading-hint">正在阅读文档与源码…</p>
          </div>
        </div>
        <div class="chat-input">
          <el-input
            v-model="chatInput"
            type="textarea"
            :rows="3"
            placeholder="询问项目结构、风险或改进建议..."
            @keydown.ctrl.enter="onSendChat"
          />
          <el-button type="primary" :loading="chatLoading" @click="onSendChat">发送 (Ctrl+Enter)</el-button>
        </div>
      </aside>
    </div>

    <InteractiveTour
      v-model="showWorkbenchTour"
      :steps="workbenchTourSteps"
      :on-complete="completeWorkbenchTour"
    />
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
  background: rgba(15, 23, 42, 0.45);
  font-size: 12px;
  color: var(--cw-text-muted);
}

.wb-repo {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--cw-text-muted);
}

.wb-repo a {
  color: var(--cw-accent);
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
  color: var(--cw-accent);
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
  color: var(--cw-accent);
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
  background: rgba(15, 23, 42, 0.55);
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
  grid-template-columns: 1fr;
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
  background: rgba(15, 23, 42, 0.55);
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  max-height: 200px;
  overflow: auto;
}

.eval-score {
  margin-left: auto;
  font-size: 13px;
  color: var(--cw-accent);
}

.issue-head {
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.issue-head__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.audit-fix-zone {
  margin-top: 12px;
  max-height: 420px;
  overflow: auto;
}

.audit-fix-pair {
  margin-bottom: 12px;
  border-radius: 10px;
  border: 1px solid var(--cw-border);
  overflow: hidden;
}

.audit-fix-pair--active {
  border-color: rgba(251, 191, 36, 0.55);
  box-shadow: 0 0 0 1px rgba(251, 191, 36, 0.2);
}

.audit-fix-pair__issue {
  padding: 10px 12px;
  background: rgba(15, 23, 42, 0.55);
  cursor: pointer;
}

.audit-fix-pair__issue-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
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
  background: rgba(0, 0, 0, 0.25);
  font-size: 11px;
  line-height: 1.45;
  color: #fca5a5;
  white-space: pre-wrap;
}

.audit-fix-pair__fix {
  padding: 10px 12px;
  background: rgba(52, 211, 153, 0.08);
  border-top: 1px solid rgba(52, 211, 153, 0.25);
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
  background: rgba(15, 23, 42, 0.7);
  font-size: 11px;
  line-height: 1.45;
  max-height: 160px;
  overflow: auto;
  white-space: pre-wrap;
}

.wb-grid {
  display: grid;
  grid-template-columns: 280px 5px minmax(0, 1fr) 5px 380px;
  gap: 0 8px;
  align-items: stretch;
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
  background: rgba(34, 211, 238, 0.35);
}

.tree-panel--collapsed {
  padding: 10px 6px;
}

.tree-panel--collapsed .panel-head {
  flex-direction: column;
  align-items: center;
  gap: 6px;
  margin-bottom: 0;
}

.tree-collapsed-label {
  writing-mode: vertical-rl;
  font-size: 12px;
  letter-spacing: 2px;
}

@media (max-width: 1100px) {
  .wb-grid {
    grid-template-columns: 1fr;
  }
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

.tree-node-label {
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
  background: rgba(248, 113, 113, 0.2);
  color: #fca5a5;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.tree-issue-badge.risk-medium {
  background: rgba(251, 146, 60, 0.22);
  color: #fdba74;
}

.tree-issue-badge.risk-low {
  background: rgba(250, 204, 21, 0.18);
  color: #fde047;
}

.audit-scan-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 10px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--cw-text-muted);
  border: 1px solid rgba(251, 191, 36, 0.35);
  background: rgba(251, 191, 36, 0.08);
}

.risk-legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  margin-bottom: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--cw-border);
  font-size: 11px;
  color: var(--cw-text-muted);
}

.risk-legend__title {
  font-weight: 600;
  color: var(--cw-text);
}

.risk-legend__item {
  padding: 2px 8px;
  border-radius: 6px;
}

.risk-legend__item.risk-high {
  background: rgba(239, 68, 68, 0.15);
  color: #fca5a5;
}

.risk-legend__item.risk-medium {
  background: rgba(249, 115, 22, 0.15);
  color: #fdba74;
}

.risk-legend__item.risk-low {
  background: rgba(234, 179, 8, 0.12);
  color: #fde047;
}

.risk-legend__item.risk-fix {
  background: rgba(52, 211, 153, 0.12);
  color: #6ee7b7;
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
  border: 1px solid var(--cw-border);
  color: var(--cw-text-muted);
}

.fix-workflow__step--done {
  border-color: rgba(52, 211, 153, 0.4);
  color: #6ee7b7;
  background: rgba(52, 211, 153, 0.08);
}

.fix-workflow__step--current {
  border-color: rgba(34, 211, 238, 0.5);
  color: #67e8f9;
  background: rgba(34, 211, 238, 0.1);
}

.fix-action-bar__step {
  font-size: 11px;
  font-weight: 600;
  color: var(--cw-accent);
  margin-right: 6px;
}

.fix-action-bar__hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--cw-text-muted);
}

.chat-ai-fix-actions {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(34, 211, 238, 0.35);
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
  --el-tag-bg-color: rgba(239, 68, 68, 0.15);
  --el-tag-border-color: rgba(239, 68, 68, 0.45);
  --el-tag-text-color: #fca5a5;
}

:deep(.el-tag.risk-medium) {
  --el-tag-bg-color: rgba(249, 115, 22, 0.15);
  --el-tag-border-color: rgba(249, 115, 22, 0.45);
  --el-tag-text-color: #fdba74;
}

:deep(.el-tag.risk-low) {
  --el-tag-bg-color: rgba(234, 179, 8, 0.12);
  --el-tag-border-color: rgba(234, 179, 8, 0.4);
  --el-tag-text-color: #fde047;
}

:deep(.el-tree-node__content) {
  height: 32px;
  border-radius: 8px;
}

:deep(.el-tree-node__content:hover) {
  background: rgba(34, 211, 238, 0.08);
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
  background: rgba(34, 211, 238, 0.25);
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
}

.editor-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
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
  border-color: rgba(34, 211, 238, 0.45) !important;
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
  background: rgba(15, 23, 42, 0.45);
}

.diff-preview--live {
  border-color: rgba(34, 211, 238, 0.35);
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
  background: rgba(15, 23, 42, 0.7);
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
  color: #86efac;
  background: rgba(52, 211, 153, 0.08);
}

.diff-line--del {
  color: #fca5a5;
  background: rgba(248, 113, 113, 0.08);
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
  border: 1px solid rgba(52, 211, 153, 0.35);
  background: rgba(52, 211, 153, 0.08);
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
  color: var(--cw-accent);
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
  background: rgba(15, 23, 42, 0.5);
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
  background: rgba(248, 113, 113, 0.2);
  color: #fca5a5;
}

.session-tab.active {
  color: var(--cw-accent);
  border-color: var(--cw-border-strong);
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
  background: rgba(15, 23, 42, 0.55);
}

.chat-bubble--assistant {
  border-color: rgba(34, 211, 238, 0.25);
}

.chat-bubble--user {
  border-color: rgba(129, 140, 248, 0.25);
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
  background: rgba(15, 23, 42, 0.35);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.chat-section__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--cw-accent);
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
  background: var(--cw-accent);
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

.chat-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
