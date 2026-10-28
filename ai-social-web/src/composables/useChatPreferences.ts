import { ref, watch, type Ref } from 'vue';
import type { User } from '../services/http';

// 在服务端支持会话偏好与逐条消息隐藏之前，
// 这些本机设置按账号和浏览器分别保存。
export function useChatPreferences(user: Ref<User>) {
  const pinned = ref<string[]>([]),
    removed = ref<Record<string, string>>({}),
    hidden = ref<Record<string, string[]>>({});
  watch(
    () => user.value.public_id,
    (id) => {
      let saved: Record<string, unknown> = {};
      try {
        saved = JSON.parse(localStorage.getItem('chat-preferences:' + id) || '{}');
      } catch {
        /* 本机存储损坏时使用空偏好。 */
      }
      if (!saved || typeof saved !== 'object' || Array.isArray(saved)) saved = {};
      pinned.value = Array.isArray(saved.pinned)
        ? saved.pinned.filter((id: unknown) => typeof id === 'string')
        : [];
      removed.value =
        saved.removed && typeof saved.removed === 'object' && !Array.isArray(saved.removed)
          ? Object.fromEntries(
              Object.entries(saved.removed).filter(
                (entry): entry is [string, string] => !!entry[0] && typeof entry[1] === 'string',
              ),
            )
          : {};
      hidden.value =
        saved.hidden && typeof saved.hidden === 'object' && !Array.isArray(saved.hidden)
          ? Object.fromEntries(
              Object.entries(saved.hidden)
                .filter(([key, value]) => key && Array.isArray(value))
                .map(([key, value]) => [
                  key,
                  (value as unknown[]).filter((item): item is string => typeof item === 'string'),
                ]),
            )
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
