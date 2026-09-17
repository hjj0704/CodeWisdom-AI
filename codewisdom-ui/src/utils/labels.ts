/** 将后端枚举值转为界面展示文案 */
export function hitlStatusLabel(status: string | null | undefined): string {
  switch (status) {
    case 'APPROVED':
      return '已确认'
    case 'MODIFIED':
      return '已修改确认'
    case 'REJECTED':
      return '已驳回'
    case 'PENDING':
      return '待审核'
    default:
      return status || '—'
  }
}

export function evalStatusLabel(status: string | null | undefined): string {
  switch (status) {
    case 'COMPLETED':
      return '已完成'
    case 'PENDING':
      return '进行中'
    case 'FAILED':
      return '失败'
    default:
      return status || '—'
  }
}

export function runCapabilityLabel(capability: string | null | undefined): string {
  switch (capability) {
    case 'HEAVY':
      return '重型项目'
    case 'LIGHT':
      return '轻量项目'
    default:
      return capability || '—'
  }
}
