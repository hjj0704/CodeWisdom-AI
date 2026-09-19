import { request } from './http'

export interface TypeDocGenRequest {
  qualifiedName: string
  kind: string
  classCommentHint?: string
  methods: Array<{
    name: string
    returnType?: string | null
    constructor: boolean
    parameters: Array<{ name: string; type: string }>
  }>
}

export interface DocGenResponse {
  skipped: boolean
  typeComment: string
  methodComments: Record<string, string>
  message: string
}

export function generateJavadoc(projectId: number, body: TypeDocGenRequest) {
  return request<DocGenResponse>({
    url: `/agent-orchestration/projects/${projectId}/doc/javadoc`,
    method: 'POST',
    data: body,
  })
}
