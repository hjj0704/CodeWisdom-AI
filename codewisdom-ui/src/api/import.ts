import { request } from './http'

export interface ImportResult {
  projectId: number
  taskId: number
  status: string
  branch: string | null
  headCommit: string | null
  fileCount: number
  totalSize: number
  message: string
}

export interface GitImportPayload {
  url: string
  branch?: string
  name?: string
}

export function importFromGit(payload: GitImportPayload) {
  return request<ImportResult>({
    url: '/project-resource/import/git',
    method: 'POST',
    data: payload,
  })
}

export function importFromZip(file: File, name?: string) {
  const form = new FormData()
  form.append('file', file)
  if (name) {
    form.append('name', name)
  }
  return request<ImportResult>({
    url: '/project-resource/import/zip',
    method: 'POST',
    data: form,
  })
}

export function pingGateway() {
  return request<string>({
    url: '/project-resource/ping',
    method: 'GET',
  })
}
