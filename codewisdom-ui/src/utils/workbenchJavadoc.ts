import { generateJavadoc } from '../api/doc'
import { applyJavadocToSource, type ApplyJavadocResult } from './javaDocApply'
import { extractJavaTypeDocForScope, type DocGenScopeMode } from './javaDocExtract'

export interface EditorLineRanges {
  selection: { startLine: number; endLine: number } | null
  viewport: { startLine: number; endLine: number } | null
}

export interface RunJavadocOptions {
  projectId: number
  source: string
  filePath: string
  scope: DocGenScopeMode
  editorRanges: EditorLineRanges
}

export interface RunJavadocOutcome {
  applied: ApplyJavadocResult
  scopeLabel: string
}

function scopeLabel(scope: DocGenScopeMode): string {
  if (scope === 'file') return '全文件'
  if (scope === 'viewport') return '当前可见区域'
  return '选中代码'
}

function resolveRange(scope: DocGenScopeMode, editorRanges: EditorLineRanges): { startLine: number; endLine: number } | null {
  if (scope === 'file') return null
  if (scope === 'selection') {
    return editorRanges.selection
  }
  return editorRanges.viewport
}

export async function runJavadocForScope(options: RunJavadocOptions): Promise<RunJavadocOutcome> {
  const { projectId, source, filePath, scope, editorRanges } = options
  const range = resolveRange(scope, editorRanges)
  if (scope === 'selection' && !range) {
    throw new Error('请先在编辑器中选中要分析的代码行')
  }
  if (scope === 'viewport' && !range) {
    throw new Error('无法读取编辑器可见区域，请稍后重试')
  }

  const parsed = extractJavaTypeDocForScope(source, filePath, scope, range ?? undefined)
  if (!parsed || (!parsed.includeTypeComment && !parsed.extracted.methods.length)) {
    throw new Error('当前范围内未识别到可补充注释的类型或方法')
  }

  const response = await generateJavadoc(projectId, {
    qualifiedName: parsed.extracted.qualifiedName,
    kind: parsed.extracted.kind,
    classCommentHint: parsed.extracted.classCommentHint,
    methods: parsed.extracted.methods.map((m) => ({
      name: m.name,
      returnType: m.returnType,
      constructor: m.constructor,
      parameters: m.parameters,
    })),
  })

  if (response.skipped) {
    throw new Error(response.message)
  }

  const lineOffset = parsed.relativeLines && range ? range.startLine - 1 : 0
  const applied = applyJavadocToSource(
    source,
    parsed.includeTypeComment ? response.typeComment : '',
    response.methodComments,
    parsed.extracted,
    { includeTypeComment: parsed.includeTypeComment, methodLineOffset: lineOffset },
  )

  return { applied, scopeLabel: scopeLabel(scope) }
}
