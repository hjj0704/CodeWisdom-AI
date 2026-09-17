import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ImportResult } from '../api/import'

const RECENT_KEY = 'cw_recent_projects'
const RECENT_LIMIT = 12

export interface RecentProject {
  projectId: number
  name: string
  fileCount: number
  importedAt: string
}

function loadRecent(): RecentProject[] {
  const raw = localStorage.getItem(RECENT_KEY)
  if (!raw) return []
  try {
    return JSON.parse(raw) as RecentProject[]
  } catch {
    return []
  }
}

export const useProjectStore = defineStore('project', () => {
  const lastImport = ref<ImportResult | null>(null)
  const currentProjectId = ref<number | null>(null)
  const recentProjects = ref<RecentProject[]>(loadRecent())

  function persistRecent() {
    localStorage.setItem(RECENT_KEY, JSON.stringify(recentProjects.value))
  }

  function setImportResult(result: ImportResult, name?: string) {
    lastImport.value = result
    currentProjectId.value = result.projectId
    const entry: RecentProject = {
      projectId: result.projectId,
      name: name?.trim() || `项目 #${result.projectId}`,
      fileCount: result.fileCount,
      importedAt: new Date().toISOString(),
    }
    recentProjects.value = [
      entry,
      ...recentProjects.value.filter((p) => p.projectId !== result.projectId),
    ].slice(0, RECENT_LIMIT)
    persistRecent()
  }

  function removeRecent(projectId: number) {
    recentProjects.value = recentProjects.value.filter((p) => p.projectId !== projectId)
    persistRecent()
  }

  return {
    lastImport,
    currentProjectId,
    recentProjects,
    setImportResult,
    removeRecent,
  }
})
