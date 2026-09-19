import type { TreeNode } from '../api/project'

export function collectJavaFilePaths(nodes: TreeNode[], limit = 200): string[] {
  const out: string[] = []
  function walk(list: TreeNode[]) {
    for (const node of list) {
      if (node.isLeaf && node.path.toLowerCase().endsWith('.java')) {
        out.push(node.path)
        if (out.length >= limit) return
      }
      if (node.children?.length) walk(node.children)
      if (out.length >= limit) return
    }
  }
  walk(nodes)
  return out
}
