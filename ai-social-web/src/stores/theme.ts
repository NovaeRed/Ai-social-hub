import { computed, onScopeDispose, ref, watch } from 'vue';
import { defineStore } from 'pinia';

export type ThemePreference = 'system' | 'light' | 'dark';
const THEME_STORAGE_KEY = 'gongming-theme';

function readPreference(): ThemePreference {
  try {
    const value = localStorage.getItem(THEME_STORAGE_KEY);
    if (value === 'light' || value === 'dark') return value;
  } catch {
    // 浏览器禁止本地存储时仍可使用本次会话的主题。
  }
  return 'system';
}

export const useThemeStore = defineStore('theme', () => {
  const preference = ref<ThemePreference>(readPreference());
  const media = window.matchMedia('(prefers-color-scheme: dark)');
  const isSystemDark = ref(media.matches);
  const isDark = computed(
    () => preference.value === 'dark' || (preference.value === 'system' && isSystemDark.value),
  );
  const toggleLabel = computed(() => (isDark.value ? '切换为浅色模式' : '切换为深色模式'));

  function setPreference(value: ThemePreference) {
    preference.value = value;
    try {
      localStorage.setItem(THEME_STORAGE_KEY, value);
    } catch {
      // 主题立即生效，存储失败只影响下次打开时的记忆。
    }
  }
  function toggleTheme() {
    setPreference(isDark.value ? 'light' : 'dark');
  }
  function systemChanged(event: MediaQueryListEvent) {
    isSystemDark.value = event.matches;
  }
  function storageChanged(event: StorageEvent) {
    if (event.key === THEME_STORAGE_KEY || event.key === null) preference.value = readPreference();
  }
  watch(
    isDark,
    (value) => {
      document.documentElement.dataset.theme = value ? 'dark' : 'light';
      document.documentElement.style.colorScheme = value ? 'dark' : 'light';
      document
        .querySelector('meta[name="theme-color"]')
        ?.setAttribute('content', value ? '#111827' : '#f7f8fa');
    },
    { immediate: true },
  );
  media.addEventListener('change', systemChanged);
  window.addEventListener('storage', storageChanged);
  onScopeDispose(() => {
    media.removeEventListener('change', systemChanged);
    window.removeEventListener('storage', storageChanged);
  });
  return { preference, isDark, toggleLabel, setPreference, toggleTheme };
});
