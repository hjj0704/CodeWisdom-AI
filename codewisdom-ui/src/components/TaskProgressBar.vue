<script setup lang="ts">
defineProps<{
  visible: boolean
  title: string
  percent: number
  stage: string
}>()
</script>

<template>
  <Transition name="task-progress-fade">
    <div v-if="visible" class="task-progress glass-panel" role="status" aria-live="polite">
      <div class="task-progress__head">
        <span class="task-progress__title">{{ title }}</span>
        <span class="task-progress__pct">{{ Math.min(100, Math.round(percent)) }}%</span>
      </div>
      <el-progress :percentage="Math.min(100, Math.round(percent))" :show-text="false" :stroke-width="5" />
      <p class="task-progress__stage">{{ stage }}</p>
    </div>
  </Transition>
</template>

<style scoped>
.task-progress {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 1200;
  width: min(360px, calc(100vw - 40px));
  padding: 14px 16px;
  box-shadow: var(--cw-shadow-lg);
}

.task-progress__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.task-progress__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--cw-text);
}

.task-progress__pct {
  font-size: 12px;
  font-weight: 600;
  color: var(--cw-primary);
  font-variant-numeric: tabular-nums;
}

.task-progress__stage {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--cw-text-muted);
  line-height: 1.5;
}

.task-progress-fade-enter-active,
.task-progress-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.task-progress-fade-enter-from,
.task-progress-fade-leave-to {
  opacity: 0;
  transform: translateY(12px);
}
</style>
