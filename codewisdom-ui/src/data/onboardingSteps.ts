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
    title: '欢迎使用 CodeWisdom',
    body: '这是一个帮你「看代码、找问题、试着改一改」的在线工具。你可以先随便看看页面，真正导入项目时需要登录账号。',
    illustration: 'welcome',
  },
  {
    id: 'my-projects',
    title: '我的项目',
    body: '登录后，你导入过的项目会出现在这里。点一下卡片就能继续上次的工作。',
    target: '[data-tour="my-projects"]',
    illustration: 'projects',
    interactive: true,
    interactiveHint: '登录后可见项目列表；现在可点「下一步」继续',
  },
  {
    id: 'import-git',
    title: '导入代码',
    body: '有两种方式：粘贴网上的项目链接（Git），或上传电脑里的 ZIP 压缩包。导入前需要登录，我们只把代码存到你的账号下，不会挪作他用。',
    target: '[data-tour="import-git"]',
    illustration: 'import',
    interactive: true,
    interactiveHint: '可先看看表单，或点「下一步」',
  },
  {
    id: 'nav-help',
    title: '帮助说明',
    body: '顶部「帮助」里有更详细的功能说明（后续会补充截图与视频）。',
    target: '[data-tour="nav-help"]',
    illustration: 'help',
    interactive: true,
    interactiveHint: '可点开帮助看看，或点「完成」结束引导',
  },
]

export const workbenchTourSteps: TourStep[] = [
  {
    id: 'wb-welcome',
    title: '工作台一览',
    body: '左边选文件，中间看和改代码，右边可以和 AI 对话。下面会教你常用按钮是干什么的。',
    illustration: 'workbench',
  },
  {
    id: 'wb-tree',
    title: '文件列表',
    body: '像文件夹一样：点文件名打开代码，点文件夹名展开或收起。',
    target: '[data-tour="wb-tree"]',
    illustration: 'workbench',
  },
  {
    id: 'wb-editor',
    title: '代码区域',
    body: '选中的文件会显示在这里。有问题的地方会用颜色标出来，方便你找到。',
    target: '[data-tour="wb-editor"]',
    illustration: 'workbench',
    interactive: true,
    interactiveHint: '打开任意文件后点「下一步」',
  },
  {
    id: 'wb-audit',
    title: '检查代码',
    body: '点这里让系统自动扫一遍，列出可能有问题的地方（类似「体检报告」）。',
    target: '[data-tour="wb-audit"]',
    illustration: 'audit',
    interactive: true,
    interactiveHint: '可点「检查代码」试试，或点「下一步」',
  },
  {
    id: 'wb-fix',
    title: '修改建议',
    body: '检查完后，可以在这里生成「怎么改」的建议，你再决定是否采纳。',
    target: '[data-tour="wb-fix"]',
    illustration: 'fix',
  },
  {
    id: 'wb-ai',
    title: 'AI 助手',
    body: '不懂某段代码或某个问题时，可以问 AI。它的回答需要你确认后才会写进文件。',
    target: '[data-tour="wb-ai"]',
    illustration: 'ai',
    interactive: true,
    interactiveHint: '可点开 AI 面板，或点「完成」',
  },
]
