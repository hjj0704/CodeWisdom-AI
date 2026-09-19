import type { ExtractedMethodDoc, ExtractedTypeDoc } from './javaDocExtract'

export interface ApplyJavadocResult {
  content: string
  insertedType: boolean
  insertedMethods: string[]
  skippedExisting: string[]
}

function leadingIndent(line: string): string {
  const m = line.match(/^(\s*)/)
  return m ? m[1] : ''
}

function hasJavadocAbove(lines: string[], lineIndex: number): boolean {
  let j = lineIndex - 1
  while (j >= 0 && lines[j].trim() === '') j--
  if (j < 0) return false
  const t = lines[j].trim()
  if (t.startsWith('/**') || t.endsWith('*/')) return true
  if (t.includes('*/')) return true
  while (j >= 0) {
    const row = lines[j].trim()
    if (row.startsWith('/**')) return true
    if (row.includes('*/')) return false
    j--
  }
  return false
}

function commentToLines(comment: string, indent: string): string[] {
  const text = comment.trim().replace(/\r\n/g, '\n')
  if (!text) return []
  if (!text.includes('\n')) {
    return [indent + text]
  }
  return text.split('\n').map((line, idx) => {
    const trimmed = line.trim()
    if (idx === 0) return indent + trimmed
    return indent + trimmed
  })
}

function findTypeLine(lines: string[], extracted: ExtractedTypeDoc): number {
  const re = new RegExp(`\\b${extracted.kind}\\s+${extracted.typeName}\\b`)
  for (let i = 0; i < lines.length; i++) {
    if (re.test(lines[i])) return i
  }
  return -1
}

function resolveMethodLineIndex(
  lines: string[],
  extracted: ExtractedTypeDoc,
  method: ExtractedMethodDoc,
  lineOffset: number,
): number {
  if (method.line >= 1) {
    const idx = method.line - 1 + lineOffset
    if (idx >= 0 && idx < lines.length) {
      const line = lines[idx]
      if (/^\s*(public|protected|private)\s/.test(line)) {
        if (method.constructor && line.includes(`${extracted.typeName}(`)) return idx
        if (!method.constructor && new RegExp(`\\b${method.name}\\s*\\(`).test(line)) return idx
      }
    }
  }
  return findMethodLine(lines, extracted, method)
}

function findMethodLine(lines: string[], extracted: ExtractedTypeDoc, method: ExtractedMethodDoc): number {
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (!/^\s*(public|protected|private)\s/.test(line)) continue
    if (method.constructor) {
      if (line.includes(`${extracted.typeName}(`)) return i
      continue
    }
    const re = new RegExp(`\\b${method.name}\\s*\\(`)
    if (re.test(line)) return i
  }
  return -1
}

export function applyJavadocToSource(
  source: string,
  typeComment: string,
  methodComments: Record<string, string>,
  extracted: ExtractedTypeDoc,
  options?: { includeTypeComment?: boolean; methodLineOffset?: number },
): ApplyJavadocResult {
  const includeType = options?.includeTypeComment ?? true
  const methodLineOffset = options?.methodLineOffset ?? 0
  const lines = source.split('\n')
  const inserts: Array<{ index: number; block: string[] }> = []
  const insertedMethods: string[] = []
  const skippedExisting: string[] = []
  let insertedType = false

  const typeLine = findTypeLine(lines, extracted)
  if (includeType && typeLine >= 0 && typeComment.trim()) {
    if (hasJavadocAbove(lines, typeLine)) {
      skippedExisting.push('类型')
    } else {
      const indent = leadingIndent(lines[typeLine])
      inserts.push({ index: typeLine, block: commentToLines(typeComment, indent) })
      insertedType = true
    }
  }

  for (const method of extracted.methods) {
    const comment = methodComments[method.name]
    if (!comment?.trim()) continue
    const lineIdx = resolveMethodLineIndex(lines, extracted, method, methodLineOffset)
    if (lineIdx < 0) continue
    if (hasJavadocAbove(lines, lineIdx)) {
      skippedExisting.push(method.name)
      continue
    }
    const indent = leadingIndent(lines[lineIdx])
    inserts.push({ index: lineIdx, block: commentToLines(comment, indent) })
    insertedMethods.push(method.name)
  }

  inserts.sort((a, b) => b.index - a.index)
  for (const ins of inserts) {
    lines.splice(ins.index, 0, ...ins.block)
  }

  return {
    content: lines.join('\n'),
    insertedType,
    insertedMethods,
    skippedExisting,
  }
}
