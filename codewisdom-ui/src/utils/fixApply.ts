export interface FixApplyResult {
  content: string
  applied: boolean
  message: string
}

/** 尝试根据建议文本自动改写目标行；无法识别时返回 applied=false。 */
export function tryApplySuggestion(
  fileContent: string,
  lineNo: number,
  suggestion: string,
): FixApplyResult {
  const lines = fileContent.split('\n')
  if (lineNo < 1 || lineNo > lines.length) {
    return { content: fileContent, applied: false, message: '行号超出文件范围' }
  }

  const idx = lineNo - 1
  const originalLine = lines[idx]

  const backtickCodes = suggestion.match(/`([^`\n]+)`/g)
  if (backtickCodes?.length) {
    const replacement = backtickCodes[backtickCodes.length - 1].slice(1, -1).trim()
    if (replacement && !replacement.includes('...')) {
      lines[idx] = preserveIndent(originalLine, replacement)
      return { content: lines.join('\n'), applied: true, message: '已按建议中的代码片段替换目标行' }
    }
  }

  if (
    suggestion.includes('catch')
    && /catch\s*\([^)]*\)\s*\{/.test(originalLine)
    && /\{\s*\}/.test(originalLine)
  ) {
    lines[idx] = originalLine.replace(/\{\s*\}/, '{ /* TODO: 处理异常 */ }')
    return { content: lines.join('\n'), applied: true, message: '已在 catch 中加入占位注释，请按需补充日志或处理逻辑' }
  }

  if (suggestion.includes('equals') || suggestion.includes('空指针')) {
    return {
      content: fileContent,
      applied: false,
      message: '空安全 equals 需人工改写法（建议常量在前或使用 Objects.equals），或改用 AI 帮我改',
    }
  }

  return {
    content: fileContent,
    applied: false,
    message: '该建议需手动修改：请参照说明编辑代码，再点「预览 Diff」查看变化',
  }
}

/** 从 AI 回复中提取代码块或单行代码建议 */
export function extractSuggestedCode(content: string): string | null {
  const fenced = content.match(/```(?:java|javascript|typescript|xml|)?\s*\n([\s\S]*?)```/i)
  if (fenced?.[1]?.trim()) return fenced[1].trim()

  const inline = content.match(/`([^`\n]{4,})`/g)
  if (inline?.length) {
    const last = inline[inline.length - 1].slice(1, -1).trim()
    if (last.includes('(') || last.includes(';') || last.includes('{')) return last
  }
  return null
}

export function applyCodeAtLine(fileContent: string, lineNo: number, newCode: string): FixApplyResult {
  const lines = fileContent.split('\n')
  if (lineNo < 1 || lineNo > lines.length) {
    return { content: fileContent, applied: false, message: '行号无效' }
  }
  const idx = lineNo - 1
  const original = lines[idx]
  const replacementLines = newCode.split('\n').map((l, i) =>
    i === 0 ? preserveIndent(original, l) : l,
  )
  lines.splice(idx, 1, ...replacementLines)
  return { content: lines.join('\n'), applied: true, message: '已写入 AI 建议到编辑器，请预览 Diff 后保存' }
}

function preserveIndent(originalLine: string, replacement: string) {
  const indent = originalLine.match(/^\s*/)?.[0] ?? ''
  if (replacement.startsWith(indent)) return replacement
  return indent + replacement.trimStart()
}
