import { request } from './http'

export interface RunProfile {
  capability: 'LIGHTWEIGHT' | 'HEAVY'
  middlewareDependencies: string[]
  deployGuideMarkdown: string
  summary: string
}

export interface SandboxCheckResult {
  allowed: boolean
  message: string
  maxExecutionSeconds: number
  maxMemoryMb: number
  maxCommandLength: number
  networkIsolation: boolean
}

export function fetchRunProfile(projectId: number) {
  return request<RunProfile>({
    url: `/evaluation-export/projects/${projectId}/run-profile`,
    method: 'GET',
  })
}

export function validateSandboxCommand(projectId: number, command: string) {
  return request<SandboxCheckResult>({
    url: `/evaluation-export/projects/${projectId}/sandbox/validate`,
    method: 'POST',
    data: { command },
  })
}

export interface SandboxRunResult {
  success: boolean
  exitCode: number
  stdout: string
  stderr: string
  durationMs: number
  message: string
}

export interface DemoLinkResult {
  found: boolean
  url: string | null
  label: string | null
  sourceFile: string | null
  message: string
}

export function discoverDemoLink(projectId: number, sourceUrl?: string | null) {
  return request<DemoLinkResult>({
    url: `/evaluation-export/projects/${projectId}/sandbox/demo-link`,
    method: 'GET',
    params: sourceUrl ? { sourceUrl } : undefined,
  })
}

export function runSandboxDemo(projectId: number, command: string) {
  return request<SandboxRunResult>({
    url: `/evaluation-export/projects/${projectId}/sandbox/run`,
    method: 'POST',
    data: { command },
  })
}
