<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as monaco from 'monaco-editor'

export interface IssueLineMark {
  line: number
  risk: 'HIGH' | 'MEDIUM' | 'LOW'
}

const props = defineProps<{
  modelValue: string
  language?: string
  readOnly?: boolean
  issueLineMarks?: IssueLineMark[]
  fixHighlightLine?: number | null
  focusLine?: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const container = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let decorations: string[] = []
let resizeObserver: ResizeObserver | null = null

function riskClass(risk: IssueLineMark['risk']) {
  if (risk === 'HIGH') return 'cw-issue-line--high'
  if (risk === 'MEDIUM') return 'cw-issue-line--medium'
  return 'cw-issue-line--low'
}

function riskGlyph(risk: IssueLineMark['risk']) {
  if (risk === 'HIGH') return 'cw-issue-glyph--high'
  if (risk === 'MEDIUM') return 'cw-issue-glyph--medium'
  return 'cw-issue-glyph--low'
}

function refreshDecorations() {
  applyHighlights(props.issueLineMarks, props.fixHighlightLine, props.focusLine)
}

function langId(lang?: string | null) {
  if (!lang) return 'plaintext'
  const map: Record<string, string> = {
    java: 'java',
    xml: 'xml',
    yaml: 'yaml',
    yml: 'yaml',
    markdown: 'markdown',
    md: 'markdown',
    json: 'json',
    python: 'python',
    py: 'python',
    javascript: 'javascript',
    js: 'javascript',
    typescript: 'typescript',
    ts: 'typescript',
    html: 'html',
    css: 'css',
    scss: 'scss',
    sql: 'sql',
    properties: 'ini',
    sh: 'shell',
  }
  return map[lang.toLowerCase()] || 'plaintext'
}

function applyHighlights(
  marks?: IssueLineMark[],
  fixLine?: number | null,
  focusLine?: number | null,
) {
  if (!editor) return
  const model = editor.getModel()
  const maxLine = model?.getLineCount() ?? 0
  const fixValid =
    fixLine != null && Number.isFinite(fixLine) && fixLine >= 1 && fixLine <= maxLine ? fixLine : null
  const focusValid =
    focusLine != null && Number.isFinite(focusLine) && focusLine >= 1 && focusLine <= maxLine
      ? focusLine
      : null

  const items: monaco.editor.IModelDeltaDecoration[] = []

  for (const mark of marks ?? []) {
    if (mark.line === fixValid || mark.line === focusValid) continue
    if (mark.line < 1 || mark.line > maxLine) continue
    items.push({
      range: new monaco.Range(mark.line, 1, mark.line, model!.getLineMaxColumn(mark.line)),
      options: {
        isWholeLine: true,
        className: riskClass(mark.risk),
        glyphMarginClassName: riskGlyph(mark.risk),
      },
    })
  }

  if (fixValid != null && fixValid !== focusValid) {
    items.push({
      range: new monaco.Range(fixValid, 1, fixValid, model!.getLineMaxColumn(fixValid)),
      options: {
        isWholeLine: true,
        className: 'cw-fix-line',
        glyphMarginClassName: 'cw-fix-glyph',
      },
    })
  }

  if (focusValid != null) {
    items.push({
      range: new monaco.Range(focusValid, 1, focusValid, model!.getLineMaxColumn(focusValid)),
      options: {
        isWholeLine: true,
        className: 'cw-focus-line',
        glyphMarginClassName: 'cw-focus-glyph',
      },
    })
  }

  decorations = editor.deltaDecorations(decorations, items)
}

function scrollToLine(line: number) {
  if (!editor || line < 1) return
  editor.revealLineInCenter(line)
  editor.setPosition({ lineNumber: line, column: 1 })
  editor.focus()
}

defineExpose({ scrollToLine })

onMounted(() => {
  if (!container.value) return
  editor = monaco.editor.create(container.value, {
    value: props.modelValue,
    language: langId(props.language),
    theme: 'vs-dark',
    readOnly: props.readOnly ?? false,
    automaticLayout: false,
    minimap: { enabled: false },
    fontSize: 13,
    scrollBeyondLastLine: false,
    wordWrap: 'off',
    glyphMargin: true,
    scrollbar: {
      vertical: 'auto',
      horizontal: 'auto',
      alwaysConsumeMouseWheel: false,
    },
  })
  editor.onDidChangeModelContent(() => {
    if (editor) {
      emit('update:modelValue', editor.getValue())
      refreshDecorations()
    }
  })

  resizeObserver = new ResizeObserver(() => {
    editor?.layout()
  })
  resizeObserver.observe(container.value)
  refreshDecorations()
})

watch(
  () => props.modelValue,
  (value) => {
    if (editor && value !== editor.getValue()) {
      editor.setValue(value)
      refreshDecorations()
    }
  },
)

watch(
  () => props.language,
  (language) => {
    if (editor?.getModel()) {
      monaco.editor.setModelLanguage(editor.getModel()!, langId(language))
    }
  },
)

watch(
  () => props.readOnly,
  (readOnly) => {
    editor?.updateOptions({ readOnly: readOnly ?? false })
  },
)

watch(
  () => [props.issueLineMarks, props.fixHighlightLine, props.focusLine] as const,
  () => {
    refreshDecorations()
  },
  { deep: true },
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  editor?.dispose()
})
</script>

<template>
  <div ref="container" class="editor-host" />
</template>

<style scoped>
.editor-host {
  width: 100%;
  height: 100%;
  min-height: 0;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--cw-border);
}
</style>

<style>
.cw-issue-line--high {
  background: rgba(248, 113, 113, 0.22) !important;
  box-shadow: inset 3px 0 0 #ef4444;
}
.cw-issue-line--medium {
  background: rgba(251, 146, 60, 0.2) !important;
  box-shadow: inset 3px 0 0 #f97316;
}
.cw-issue-line--low {
  background: rgba(250, 204, 21, 0.16) !important;
  box-shadow: inset 3px 0 0 #eab308;
}
.cw-issue-glyph--high {
  background: #ef4444;
  border-radius: 50%;
  margin-left: 6px;
  width: 8px !important;
  height: 8px !important;
}
.cw-issue-glyph--medium {
  background: #f97316;
  border-radius: 50%;
  margin-left: 6px;
  width: 8px !important;
  height: 8px !important;
}
.cw-issue-glyph--low {
  background: #eab308;
  border-radius: 50%;
  margin-left: 6px;
  width: 8px !important;
  height: 8px !important;
}
.cw-fix-line {
  background: rgba(52, 211, 153, 0.18) !important;
}
.cw-fix-glyph {
  background: #34d399;
  border-radius: 50%;
  margin-left: 6px;
  width: 8px !important;
  height: 8px !important;
}
.cw-focus-line {
  background: rgba(251, 191, 36, 0.28) !important;
  box-shadow: inset 3px 0 0 #fbbf24;
}
.cw-focus-glyph {
  background: #fbbf24;
  border-radius: 50%;
  margin-left: 6px;
  width: 10px !important;
  height: 10px !important;
  box-shadow: 0 0 8px rgba(251, 191, 36, 0.8);
}
</style>
