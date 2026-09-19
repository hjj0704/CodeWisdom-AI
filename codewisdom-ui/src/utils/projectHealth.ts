const HEALTH_KEY = 'cw_project_health'

export interface ProjectHealthSnapshot {
  projectId: number
  totalIssues: number
  highCount: number
  mediumCount: number
  lowCount: number
  overallScore?: number
  auditedAt: string
}

function readAll(): Record<string, ProjectHealthSnapshot> {
  try {
    return JSON.parse(localStorage.getItem(HEALTH_KEY) ?? '{}') as Record<string, ProjectHealthSnapshot>
  } catch {
    return {}
  }
}

function writeAll(data: Record<string, ProjectHealthSnapshot>) {
  localStorage.setItem(HEALTH_KEY, JSON.stringify(data))
}

export function saveProjectHealth(snapshot: ProjectHealthSnapshot) {
  const all = readAll()
  all[String(snapshot.projectId)] = snapshot
  writeAll(all)
}

export function loadProjectHealth(projectId: number): ProjectHealthSnapshot | null {
  return readAll()[String(projectId)] ?? null
}

export function loadAllProjectHealth(): Map<number, ProjectHealthSnapshot> {
  const map = new Map<number, ProjectHealthSnapshot>()
  for (const [key, value] of Object.entries(readAll())) {
    const id = Number(key)
    if (Number.isFinite(id)) map.set(id, value)
  }
  return map
}
