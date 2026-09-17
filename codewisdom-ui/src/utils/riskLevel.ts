import type { RiskLevel } from '../api/audit'

/** 界面文案：高=影响业务逻辑，中=预发布/质量风险，低=规范性问题 */
export function riskLevelLabel(level: RiskLevel | string): string {
  switch (level) {
    case 'HIGH':
      return '高 · 影响业务逻辑'
    case 'MEDIUM':
      return '中 · 质量/预发布风险'
    case 'LOW':
      return '低 · 规范性问题'
    default:
      return String(level)
  }
}

export function riskLevelShort(level: RiskLevel | string): string {
  switch (level) {
    case 'HIGH':
      return '高'
    case 'MEDIUM':
      return '中'
    case 'LOW':
      return '低'
    default:
      return String(level)
  }
}

export function riskTagType(level: RiskLevel | string): 'danger' | 'warning' | 'info' {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'info'
}

export function riskIssueClass(level: RiskLevel | string): string {
  if (level === 'HIGH') return 'risk-high'
  if (level === 'MEDIUM') return 'risk-medium'
  return 'risk-low'
}
