import { request } from './http'

export interface ChatSession {
  id: number
  projectId: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: number
  sessionId: number
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt: string
}

export interface ClarifyOption {
  id: string
  label: string
}

export interface AgentAction {
  type: string
  scope?: string
  summary: string
  requiresConfirmation: boolean
  filePath?: string | null
  symbol?: string | null
  line?: number | null
  clarifyQuestion?: string | null
  options?: ClarifyOption[] | null
}

export interface WorkbenchContext {
  filePath?: string
  selectionStartLine?: number
  selectionEndLine?: number
  viewportStartLine?: number
  viewportEndLine?: number
  selectionSnippet?: string
  fileContent?: string
  javaFilePaths?: string[]
}

export interface ChatReply {
  userMessage: ChatMessage
  assistantMessage: ChatMessage
  actions?: AgentAction[]
}

export function listSessions(projectId: number) {
  return request<ChatSession[]>({
    url: `/agent-orchestration/projects/${projectId}/sessions`,
    method: 'GET',
  })
}

export function createSession(projectId: number, title?: string) {
  return request<ChatSession>({
    url: `/agent-orchestration/projects/${projectId}/sessions`,
    method: 'POST',
    data: { title },
  })
}

export function listMessages(sessionId: number) {
  return request<ChatMessage[]>({
    url: `/agent-orchestration/sessions/${sessionId}/messages`,
    method: 'GET',
  })
}

export function sendChat(sessionId: number, content: string, workbench?: WorkbenchContext) {
  return request<ChatReply>({
    url: `/agent-orchestration/sessions/${sessionId}/chat`,
    method: 'POST',
    data: { content, workbench },
  })
}

export function onboardProject(projectId: number) {
  return request<ChatReply>({
    url: `/agent-orchestration/projects/${projectId}/onboard`,
    method: 'POST',
  })
}

export function deleteSession(sessionId: number) {
  return request<null>({
    url: `/agent-orchestration/sessions/${sessionId}`,
    method: 'DELETE',
  })
}
