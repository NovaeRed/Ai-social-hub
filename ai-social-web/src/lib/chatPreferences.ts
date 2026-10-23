import { ref, watch, type Ref } from 'vue';
import type { User } from './api';

// Until server-side conversation preferences and per-message hiding exist,
// these explicitly local choices are isolated by account and browser.
export function useChatPreferences(user: Ref<User>) {
  const pinned = ref<string[]>([]),
    removed = ref<Record<string, string>>({}),
    hidden = ref<Record<string, string[]>>({});
  watch(
    () => user.value.public_id,
    (id) => {
      let saved: any = {};
      try {
        saved = JSON.parse(localStorage.getItem('chat-preferences:' + id) || '{}');
      } catch {}
      pinned.value = Array.isArray(saved.pinned)
        ? saved.pinned.filter((id: unknown) => typeof id === 'string')
        : [];
      removed.value =
        saved.removed && typeof saved.removed === 'object' && !Array.isArray(saved.removed)
          ? saved.removed
          : {};
      hidden.value =
        saved.hidden && typeof saved.hidden === 'object' && !Array.isArray(saved.hidden)
          ? saved.hidden
          : {};
    },
    { immediate: true },
  );
  function save() {
    if (!user.value.public_id) return;
    try {
      localStorage.setItem(
        'chat-preferences:' + user.value.public_id,
        JSON.stringify({ pinned: pinned.value, removed: removed.value, hidden: hidden.value }),
      );
    } catch {
      return false;
    }
    return true;
  }
  return { pinned, removed, hidden, save };
}
