import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ImportResult } from '../api/import'

export const useProjectStore = defineStore('project', () => {
  const lastImport = ref<ImportResult | null>(null)
  const currentProjectId = ref<number | null>(null)

  function setImportResult(result: ImportResult) {
    lastImport.value = result
    currentProjectId.value = result.projectId
  }

  return { lastImport, currentProjectId, setImportResult }
})
