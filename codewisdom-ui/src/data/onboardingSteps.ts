export type TourIllustration = 'welcome' | 'import' | 'projects' | 'workbench' | 'audit' | 'fix' | 'ai' | 'help'

export interface TourStep {
  id: string
  title: string
  body: string
  target?: string
  illustration?: TourIllustration
  /** 需用户点击高亮区域后继续 */
  interactive?: boolean
  interactiveHint?: string
  route?: string
}

export const importTourSteps: TourStep[] = [
  {
    id: 'welcome',
    title: '欢迎加入 CodeWisdom',
    body: '接下来用可点击的引导熟悉平台：你可以直接点高亮区域操作，或点「下一步」继续。',
    illustration: 'welcome',
  },
  {
    id: 'my-projects',
    title: '打开已有项目',
    body: '「我的项目」列出账号下已导入的项目。点击任意卡片可进入工作台（若列表为空，请先完成下方导入）。',
    target: '[data-tour="my-projects"]',
    illustration: 'projects',
    interactive: true,
    interactiveHint: '点击项目卡片进入工作台，或点「下一步」',
  },
  {
    id: 'import-git',
    title: '导入新项目',
    body: '填写 Git 地址或切换到 ZIP 上传。导入成功后会自动跳转到工作台。',
    target: '[data-tour="import-git"]',
    illustration: 'import',
    interactive: true,
    interactiveHint: '可在此填写仓库地址体验，或点「下一步」',
  },
  {
    id: 'nav-help',
    title: '随时查阅帮助',
    body: '顶部「帮助」有完整功能文档。进入工作台后选中文件，还会有一次工作台专属引导。',
    target: '[data-tour="nav-help"]',
    illustration: 'help',
    interactive: true,
    interactiveHint: '点击「帮助」查看文档，或点「完成」结束导入引导',
  },
]

export const workbenchTourSteps: TourStep[] = [
  {
    id: 'wb-tree',
    title: '文件树',
    body: '左侧浏览项目结构。点击文件名查看源码；文件夹名可展开/收起。可拖动边缘调整宽度。',
    target: '[data-tour="wb-tree"]',
    illustration: 'workbench',
  },
  {
    id: 'wb-editor',
    title: '代码编辑区',
    body: '选中文件后在此阅读/编辑代码。审计问题行红色高亮，修复建议目标行绿色高亮。可拖动下方横条调整高度。',
    target: '[data-tour="wb-editor"]',
    illustration: 'workbench',
    interactive: true,
    interactiveHint: '你已成功打开文件！点「下一步」继续',
  },
  {
    id: 'wb-audit',
    title: '运行审计',
    body: '点击扫描代码问题，结果会显示在文件树角标和下方列表。这是修复流程的第一步。',
    target: '[data-tour="wb-audit"]',
    illustration: 'audit',
    interactive: true,
    interactiveHint: '可点击「运行审计」体验，或点「下一步」',
  },
  {
    id: 'wb-fix',
    title: '生成修复建议',
    body: '审计后点此生成建议。选中建议会出现「一键应用 / 预览 Diff / 放弃」操作条。',
    target: '[data-tour="wb-fix"]',
    illustration: 'fix',
  },
  {
    id: 'wb-ai',
    title: 'AI 分析',
    body: 'AI 会先读 README 等文档，再总结项目定位。适合快速了解项目用途，不能替代 Diff 确认。',
    target: '[data-tour="wb-ai"]',
    illustration: 'ai',
    interactive: true,
    interactiveHint: '可点击打开 AI 面板，或点「完成」结束引导',
  },
]
