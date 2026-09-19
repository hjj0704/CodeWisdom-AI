import type { AuditIssueItem } from '../api/audit'

function normalizePath(path: string): string {
  return path.replace(/\\/g, '/')
}

export function normalizeAuditIssueKey(key: string): string {
  const parts = key.split('|')
  if (parts.length >= 2) {
    parts[1] = normalizePath(parts[1])
    return parts.join('|')
  }
  return key
}

export function auditIssueKey(issue: Pick<AuditIssueItem, 'issueKey' | 'ruleId' | 'filePath' | 'line'>): string {
  const path = normalizePath(issue.filePath)
  if (issue.issueKey?.trim()) {
    const key = issue.issueKey.trim()
    const parts = key.split('|')
    if (parts.length >= 2) {
      parts[1] = normalizePath(parts[1])
      return parts.join('|')
    }
    return key
  }
  return `${issue.ruleId}|${path}|${issue.line}`
}

/** 空 catch 等可批量忽略的噪音项 */
export function isIgnorableAuditIssue(issue: AuditIssueItem): boolean {
  return issue.ruleId === 'CW-EXC-001' || issue.description.includes('catch 块为空')
}

/** pom.xml 依赖声明类问题，通常不影响运行时 */
export function isDependencyConfigIssue(issue: AuditIssueItem): boolean {
  return issue.ruleId.startsWith('CW-DEP-') || issue.category === 'DEPENDENCY'
}

/** 默认折叠展示的低影响项（仍可手动展开查看） */
export function isLowImpactAuditIssue(issue: AuditIssueItem): boolean {
  if (isIgnorableAuditIssue(issue)) return true
  if (isDependencyConfigIssue(issue) && issue.riskLevel === 'LOW') return true
  return false
}

export function formatAuditPath(path: string, maxLen = 44): string {
  const normalized = normalizePath(path)
  if (normalized.length <= maxLen) return normalized
  const head = Math.max(8, Math.floor(maxLen * 0.35))
  const tail = maxLen - head - 1
  return `${normalized.slice(0, head)}…${normalized.slice(-tail)}`
}

export function shouldCollapseAuditText(text: string, threshold = 96): boolean {
  return text.trim().length > threshold
}
