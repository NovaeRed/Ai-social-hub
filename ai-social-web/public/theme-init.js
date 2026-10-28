(() => {
  let preference = 'system';
  try {
    preference = globalThis.localStorage.getItem('gongming-theme') || 'system';
  } catch {
    /* 无存储权限时跟随系统。 */
  }
  const isDark =
    preference === 'dark' ||
    (preference !== 'light' && globalThis.matchMedia('(prefers-color-scheme: dark)').matches);
  globalThis.document.documentElement.dataset.theme = isDark ? 'dark' : 'light';
  globalThis.document.documentElement.style.colorScheme = isDark ? 'dark' : 'light';
})();
