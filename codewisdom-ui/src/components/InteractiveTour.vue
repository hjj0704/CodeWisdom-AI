<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { TourIllustration, TourStep } from '../data/onboardingSteps'
import { skipAllOnboarding } from '../utils/onboarding'

const props = defineProps<{
  modelValue: boolean
  steps: TourStep[]
  onComplete?: () => void
}>()

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const router = useRouter()
const stepIndex = ref(0)
const targetRect = ref<DOMRect | null>(null)
const tourCardRef = ref<HTMLElement | null>(null)

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const current = computed(() => props.steps[stepIndex.value])
const isLast = computed(() => stepIndex.value >= props.steps.length - 1)
const hasTarget = computed(() => !!current.value?.target && !!targetRect.value)

const spotlightStyle = computed(() => {
  if (!targetRect.value) return {}
  const pad = 10
  return {
    top: `${Math.max(0, targetRect.value.top - pad)}px`,
    left: `${Math.max(0, targetRect.value.left - pad)}px`,
    width: `${targetRect.value.width + pad * 2}px`,
    height: `${targetRect.value.height + pad * 2}px`,
  }
})

const cardStyle = computed(() => {
  if (!targetRect.value) {
    return { top: '50%', left: '50%', transform: 'translate(-50%, -50%)' }
  }
  const gap = 16
  const cardW = 360
  let top = targetRect.value.bottom + gap
  let left = targetRect.value.left

  if (top + 280 > window.innerHeight) {
    top = Math.max(12, targetRect.value.top - 280 - gap)
  }
  if (left + cardW > window.innerWidth - 12) {
    left = window.innerWidth - cardW - 12
  }
  return { top: `${top}px`, left: `${Math.max(12, left)}px` }
})

function panelStyle(box: { top: number; left: number; width: number; height: number }) {
  return {
    top: `${box.top}px`,
    left: `${box.left}px`,
    width: `${box.width}px`,
    height: `${box.height}px`,
  }
}

const shadePanels = computed(() => {
  if (!targetRect.value) return null
  const pad = 10
  const t = Math.max(0, targetRect.value.top - pad)
  const l = Math.max(0, targetRect.value.left - pad)
  const r = targetRect.value.right + pad
  const b = targetRect.value.bottom + pad
  const w = window.innerWidth
  const h = window.innerHeight
  return {
    top: panelStyle({ top: 0, left: 0, width: w, height: t }),
    left: panelStyle({ top: t, left: 0, width: l, height: b - t }),
    right: panelStyle({ top: t, left: r, width: w - r, height: b - t }),
    bottom: panelStyle({ top: b, left: 0, width: w, height: h - b }),
  }
})

let highlightedEl: HTMLElement | null = null
let clickListener: ((e: MouseEvent) => void) | null = null
let blockListener: ((e: Event) => void) | null = null
let resizeListener: (() => void) | null = null

function clearHighlight() {
  highlightedEl?.classList.remove('tour-highlight-target')
  highlightedEl = null
}

function measureTarget() {
  clearHighlight()
  const selector = current.value?.target
  if (!selector) {
    targetRect.value = null
    return
  }
  const el = document.querySelector(selector)
  if (!el || !(el instanceof HTMLElement)) {
    targetRect.value = null
    return
  }
  el.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  targetRect.value = el.getBoundingClientRect()
  el.classList.add('tour-highlight-target')
  highlightedEl = el
}

function removeClickListener() {
  if (clickListener) {
    document.removeEventListener('click', clickListener, true)
    clickListener = null
  }
}

function removeBlockListener() {
  if (blockListener) {
    document.removeEventListener('pointerdown', blockListener, true)
    document.removeEventListener('mousedown', blockListener, true)
    document.removeEventListener('click', blockListener, true)
    blockListener = null
  }
}

function isAllowedInteraction(event: Event): boolean {
  const path = event.composedPath()
  if (tourCardRef.value && path.includes(tourCardRef.value)) return true

  if (current.value?.interactive && current.value.target) {
    const el = document.querySelector(current.value.target)
    if (el && path.some((n) => n === el || (n instanceof Node && el.contains(n)))) {
      return true
    }
  }
  return false
}

function setupBlockLayer() {
  removeBlockListener()
  if (!visible.value) return

  blockListener = (e: Event) => {
    if (!visible.value) return
    if (isAllowedInteraction(e)) return
    e.preventDefault()
    e.stopPropagation()
    e.stopImmediatePropagation()
  }

  document.addEventListener('pointerdown', blockListener, true)
  document.addEventListener('mousedown', blockListener, true)
  document.addEventListener('click', blockListener, true)
}

function setupInteractiveClick() {
  removeClickListener()
  if (!visible.value || !current.value?.interactive || !current.value.target) return

  clickListener = (e: MouseEvent) => {
    const el = document.querySelector(current.value!.target!)
    if (!el) return
    const rect = el.getBoundingClientRect()
    const x = e.clientX
    const y = e.clientY
    if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
      removeClickListener()
      setTimeout(() => advance(), 400)
    }
  }
  document.addEventListener('click', clickListener, true)
}

function setBodyTourActive(active: boolean) {
  document.body.classList.toggle('cw-tour-active', active)
}

async function prepareStep() {
  const step = current.value
  if (step?.route && router.currentRoute.value.path !== step.route) {
    await router.push(step.route)
  }
  await nextTick()
  setTimeout(() => {
    measureTarget()
    setupBlockLayer()
    setupInteractiveClick()
  }, 320)
}

function close() {
  removeClickListener()
  removeBlockListener()
  clearHighlight()
  setBodyTourActive(false)
  visible.value = false
}

function skip() {
  skipAllOnboarding()
  props.onComplete?.()
  close()
}

function finish() {
  props.onComplete?.()
  close()
}

function advance() {
  if (isLast.value) {
    finish()
    return
  }
  stepIndex.value += 1
  prepareStep()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      setBodyTourActive(true)
      stepIndex.value = 0
      prepareStep()
      resizeListener = () => measureTarget()
      window.addEventListener('resize', resizeListener)
      window.addEventListener('scroll', resizeListener, true)
    } else {
      removeClickListener()
      removeBlockListener()
      clearHighlight()
      setBodyTourActive(false)
      if (resizeListener) {
        window.removeEventListener('resize', resizeListener)
        window.removeEventListener('scroll', resizeListener, true)
        resizeListener = null
      }
    }
  },
)

onUnmounted(() => {
  removeClickListener()
  removeBlockListener()
  clearHighlight()
  setBodyTourActive(false)
  if (resizeListener) {
    window.removeEventListener('resize', resizeListener)
    window.removeEventListener('scroll', resizeListener, true)
  }
})

const illusMap: Record<TourIllustration, string> = {
  welcome: 'illus-welcome',
  import: 'illus-import',
  projects: 'illus-projects',
  workbench: 'illus-workbench',
  audit: 'illus-audit',
  fix: 'illus-fix',
  ai: 'illus-ai',
  help: 'illus-help',
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="tour-root" role="dialog" aria-modal="true" aria-label="新手引导">
      <template v-if="hasTarget && shadePanels">
        <div class="tour-shade" :style="shadePanels.top" />
        <div class="tour-shade" :style="shadePanels.left" />
        <div class="tour-shade" :style="shadePanels.right" />
        <div class="tour-shade" :style="shadePanels.bottom" />
        <div
          v-if="!current?.interactive"
          class="tour-spotlight-guard"
          :style="spotlightStyle"
          aria-hidden="true"
        />
        <div class="tour-spotlight-ring" :style="spotlightStyle" />
      </template>
      <div v-else class="tour-shade tour-shade--full" />

      <div
        ref="tourCardRef"
        class="tour-card glass-panel"
        :class="{ 'tour-card--center': !hasTarget }"
        :style="cardStyle"
      >
        <div v-if="current.illustration" class="tour-illus" :class="illusMap[current.illustration]" />
        <div class="tour-progress">
          <span
            v-for="(_, i) in steps"
            :key="i"
            class="tour-dot"
            :class="{ active: i === stepIndex, done: i < stepIndex }"
          />
        </div>
        <h3 class="tour-title">{{ current.title }}</h3>
        <p class="tour-body">{{ current.body }}</p>
        <p v-if="current.interactive && current.interactiveHint" class="tour-hint">
          👆 {{ current.interactiveHint }}
        </p>
        <p v-else-if="hasTarget" class="tour-hint tour-hint--muted">
          当前仅可操作本说明卡片；点「下一步」继续。
        </p>
        <div class="tour-actions">
          <el-button link type="info" @click="skip">跳过全部引导</el-button>
          <el-button type="primary" @click="advance">
            {{ isLast ? '完成' : '下一步' }}
          </el-button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style>
body.cw-tour-active {
  overflow: hidden;
}

body.cw-tour-active #app {
  pointer-events: none;
  user-select: none;
}

body.cw-tour-active .tour-highlight-target {
  pointer-events: auto !important;
  position: relative;
  z-index: 10000 !important;
  isolation: isolate;
  box-shadow:
    0 0 0 2px rgba(34, 211, 238, 0.95),
    0 0 0 6px rgba(34, 211, 238, 0.35),
    0 0 32px rgba(34, 211, 238, 0.45);
  border-radius: 12px;
}

body.cw-tour-active .tour-highlight-target * {
  pointer-events: auto !important;
}
</style>

<style scoped>
.tour-root {
  position: fixed;
  inset: 0;
  z-index: 9999;
  pointer-events: auto;
}

.tour-shade {
  position: fixed;
  background: rgba(2, 6, 23, 0.78);
  pointer-events: auto;
  backdrop-filter: grayscale(0.65) brightness(0.55);
  z-index: 1;
}

.tour-shade--full {
  inset: 0;
  z-index: 1;
}

.tour-spotlight-guard {
  position: fixed;
  z-index: 2;
  pointer-events: auto;
  cursor: not-allowed;
  border-radius: 12px;
  background: transparent;
}

.tour-spotlight-ring {
  position: fixed;
  border: 2px solid var(--cw-primary);
  border-radius: 12px;
  box-shadow: 0 0 0 4px var(--cw-primary-bg);
  pointer-events: none;
  z-index: 3;
}

.tour-card {
  position: fixed;
  width: min(360px, calc(100vw - 24px));
  padding: 18px 20px;
  pointer-events: auto;
  z-index: 10;
  border: 1px solid var(--cw-border) !important;
  background: var(--cw-bg-elevated) !important;
  box-shadow: var(--cw-shadow-lg);
  border-radius: var(--cw-radius);
}

.tour-card--center {
  top: 50% !important;
  left: 50% !important;
  transform: translate(-50%, -50%);
}

.tour-illus {
  height: 88px;
  border-radius: 10px;
  margin-bottom: 14px;
  border: 1px solid var(--cw-border);
  background: var(--cw-surface-subtle);
  position: relative;
  overflow: hidden;
}

.illus-welcome::before {
  content: 'CW';
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  color: var(--cw-primary);
}

.illus-import {
  background: linear-gradient(135deg, var(--cw-primary-bg), var(--cw-surface-subtle));
}
.illus-import::after {
  content: 'Git / ZIP → 导入';
  position: absolute;
  bottom: 10px;
  left: 12px;
  font-size: 12px;
  color: var(--cw-text-muted);
}

.illus-projects::after {
  content: '📁 项目卡片 · 点击进入';
  position: absolute;
  bottom: 10px;
  left: 12px;
  font-size: 12px;
}

.illus-workbench {
  background: linear-gradient(90deg, var(--cw-primary-bg) 28%, transparent 28%),
    var(--cw-surface-subtle);
}
.illus-workbench::after {
  content: '树 | 代码 | AI';
  position: absolute;
  bottom: 8px;
  right: 12px;
  font-size: 11px;
  color: var(--cw-text-muted);
}

.illus-audit::after {
  content: '🔍 扫描 → 问题列表';
  position: absolute;
  bottom: 10px;
  left: 12px;
  font-size: 12px;
}

.illus-fix::after {
  content: '✨ 建议 → 一键应用 → Diff';
  position: absolute;
  bottom: 10px;
  left: 12px;
  font-size: 12px;
}

.illus-ai::after {
  content: '💬 读文档 → 五段分析';
  position: absolute;
  bottom: 10px;
  left: 12px;
  font-size: 12px;
}

.illus-help::after {
  content: '📖 帮助文档';
  position: absolute;
  bottom: 10px;
  left: 12px;
  font-size: 12px;
}

.tour-progress {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
}

.tour-dot {
  flex: 1;
  height: 3px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.25);
}

.tour-dot.active {
  background: var(--cw-primary);
}

.tour-dot.done {
  background: var(--cw-primary-border);
}

.tour-title {
  margin: 0 0 8px;
  font-size: 17px;
}

.tour-body {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--cw-text);
}

.tour-hint {
  margin: 10px 0 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--cw-primary-bg);
  border: 1px solid var(--cw-primary-border);
  font-size: 12px;
  color: var(--cw-primary);
}

.tour-hint--muted {
  background: rgba(148, 163, 184, 0.12);
  color: var(--cw-text-muted);
}

.tour-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}
</style>
