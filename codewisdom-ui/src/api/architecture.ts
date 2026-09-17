import { request } from './http'

export interface ArchitectureView {
  layerDiagram: string
  packageDiagram: string
  typeCount: number
  callEdgeCount: number
}

export function fetchProjectArchitecture(projectId: number) {
  return request<ArchitectureView>({
    url: `/code-analysis/projects/${projectId}/architecture`,
    method: 'GET',
  })
}
