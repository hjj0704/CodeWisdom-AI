import { request, http } from './http'

export interface FileTreeNode {
  id: number | null
  parentId: number | null
  name: string
  path: string
  type: 'DIR' | 'FILE'
  language: string | null
  category: string
  size: number
  children: FileTreeNode[]
}

export interface FileTreeStats {
  totalNodes: number
  directoryCount: number
  fileCount: number
  totalSize: number
  byCategory: Record<string, number>
  byLanguage: Record<string, number>
  maxDepth: number
}

export interface FileContent {
  path: string
  language: string | null
  size: number
  binary: boolean
  content: string | null
}

export interface ProjectMeta {
  id: number
  name: string
  sourceType: 'GIT' | 'ZIP' | 'FILES'
  sourceUrl: string | null
  defaultBranch: string | null
  exportEnabled: boolean
}

export interface ProjectListItem {
  id: number
  name: string
  sourceType: 'GIT' | 'ZIP' | 'FILES'
  sourceUrl: string | null
  fileCount: number
  totalSize: number
  updatedAt: string
}

export function fetchProjectList() {
  return request<ProjectListItem[]>({
    url: '/project-resource/projects',
    method: 'GET',
  })
}

export function fetchProjectMeta(projectId: number) {
  return request<ProjectMeta>({
    url: `/project-resource/projects/${projectId}`,
    method: 'GET',
  })
}

export function fetchProjectTree(projectId: number) {
  return request<FileTreeNode>({
    url: `/project-resource/projects/${projectId}/tree`,
    method: 'GET',
  })
}

export function fetchProjectStats(projectId: number) {
  return request<FileTreeStats>({
    url: `/project-resource/projects/${projectId}/stats`,
    method: 'GET',
  })
}

export function fetchFileContent(projectId: number, path: string) {
  return request<FileContent>({
    url: `/project-resource/projects/${projectId}/files/content`,
    method: 'GET',
    params: { path },
  })
}

export function downloadProjectZip(projectId: number) {
  return http.get(`/project-resource/projects/${projectId}/export.zip`, {
    responseType: 'blob',
  })
}

export function saveFileContent(projectId: number, path: string, content: string) {
  return request<FileContent>({
    url: `/project-resource/projects/${projectId}/files/content`,
    method: 'PUT',
    data: { path, content },
  })
}

export function toTreeData(node: FileTreeNode): TreeNode[] {
  if (!node.children?.length) return []
  return node.children.map((child) => ({
    label: child.name,
    path: child.path,
    isLeaf: child.type === 'FILE',
    children: child.type === 'DIR' ? toTreeData(child) : undefined,
  }))
}

export interface TreeNode {
  label: string
  path: string
  isLeaf?: boolean
  children?: TreeNode[]
}
