export interface ExtractedMethodDoc {
  name: string
  returnType: string | null
  constructor: boolean
  line: number
  parameters: Array<{ name: string; type: string }>
}

export interface ExtractedTypeDoc {
  typeName: string
  typeLine: number
  qualifiedName: string
  kind: string
  classCommentHint: string
  methods: ExtractedMethodDoc[]
}

export type DocGenScopeMode = 'file' | 'viewport' | 'selection'

function lineNumberAt(source: string, index: number): number {
  if (index <= 0) return 1
  return source.slice(0, index).split('\n').length
}

function guessQualifiedName(filePath: string, typeName: string): string {
  const normalized = filePath.replace(/\\/g, '/')
  const javaIdx = normalized.indexOf('/src/main/java/')
  if (javaIdx >= 0) {
    const pkgPath = normalized.slice(javaIdx + '/src/main/java/'.length).replace(/\.java$/, '')
    const pkg = pkgPath.split('/').slice(0, -1).join('.')
    return pkg ? `${pkg}.${typeName}` : typeName
  }
  return typeName
}

export function extractJavaTypeDoc(source: string, filePath: string): ExtractedTypeDoc | null {
  const typeMatch = source.match(
    /(?:public\s+)?(?:abstract\s+)?(?:final\s+)?(class|interface|enum|record)\s+(\w+)/,
  )
  if (!typeMatch || typeMatch.index == null) return null
  const kind = typeMatch[1]
  const typeName = typeMatch[2]
  const typeLine = lineNumberAt(source, typeMatch.index)
  const qualifiedName = guessQualifiedName(filePath, typeName)

  const methods: ExtractedMethodDoc[] = []
  const methodRe =
    /(?:public|protected|private)\s+(?:static\s+)?(?:final\s+)?(?:<[^>]+>\s+)?([\w.<>,\[\]?]+)\s+(\w+)\s*\(([^)]*)\)/g

  let match: RegExpExecArray | null
  while ((match = methodRe.exec(source)) !== null) {
    const returnType = match[1].trim()
    const name = match[2]
    if (name === typeName && returnType === typeName) {
      continue
    }
    if (['if', 'for', 'while', 'switch', 'catch'].includes(name)) continue
    methods.push({
      name,
      returnType,
      constructor: false,
      line: lineNumberAt(source, match.index),
      parameters: parseParams(match[3]),
    })
  }

  const ctorRe = new RegExp(`(?:public|protected|private)\\s+${typeName}\\s*\\(([^)]*)\\)`)
  const ctorMatch = ctorRe.exec(source)
  if (ctorMatch && ctorMatch.index != null) {
    methods.unshift({
      name: typeName,
      returnType: null,
      constructor: true,
      line: lineNumberAt(source, ctorMatch.index),
      parameters: parseParams(ctorMatch[1]),
    })
  }

  const unique = new Map<string, ExtractedMethodDoc>()
  for (const m of methods) {
    if (!unique.has(m.name)) unique.set(m.name, m)
  }

  return {
    typeName,
    typeLine,
    qualifiedName,
    kind,
    classCommentHint: '',
    methods: [...unique.values()].slice(0, 24),
  }
}

function extractMethodsFromSnippet(snippet: string, typeName: string): ExtractedMethodDoc[] {
  const methods: ExtractedMethodDoc[] = []
  const lines = snippet.split('\n')
  const methodRe =
    /(?:public|protected|private)\s+(?:static\s+)?(?:final\s+)?(?:<[^>]+>\s+)?([\w.<>,\[\]?]+)\s+(\w+)\s*\(([^)]*)\)/

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const m = line.match(methodRe)
    if (!m) continue
    const name = m[2]
    if (['if', 'for', 'while', 'switch', 'catch'].includes(name)) continue
    const constructor = name === typeName && m[1].trim() === typeName
    methods.push({
      name,
      returnType: constructor ? null : m[1].trim(),
      constructor,
      line: i + 1,
      parameters: parseParams(m[3]),
    })
  }
  const unique = new Map<string, ExtractedMethodDoc>()
  for (const item of methods) {
    if (!unique.has(item.name)) unique.set(item.name, item)
  }
  return [...unique.values()]
}

export function extractJavaTypeDocForScope(
  source: string,
  filePath: string,
  scope: DocGenScopeMode,
  range?: { startLine: number; endLine: number },
): { extracted: ExtractedTypeDoc; includeTypeComment: boolean; relativeLines: boolean } | null {
  const full = extractJavaTypeDoc(source, filePath)
  if (!full) return null
  if (scope === 'file' || !range) {
    return { extracted: full, includeTypeComment: true, relativeLines: false }
  }
  const { startLine, endLine } = range
  const typeIn = full.typeLine >= startLine && full.typeLine <= endLine
  const methods = full.methods.filter((m) => m.line >= startLine && m.line <= endLine)
  if (!typeIn && methods.length === 0) {
    const snippet = source.split('\n').slice(startLine - 1, endLine).join('\n')
    const fromSnippet = extractMethodsFromSnippet(snippet, full.typeName)
    if (!fromSnippet.length) return null
    return {
      extracted: { ...full, methods: fromSnippet },
      includeTypeComment: false,
      relativeLines: true,
    }
  }
  return {
    extracted: { ...full, methods },
    includeTypeComment: typeIn,
    relativeLines: false,
  }
}

function parseParams(raw: string): Array<{ name: string; type: string }> {
  if (!raw.trim()) return []
  return raw
    .split(',')
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => {
      const tokens = part.replace(/\s+@?\w+(?:\([^)]*\))?/g, '').trim().split(/\s+/)
      if (tokens.length >= 2) {
        const name = tokens[tokens.length - 1]
        const type = tokens.slice(0, -1).join(' ')
        return { name, type }
      }
      return { name: 'arg', type: part }
    })
}
