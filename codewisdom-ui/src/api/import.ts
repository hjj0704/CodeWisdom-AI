import { request } from './http'

export interface ImportResult {
  projectId: number
  taskId: number
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  branch: string | null
  headCommit: string | null
  fileCount: number
  totalSize: number
  message: string
}

function assertImportSuccess(result: ImportResult): ImportResult {
  if (result.status === 'SUCCESS') {
    return result
  }
  throw new Error(result.message || `导入失败（${result.status}）`)
}

export interface GitImportPayload {
  url: string
  branch?: string
  name?: string
  /** Git 导入是否允许后续 ZIP 导出，默认 true */
  exportEnabled?: boolean
}

export function importFromGit(payload: GitImportPayload) {
  return request<ImportResult>({
    url: '/project-resource/import/git',
    method: 'POST',
    data: payload,
  }).then(assertImportSuccess)
}

export function importFromZip(file: File, name?: string) {
  const form = new FormData()
  form.append('file', file, file.name || 'upload.zip')
  if (name) {
    form.append('name', name)
  }
  return request<ImportResult>({
    url: '/project-resource/import/zip',
    method: 'POST',
    data: form,
  }).then(assertImportSuccess)
}

export function pingGateway() {
  return request<string>({
    url: '/project-resource/ping',
    method: 'GET',
  })
}
