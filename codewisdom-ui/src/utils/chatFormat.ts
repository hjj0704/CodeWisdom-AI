export interface ChatSection {
  title: string
  body: string
}

const SECTION_MARKERS = ['①', '②', '③', '④', '⑤'] as const

/** 解析 AI 五段式回复；无法识别时返回 null，回退纯文本展示。 */
export function parseAssistantSections(content: string): ChatSection[] | null {
  const text = content.trim()
  if (!text) return null

  const hits = SECTION_MARKERS.map((m) => text.indexOf(m)).filter((i) => i >= 0)
  if (hits.length < 2) return null

  const sections: ChatSection[] = []
  for (let i = 0; i < SECTION_MARKERS.length; i++) {
    const marker = SECTION_MARKERS[i]
    const start = text.indexOf(marker)
    if (start < 0) continue

    let end = text.length
    for (let j = i + 1; j < SECTION_MARKERS.length; j++) {
      const next = text.indexOf(SECTION_MARKERS[j])
      if (next >= 0) {
        end = next
        break
      }
    }

    const chunk = text.slice(start, end).trim()
    const colonIdx = chunk.indexOf('：')
    if (colonIdx > 0) {
      sections.push({
        title: chunk.slice(0, colonIdx + 1),
        body: chunk.slice(colonIdx + 1).trim(),
      })
    } else {
      sections.push({ title: marker, body: chunk.slice(marker.length).trim() })
    }
  }

  return sections.length >= 2 ? sections : null
}
