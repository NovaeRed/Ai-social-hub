<script setup lang="ts">
import { Sun, Moon, Monitor } from 'lucide-vue-next';
import { useThemeStore, type ThemePreference } from '../stores/theme';

const theme = useThemeStore();
const THEME_OPTIONS: { value: ThemePreference; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: '浅色', icon: Sun },
  { value: 'dark', label: '深色', icon: Moon },
  { value: 'system', label: '跟随系统', icon: Monitor },
];
</script>
<template>
  <section class="card theme-preferences">
    <h3>外观</h3>
    <p class="muted">选择界面主题，设置将保存在当前浏览器。</p>
    <div class="theme-options" role="group" aria-label="主题模式">
      <button
        v-for="option in THEME_OPTIONS"
        :key="option.value"
        class="secondary"
        :aria-pressed="theme.preference === option.value"
        @click="theme.setPreference(option.value)"
      >
        <component :is="option.icon" :size="18" />{{ option.label }}
      </button>
    </div>
  </section>
</template>
<style scoped>
.theme-preferences {
  padding: 24px;
  margin-bottom: 24px;
}
.theme-preferences p {
  margin: 8px 0 16px;
}
.theme-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.theme-options button[aria-pressed='true'] {
  border-color: var(--accent);
  color: var(--accent);
}
</style>
