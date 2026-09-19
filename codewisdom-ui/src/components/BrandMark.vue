<script setup lang="ts">
withDefaults(
  defineProps<{
    size?: number
  }>(),
  { size: 36 },
)
</script>

<template>
  <img src="/logo.svg" alt="CodeWisdom" class="brand-mark" :width="size" :height="size" />
</template>

<style scoped>
.brand-mark {
  display: block;
  flex-shrink: 0;
  border-radius: 8px;
}
</style>
