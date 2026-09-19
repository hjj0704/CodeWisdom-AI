import type { AuditIssueItem } from '../api/audit'
import { auditIssueKey } from './audit'

const STORAGE_KEY = 'cw_audit_history'
const MAX_PER_PROJECT = 12

export interface AuditHistoryEntry {
  id: string
  projectId: number
  capturedAt: string
  total: number
  highCount: number
  mediumCount: number
  lowCount: number
  issueKeys: string[]
}

export interface AuditHistoryDiff {
  addedKeys: string[]
  removedKeys: string[]
  unchangedKeys: string[]
}

function readAll(): Record<string, AuditHistoryEntry[]> {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}') as Record<string, AuditHistoryEntry[]>
  } catch {
    return {}
  }
}

function writeAll(data: Record<string, AuditHistoryEntry[]>) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
}

export function listAuditHistory(projectId: number): AuditHistoryEntry[] {
  return readAll()[String(projectId)] ?? []
}

export function pushAuditHistory(projectId: number, issues: AuditIssueItem[]): AuditHistoryEntry {
  const all = readAll()
  const key = String(projectId)
  const list = all[key] ?? []
  const entry: AuditHistoryEntry = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    projectId,
    capturedAt: new Date().toISOString(),
    total: issues.length,
    highCount: issues.filter((i) => i.riskLevel === 'HIGH').length,
    mediumCount: issues.filter((i) => i.riskLevel === 'MEDIUM').length,
    lowCount: issues.filter((i) => i.riskLevel === 'LOW').length,
    issueKeys: issues.map((i) => auditIssueKey(i)),
  }
  const last = list[0]
  if (last && last.issueKeys.length === entry.issueKeys.length) {
    const same =
      last.issueKeys.length === entry.issueKeys.length &&
      last.issueKeys.every((k, idx) => k === entry.issueKeys[idx])
    if (same) return last
  }
  all[key] = [entry, ...list].slice(0, MAX_PER_PROJECT)
  writeAll(all)
  return entry
}

export function diffAuditHistory(
  previous: AuditHistoryEntry,
  currentKeys: string[],
): AuditHistoryDiff {
  const prevSet = new Set(previous.issueKeys)
  const curSet = new Set(currentKeys)
  const addedKeys: string[] = []
  const removedKeys: string[] = []
  const unchangedKeys: string[] = []
  for (const k of curSet) {
    if (prevSet.has(k)) unchangedKeys.push(k)
    else addedKeys.push(k)
  }
  for (const k of prevSet) {
    if (!curSet.has(k)) removedKeys.push(k)
  }
  return { addedKeys, removedKeys, unchangedKeys }
}

export function formatHistoryTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
