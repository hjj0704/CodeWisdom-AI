<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const activeSection = ref('overview')
const contentRef = ref<HTMLElement | null>(null)

const sections = [
  { id: 'overview', title: '平台概述' },
  { id: 'import', title: '项目导入' },
  { id: 'workbench', title: '工作台' },
  { id: 'audit', title: '运行审计' },
  { id: 'risk', title: '风险等级' },
  { id: 'fix', title: '修复建议' },
  { id: 'diff', title: '预览 Diff' },
  { id: 'hitl', title: '人工确认 HITL' },
  { id: 'ai', title: 'AI 分析' },
  { id: 'arch', title: '架构图' },
  { id: 'export', title: '导出与沙箱' },
]

let observer: IntersectionObserver | null = null

function scrollToSection(id: string) {
  const el = document.getElementById(id)
  if (!el) return
  activeSection.value = id
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  router.replace({ hash: `#${id}` })
}

function setupObserver() {
  observer?.disconnect()
  observer = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((e) => e.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)
      if (visible[0]?.target.id) {
        activeSection.value = visible[0].target.id
      }
    },
    { rootMargin: '-20% 0px -55% 0px', threshold: [0, 0.25, 0.5, 1] },
  )
  for (const s of sections) {
    const el = document.getElementById(s.id)
    if (el) observer.observe(el)
  }
}

onMounted(() => {
  const hash = route.hash.replace('#', '')
  if (hash && sections.some((s) => s.id === hash)) {
    activeSection.value = hash
    setTimeout(() => scrollToSection(hash), 100)
  }
  setupObserver()
})

onUnmounted(() => {
  observer?.disconnect()
})
</script>

<template>
  <div class="help-page">
    <header class="help-hero glass-panel">
      <span class="section-label">Documentation</span>
      <h1 class="gradient-text">帮助中心</h1>
      <p>了解 CodeWisdom 每个功能的作用与推荐操作顺序。辅助分析结果均需人工确认。</p>
    </header>

    <div class="help-layout">
      <nav class="help-nav glass-panel">
        <button
          v-for="s in sections"
          :key="s.id"
          type="button"
          class="help-nav__link"
          :class="{ active: activeSection === s.id }"
          @click="scrollToSection(s.id)"
        >
          {{ s.title }}
        </button>
      </nav>

      <article ref="contentRef" class="help-content glass-panel">
        <section id="overview" class="help-section">
          <h2>平台概述</h2>
          <p>CodeWisdom 是多智能体代码治理平台：导入 Java 项目 → 静态审计 → 生成修复建议 → 人工确认 → 可选 AI 解读与架构可视化。</p>
          <p><strong>推荐流程：</strong>导入 → 审计 → 修复建议 → Diff 确认 → 保存 → HITL 确认 → ZIP 导出。</p>
        </section>

        <section id="import" class="help-section">
          <h2>项目导入</h2>
          <ul>
            <li><strong>Git 导入：</strong>填写仓库 URL，可选分支与项目名；支持控制是否允许 ZIP 导出。</li>
            <li><strong>ZIP 导入：</strong>上传压缩包，系统自动解压并构建文件树。</li>
            <li>导入成功后进入工作台；「我的项目」列表可快速 reopen。</li>
          </ul>
        </section>

        <section id="workbench" class="help-section">
          <h2>工作台</h2>
          <ul>
            <li><strong>文件树：</strong>点击文件查看源码；可收起/拖动宽度。</li>
            <li><strong>代码区：</strong>可拖动高度；审计问题按风险着色（红/橙/黄）；当前修复建议行绿色；跳转定位行金色。</li>
            <li><strong>修复流程条：</strong>① 审计 → ② 生成建议 → ③ 应用或 AI 改 → ④ 预览 Diff → ⑤ 保存并 HITL。</li>
            <li><strong>AI 分析：</strong>默认收起；「AI 帮我改」会在会话里给出改法，需你点「采用」或「忽略」后才写入编辑器。</li>
          </ul>
        </section>

        <section id="audit" class="help-section">
          <h2>运行审计</h2>
          <p>扫描项目中的潜在问题（空指针、死代码、异常处理等），按高/中/低分级。</p>
          <p>审计完成后：文件树节点显示问题数角标（颜色对应该文件最高风险）；下方列表点击可跳转到对应文件行。</p>
          <p>空 catch 等低风险项可「忽略」或开启「隐藏空 catch」，不影响你处理高优先级问题。</p>
        </section>

        <section id="risk" class="help-section">
          <h2>风险等级</h2>
          <p>编辑器行左侧圆点与背景色、审计列表标签、文件树角标均遵循同一套含义：</p>
          <ul>
            <li><strong class="risk-doc-high">高（红色）</strong>：可能影响业务逻辑或运行正确性，建议优先处理。</li>
            <li><strong class="risk-doc-medium">中（橙色）</strong>：不一定会立刻出错，但在预发布、质量门禁或扩展时容易出问题。</li>
            <li><strong class="risk-doc-low">低（黄色）</strong>：多为规范性问题；若你确认可接受，可忽略或隐藏（如空 catch）。</li>
          </ul>
          <p>颜色用于<strong>辅助排序与定位</strong>，不能代替 Diff 与 HITL 人工确认。</p>
        </section>

        <section id="fix" class="help-section">
          <h2>修复建议</h2>
          <p>审计完成后点击「<strong>生成修复建议</strong>」（不是「恢复」）；系统针对中/高危等问题给出改法（AI 不可用时使用规则模板）。</p>
          <p><strong>模板建议（操作条）：</strong></p>
          <ol>
            <li>在下方列表点「定位并应用」或选中建议，自动打开文件；</li>
            <li><strong>写入编辑器（应用建议）：</strong>尝试自动改写目标行；失败时请对照说明手动改；</li>
            <li><strong>预览 Diff：</strong>对比「已保存版本」与「编辑器当前内容」；</li>
            <li><strong>保存</strong>后，在 HITL 区确认；</li>
            <li><strong>放弃该条建议：</strong>跳过本条，继续下一条。</li>
          </ol>
          <p><strong>AI 帮我改：</strong>针对单条审计问题自动发问；AI 回复后请在会话中点「<strong>采用 AI 修改</strong>」写入编辑器，或「<strong>忽略此警告</strong>」「暂不处理」。写入后同样要走 Diff → 保存 → HITL。</p>
        </section>

        <section id="diff" class="help-section">
          <h2>预览 Diff</h2>
          <p><strong>Diff 对比的是什么？</strong></p>
          <ul>
            <li><strong>红色（-）：</strong>上次<strong>保存到服务器</strong>的代码；</li>
            <li><strong>绿色（+）：</strong>编辑器<strong>当前内容</strong>（应用建议或手动修改后）。</li>
          </ul>
          <p>典型用法：应用建议或采用 AI 修改 → 预览 Diff → 满意则「保存」→ HITL「确认」。</p>
          <p>若 Diff 显示「无变化」，说明编辑器内容与已保存版本相同，请先应用建议或手动修改。</p>
        </section>

        <section id="hitl" class="help-section">
          <h2>人工确认 HITL</h2>
          <p>Human-in-the-Loop：修复流程必须有人工把关。</p>
          <ul>
            <li><strong>确认：</strong>接受当前修改并保存文件；</li>
            <li><strong>修改后确认：</strong>你手动改过再确认；</li>
            <li><strong>驳回：</strong>拒绝本次修复方向，可重新生成建议。</li>
          </ul>
        </section>

        <section id="ai" class="help-section">
          <h2>AI 分析</h2>
          <p>会先读取 README、pom 等文档，再结合源码抽样，用五段式说明：定位 / 用途 / 技术 / 结构 / 建议。</p>
          <p>适合快速了解「这是什么项目、给谁用、优先改什么」，不能替代审计与 Diff 确认。</p>
          <p>对具体代码问题请用审计列表中的「AI 帮我改」，并在会话里<strong>手动确认</strong>后再写入编辑器。</p>
        </section>

        <section id="arch" class="help-section">
          <h2>架构图</h2>
          <p>基于解析结果生成分层图与包拓扑（Mermaid），可缩放、下载 SVG/Mermaid 源文件。属辅助分析，需人工核对。</p>
        </section>

        <section id="export" class="help-section">
          <h2>导出与沙箱</h2>
          <ul>
            <li><strong>下载 ZIP：</strong>导出当前项目副本（Git 导入时可关闭此能力）；</li>
            <li><strong>沙箱校验：</strong>在「更多分析 → 运行判定」中校验命令是否允许执行；</li>
            <li><strong>评测报告：</strong>量化评分与 Markdown 报告，供迭代参考。</li>
          </ul>
        </section>
      </article>
    </div>
  </div>
</template>

<style scoped>
.help-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.help-hero {
  padding: 24px 28px;
}

.help-hero h1 {
  margin: 8px 0;
  font-size: 28px;
}

.help-hero p {
  margin: 0;
  font-size: 16px;
  color: var(--cw-text-muted);
  line-height: 1.65;
}

.help-layout {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 16px;
  align-items: start;
}

.help-nav {
  position: sticky;
  top: 80px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: calc(100vh - 100px);
  overflow: auto;
}

.help-nav__link {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 15px;
  color: var(--cw-text-muted);
  text-decoration: none;
  text-align: left;
  border: none;
  background: transparent;
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
}

.help-nav__link:hover,
.help-nav__link.active {
  color: var(--cw-accent);
  background: rgba(34, 211, 238, 0.1);
}

.help-content {
  padding: 28px 32px;
  scroll-behavior: smooth;
}

.help-section {
  margin-bottom: 40px;
  scroll-margin-top: 96px;
}

.help-section h2 {
  margin: 0 0 16px;
  font-size: 22px;
  font-weight: 700;
  color: var(--cw-text);
}

.help-section p,
.help-section li {
  font-size: 16px;
  line-height: 1.85;
  color: var(--cw-text);
}

.help-section strong {
  color: var(--cw-accent);
  font-weight: 600;
}

.help-section ul,
.help-section ol {
  padding-left: 22px;
  margin: 12px 0;
}

.help-section li {
  margin-bottom: 8px;
}

.risk-doc-high {
  color: #fca5a5 !important;
}

.risk-doc-medium {
  color: #fdba74 !important;
}

.risk-doc-low {
  color: #fde047 !important;
}

@media (max-width: 800px) {
  .help-layout {
    grid-template-columns: 1fr;
  }

  .help-nav {
    position: static;
    flex-direction: row;
    flex-wrap: wrap;
    max-height: none;
  }

  .help-content {
    padding: 20px;
  }
}
</style>
