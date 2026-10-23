<script setup lang="ts">
import { ref } from 'vue';
import { MoreHorizontal, Pin, Trash2 } from 'lucide-vue-next';
import type { Conversation } from '../lib/api';
defineProps<{ conversation: Conversation; selected: boolean; pinned: boolean; index: number }>();
const emit = defineEmits<{ open: []; pin: []; remove: [] }>();
const expanded = ref(false);
let start: { x: number; y: number } | null = null,
  suppress = false;
function down(event: PointerEvent) {
  if (event.button !== 0 || !event.isPrimary) return;
  start = { x: event.clientX, y: event.clientY };
  suppress = false;
  (event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);
}
function move(event: PointerEvent) {
  if (!start) return;
  const x = event.clientX - start.x,
    y = event.clientY - start.y;
  if (Math.abs(y) > 25 && Math.abs(y) > Math.abs(x)) {
    start = null;
    return;
  }
  if (Math.abs(x) > 45 && Math.abs(x) > Math.abs(y) * 1.5) {
    expanded.value = x < 0;
    suppress = true;
    start = null;
  }
}
function open() {
  if (suppress) {
    suppress = false;
    return;
  }
  expanded.value = false;
  emit('open');
}
</script>
<template>
  <div class="conversation-item" @keydown.esc.stop="expanded = false">
    <button
      :class="['conversation-row', { selected, unread: conversation.unread_count > 0, pinned }]"
      @pointerdown="down"
      @pointermove="move"
      @pointerup="start = null"
      @pointercancel="start = null"
      @contextmenu.prevent="expanded = !expanded"
      @click="open"
    >
      <span :class="['avatar', ['peach', 'lavender', 'sage'][index % 3]]">{{
        conversation.name.slice(-2)
      }}</span>
      <span class="conversation-info"
        ><strong><Pin v-if="pinned" :size="11" aria-label="已置顶" />{{ conversation.name }}</strong
        ><small>{{ conversation.latest_message?.content || '暂无消息' }}</small></span
      >
      <span
        v-if="conversation.unread_count > 0"
        class="count conversation-unread"
        :aria-label="conversation.unread_count + '条未读'"
        >{{ conversation.unread_count > 99 ? '99+' : conversation.unread_count }}</span
      >
    </button>
    <button
      class="conversation-more icon-button"
      :aria-label="'会话操作：' + conversation.name"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      <MoreHorizontal :size="15" />
    </button>
    <div v-if="expanded" class="conversation-row-actions">
      <button
        @click="
          emit('pin');
          expanded = false;
        "
      >
        <Pin :size="14" />{{ pinned ? '取消置顶' : '置顶聊天' }}
      </button>
      <button
        class="danger"
        @click="
          emit('remove');
          expanded = false;
        "
      >
        <Trash2 :size="14" />移除会话
      </button>
    </div>
  </div>
</template>
