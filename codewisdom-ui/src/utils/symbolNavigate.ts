import { fetchFileContent } from '../api/project'

export interface SymbolLocation {
  filePath: string
  line: number
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function findSymbolLineInJava(source: string, symbol: string): number | null {
  const name = escapeRegExp(symbol.trim())
  if (!name) return null
  const patterns = [
    new RegExp(`\\b${name}\\s*\\(`),
    new RegExp(`\\b(class|interface|enum|record)\\s+${name}\\b`),
    new RegExp(`\\b${name}\\s*;`),
  ]
  const lines = source.split('\n')
  for (let i = 0; i < lines.length; i++) {
    if (patterns.some((re) => re.test(lines[i]))) {
      return i + 1
    }
  }
  return null
}

function rankPath(path: string, symbol: string): number {
  const lower = path.toLowerCase()
  const sym = symbol.toLowerCase()
  if (lower.endsWith(`/${sym}.java`)) return 0
  if (lower.includes(sym)) return 1
  return 2
}

export async function locateJavaSymbol(options: {
  projectId: number
  javaPaths: string[]
  symbol: string
  hintFilePath?: string
  currentFilePath?: string
  currentFileContent?: string
  lineHint?: number | null
}): Promise<SymbolLocation | null> {
  const symbol = options.symbol.trim()
  if (!symbol) return null

  if (options.hintFilePath) {
    const loc = await tryFile(options.projectId, options.hintFilePath, symbol, options.lineHint)
    if (loc) return loc
  }

  if (options.currentFilePath && options.currentFileContent) {
    const line =
      options.lineHint && options.lineHint > 0
        ? options.lineHint
        : findSymbolLineInJava(options.currentFileContent, symbol)
    if (line) {
      return { filePath: options.currentFilePath, line }
    }
  }

  const paths = [...options.javaPaths]
  paths.sort((a, b) => rankPath(a, symbol) - rankPath(b, symbol))
  const maxScan = 48
  for (let i = 0; i < Math.min(paths.length, maxScan); i++) {
    const path = paths[i]
    if (path === options.currentFilePath) continue
    const loc = await tryFile(options.projectId, path, symbol, null)
    if (loc) return loc
  }
  return null
}

async function tryFile(
  projectId: number,
  filePath: string,
  symbol: string,
  lineHint: number | null | undefined,
): Promise<SymbolLocation | null> {
  try {
    const file = await fetchFileContent(projectId, filePath)
    if (file.binary || !file.content) return null
    const line = lineHint && lineHint > 0 ? lineHint : findSymbolLineInJava(file.content, symbol)
    if (!line) return null
    return { filePath, line }
  } catch {
    return null
  }
}
