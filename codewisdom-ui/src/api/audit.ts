import { request } from './http'

export type RiskLevel = 'HIGH' | 'MEDIUM' | 'LOW'

export interface AuditIssueItem {
  issueKey: string
  ruleId: string
  category: string
  riskLevel: RiskLevel
  filePath: string
  line: number
  description: string
  riskNote: string
  triggerSnippet: string
}

export interface AuditReportView {
  total: number
  highCount: number
  mediumCount: number
  lowCount: number
  issues: AuditIssueItem[]
  javaFilesTotal?: number
  javaFilesScanned?: number
  parseSkippedCount?: number
  scanTruncated?: boolean
  scanNote?: string
}

export interface ProjectScore {
  overall: number
  bugRecall: number
  falsePositiveRate: number
  architectureAccuracy: number
  ruleMatchRate: number
  documentationScore: number
  summary: string
}

export function runProjectAudit(projectId: number) {
  return request<AuditReportView>({
    url: `/code-analysis/projects/${projectId}/audit`,
    method: 'POST',
  })
}

export function fetchProjectScore(projectId: number) {
  return request<ProjectScore>({
    url: `/evaluation-export/projects/${projectId}/score`,
    method: 'GET',
  })
}
