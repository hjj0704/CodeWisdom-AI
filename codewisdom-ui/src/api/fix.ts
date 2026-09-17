import { request } from './http'
import type { AuditIssueItem, RiskLevel } from './audit'

export interface FixRecord {
  id: number
  issueKey: string
  ruleId: string
  filePath: string
  lineNo: number
  riskLevel: string
  suggestion: string
  rationale: string
  diffChanged: boolean
  createdAt: string
}

export interface StructuredDiff {
  filePath: string
  beforeContent: string
  afterContent: string
  changed: boolean
  hunks: DiffHunk[]
}

export interface DiffHunk {
  oldStart: number
  oldLines: number
  newStart: number
  newLines: number
  lines: string[]
}

export type HitlReviewStatus = 'PENDING' | 'APPROVED' | 'MODIFIED' | 'REJECTED'

export interface HitlState {
  round: number
  approved: boolean
  terminated: boolean
  lastStatus: HitlReviewStatus
}

export function suggestFixes(projectId: number, issues: AuditIssueItem[]) {
  return request<FixRecord[]>({
    url: `/agent-orchestration/projects/${projectId}/fix/suggest`,
    method: 'POST',
    data: {
      issues: issues.map((i) => ({
        ruleId: i.ruleId,
        filePath: i.filePath,
        line: i.line,
        description: i.description,
        riskNote: i.riskNote,
        riskLevel: i.riskLevel as RiskLevel,
      })),
    },
  })
}

export function listFixRecords(projectId: number) {
  return request<FixRecord[]>({
    url: `/agent-orchestration/projects/${projectId}/fix/records`,
    method: 'GET',
  })
}

export function previewDiff(
  projectId: number,
  payload: { filePath: string; beforeContent: string; afterContent: string; fixRecordId?: number },
) {
  return request<StructuredDiff>({
    url: `/agent-orchestration/projects/${projectId}/fix/diff`,
    method: 'POST',
    data: payload,
  })
}

export function fetchHitlState(projectId: number, sessionKey?: string) {
  return request<HitlState>({
    url: `/agent-orchestration/projects/${projectId}/fix/hitl`,
    method: 'GET',
    params: sessionKey ? { sessionKey } : undefined,
  })
}

export function submitHitl(
  projectId: number,
  payload: { status: HitlReviewStatus; fixRecordId?: number; sessionKey?: string },
) {
  return request<HitlState>({
    url: `/agent-orchestration/projects/${projectId}/fix/hitl`,
    method: 'POST',
    data: payload,
  })
}
