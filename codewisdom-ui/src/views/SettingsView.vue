<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Moon, Sunny } from '@element-plus/icons-vue'
import { useSettingsStore } from '../stores/settings'
import { useThemeStore } from '../stores/theme'

const settingsStore = useSettingsStore()
const themeStore = useThemeStore()

const settings = computed(() => settingsStore.settings)

function onPatch<K extends keyof typeof settingsStore.settings>(
  key: K,
  value: (typeof settingsStore.settings)[K],
) {
  settingsStore.patch({ [key]: value })
}

function onResetSettings() {
  settingsStore.reset()
  ElMessage.success('已恢复默认设置')
}
</script>

<template>
  <div class="settings-page">
    <header class="settings-hero glass-panel">
      <span class="section-label">偏好</span>
      <h1 class="gradient-text">设置</h1>
      <p class="settings-hero__desc">外观、工作台默认行为与编辑器显示。设置保存在本机浏览器。</p>
    </header>

    <div class="settings-layout glass-panel">
      <el-tabs tab-position="left" class="settings-tabs">
        <el-tab-pane label="外观" name="appearance">
          <h2>外观与动效</h2>
          <div class="settings-row">
            <span>主题模式</span>
            <el-button :icon="themeStore.isDark ? Sunny : Moon" @click="themeStore.toggle()">
              {{ themeStore.isDark ? '切换浅色' : '切换深色' }}
            </el-button>
          </div>
          <div class="settings-row">
            <span>减少背景动效</span>
            <el-switch
              :model-value="settings.reduceMotion"
              @change="(v: boolean) => onPatch('reduceMotion', v)"
            />
          </div>
          <p class="settings-hint">开启后关闭粒子、字母飘落等动画，减轻视觉干扰。</p>
        </el-tab-pane>

        <el-tab-pane label="工作台" name="workbench">
          <h2>工作台默认</h2>
          <div class="settings-row">
            <span>默认隐藏 pom 依赖提示</span>
            <el-switch
              :model-value="settings.workbenchHideDependency"
              @change="(v: boolean) => onPatch('workbenchHideDependency', v)"
            />
          </div>
          <div class="settings-row">
            <span>默认隐藏空 catch</span>
            <el-switch
              :model-value="settings.workbenchHideCatch"
              @change="(v: boolean) => onPatch('workbenchHideCatch', v)"
            />
          </div>
          <div class="settings-row">
            <span>紧凑面板（收起推荐流程）</span>
            <el-switch
              :model-value="settings.workbenchCompactPanels"
              @change="(v: boolean) => onPatch('workbenchCompactPanels', v)"
            />
          </div>
          <p class="settings-hint">文档注释请在项目工作台打开 Java 文件后，使用「一键补充注释」。</p>
        </el-tab-pane>

        <el-tab-pane label="编辑器" name="editor">
          <h2>代码编辑器</h2>
          <div class="settings-row">
            <span>字号 {{ settings.editorFontSize }}px</span>
            <el-slider
              :model-value="settings.editorFontSize"
              :min="12"
              :max="20"
              :step="1"
              style="max-width: 240px"
              @update:model-value="(v: number) => onPatch('editorFontSize', v)"
            />
          </div>
          <div class="settings-row">
            <span>显示 Minimap</span>
            <el-switch
              :model-value="settings.editorMinimap"
              @change="(v: boolean) => onPatch('editorMinimap', v)"
            />
          </div>
          <pre class="editor-preview" :style="{ fontSize: `${settings.editorFontSize}px` }">// 预览字号
public void preview() {}</pre>
        </el-tab-pane>
      </el-tabs>

      <div class="settings-footer">
        <el-button @click="onResetSettings">恢复全部默认</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 960px;
  margin: 0 auto;
  padding-bottom: 32px;
}

.settings-hero {
  padding: 24px 28px;
}

.settings-hero__desc {
  margin: 8px 0 0;
  color: var(--cw-text-muted);
  font-size: 14px;
  line-height: 1.6;
}

.settings-layout {
  padding: 8px 16px 20px;
}

.settings-tabs :deep(.el-tabs__content) {
  padding: 8px 16px 0;
}

.settings-tabs h2 {
  margin: 0 0 16px;
  font-size: 16px;
}

.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid var(--cw-border);
  font-size: 14px;
}

.settings-hint {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--cw-text-muted);
  line-height: 1.55;
}

.editor-preview {
  margin-top: 16px;
  padding: 12px;
  border-radius: var(--cw-radius-sm);
  background: var(--cw-surface-code);
  border: 1px solid var(--cw-border);
  font-family: var(--cw-mono);
}

.settings-footer {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--cw-border);
}
</style>
