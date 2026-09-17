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

export interface ChatReply {
  userMessage: ChatMessage
  assistantMessage: ChatMessage
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

export function sendChat(sessionId: number, content: string) {
  return request<ChatReply>({
    url: `/agent-orchestration/sessions/${sessionId}/chat`,
    method: 'POST',
    data: { content },
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
