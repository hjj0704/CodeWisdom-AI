import type { AuditIssueItem } from '../api/audit'

export function auditIssueKey(issue: Pick<AuditIssueItem, 'issueKey' | 'ruleId' | 'filePath' | 'line'>): string {
  if (issue.issueKey?.trim()) return issue.issueKey
  return `${issue.ruleId}|${issue.filePath}|${issue.line}`
}

export function isIgnorableAuditIssue(issue: AuditIssueItem): boolean {
  return issue.ruleId === 'CW-EXC-001' || issue.description.includes('catch 块为空')
}
