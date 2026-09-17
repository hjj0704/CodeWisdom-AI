import { request } from './http'

export interface EvalReport {
  reportId: number
  projectId: number
  status: string
  overallScore: number
  metricsJson: string
  traceJson: string
  reportMarkdown: string
}

export function fetchEvalReport(projectId: number) {
  return request<EvalReport>({
    url: `/evaluation-export/projects/${projectId}/eval-report`,
    method: 'get',
  })
}
