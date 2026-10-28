<script setup lang="ts">
import type { UploadedFile } from '../types/domain';

import { computed, ref, watch, onBeforeUnmount, nextTick } from 'vue';
import { MoreHorizontal, Share2, Trash2, X, FileText, Copy } from 'lucide-vue-next';
import {
  IS_DEMO_MODE,
  post,
  request,
  readChatFile,
  type Message,
  type User,
  type Conversation,
} from '../services/http';
import { redactIdentifiers } from '../lib/presentation';
const props = defineProps<{
  messages: Message[];
  user: User;
  conversationId: string;
  conversations: Conversation[];
}>();
const emit = defineEmits<{
  download: [message: Message];
  hide: [ids: string[]];
  notify: [message: string];
  sent: [target: string, message: Message];
}>();
const isSelecting = ref(false),
  picked = ref<string[]>([]),
  modal = ref(''),
  target = ref(''),
  isBusy = ref(false),
  error = ref(''),
  completed = ref(0);
const dialog = ref<HTMLElement | null>(null),
  forwardQueue = ref<Message[]>([]);
let previousFocus: HTMLElement | null = null,
  previousOverflow = '';
watch(modal, async (value, before) => {
  if (value && !before) {
    previousFocus = document.activeElement as HTMLElement;
    previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    await nextTick();
    dialog.value
      ?.querySelector<HTMLElement>('button:not(:disabled), input:not(:disabled)')
      ?.focus();
  } else if (!value && before) {
    document.body.style.overflow = previousOverflow;
    await nextTick();
    if (previousFocus?.isConnected) previousFocus.focus();
    else document.querySelector<HTMLElement>('.message-selection-bar button')?.focus();
  }
});
function trapFocus(event: KeyboardEvent) {
  if (event.key !== 'Tab') return;
  const nodes = Array.from(
    dialog.value?.querySelectorAll<HTMLElement>(
      'button:not(:disabled), input:not(:disabled), select:not(:disabled), a[href]',
    ) || [],
  );
  const first = nodes[0],
    last = nodes[nodes.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last?.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first?.focus();
  }
}
const selectedMessages = computed(() =>
  props.messages.filter((m) => picked.value.includes(m.public_id)),
);
const targets = computed(() =>
  props.conversations.filter((c) => c.public_id !== props.conversationId),
);
let timer: ReturnType<typeof setTimeout> | undefined,
  start: { x: number; y: number } | null = null,
  longPressed = false;
let attachments = new Map<string, string>(),
  keys = new Map<string, string>();
let generation = 0;
watch(
  () => props.conversationId,
  () => {
    generation++;
    cancel();
    modal.value = '';
    clearTimeout(timer);
  },
);
onBeforeUnmount(() => {
  generation++;
  clearTimeout(timer);
  if (modal.value) document.body.style.overflow = previousOverflow;
});
function cancel() {
  isSelecting.value = false;
  picked.value = [];
  error.value = '';
}
function select(m: Message) {
  if (!picked.value.includes(m.public_id) && picked.value.length >= 20) {
    emit('notify', '一次最多选择 20 条消息');
    return;
  }
  isSelecting.value = true;
  if (!picked.value.includes(m.public_id)) picked.value.push(m.public_id);
}
function down(event: PointerEvent, m: Message) {
  if (
    event.button !== 0 ||
    !event.isPrimary ||
    (event.target as HTMLElement).closest('input, audio, .message-more')
  )
    return;
  clearTimeout(timer);
  start = { x: event.clientX, y: event.clientY };
  longPressed = false;
  timer = setTimeout(() => {
    select(m);
    longPressed = true;
  }, 500);
}
function move(event: PointerEvent) {
  if (start && Math.hypot(event.clientX - start.x, event.clientY - start.y) > 12)
    clearTimeout(timer);
}
function cancelPress() {
  clearTimeout(timer);
}
function capture(event: MouseEvent, m: Message) {
  if (!isSelecting.value) return;
  if ((event.target as HTMLElement).closest('.message-choice')) return;
  event.preventDefault();
  event.stopPropagation();
  if (longPressed) {
    longPressed = false;
    return;
  }
  toggle(m.public_id);
}
function toggle(id: string) {
  if (picked.value.includes(id)) picked.value = picked.value.filter((x) => x !== id);
  else if (picked.value.length < 20) picked.value.push(id);
  else emit('notify', '一次最多选择 20 条消息');
}
function changeChoice(event: Event, id: string) {
  toggle(id);
  (event.target as HTMLInputElement).checked = picked.value.includes(id);
}
function openForward() {
  target.value = '';
  error.value = '';
  completed.value = 0;
  attachments.clear();
  keys.clear();
  forwardQueue.value = selectedMessages.value.map((message) => ({ ...message }));
  modal.value = 'forward';
}
async function copy() {
  try {
    await navigator.clipboard.writeText(
      selectedMessages.value
        .map((m) => m.content || `[文件] ${m.file?.original_filename || '附件'}`)
        .join('\n'),
    );
    emit('notify', '已复制所选消息');
  } catch {
    error.value = '无法访问剪贴板，请检查浏览器权限。';
  }
}
async function forward() {
  if (isBusy.value || !target.value || !selectedMessages.value.length) return;
  const queue = [...forwardQueue.value],
    destination = target.value,
    epoch = generation;
  if (queue.some((m) => !m.content && !m.file)) {
    error.value = '所选消息包含暂不支持转发的内容，请取消选择后重试。';
    return;
  }
  isBusy.value = true;
  error.value = '';
  try {
    for (let i = completed.value; i < queue.length; i++) {
      const m = queue[i];
      const cacheKey = `${destination}:${m.public_id}`;
      if (!keys.has(cacheKey)) keys.set(cacheKey, crypto.randomUUID());
      let body: {
        content?: string;
        media_type?: string;
        file_public_id?: string;
        temp_id?: string;
        media_url?: string;
      } = {
        media_type: 'TEXT',
        content: `转发自 ${m.sender.nickname}：\n${m.content}`,
        temp_id: keys.get(cacheKey),
      };
      if (m.file) {
        if (!m.file.public_id) throw new Error('该附件暂无法转发，请重新选择。');
        if (!IS_DEMO_MODE && !attachments.has(cacheKey)) {
          const blob = await readChatFile(m.file.public_id);
          if (epoch !== generation) return;
          const form = new FormData();
          form.append('file', blob, m.file.original_filename);
          form.append('conversation_public_id', destination);
          const result = await request<UploadedFile>('/files', { method: 'POST', body: form });
          attachments.set(cacheKey, result.public_id);
        }
        body = {
          media_type: 'FILE',
          file_public_id: attachments.get(cacheKey),
          temp_id: keys.get(cacheKey),
        };
      }
      if (epoch !== generation) return;
      const result = IS_DEMO_MODE
        ? {
            ...m,
            content: m.file ? m.content : body.content,
            public_id: 'forward-' + crypto.randomUUID(),
            sender: props.user,
            created_at: new Date().toISOString(),
          }
        : await post<Message>(`/conversations/${encodeURIComponent(destination)}/messages`, body);
      if (epoch !== generation) return;
      emit('sent', destination, {
        ...result,
        content: result.content || '',
        sender: result.sender || props.user,
      });
      completed.value = i + 1;
    }
    modal.value = '';
    cancel();
    emit('notify', `已转发 ${queue.length} 条消息`);
  } catch (e) {
    error.value = `已发送 ${completed.value}/${queue.length} 条。${(e as Error).message}，可继续重试剩余消息。`;
  } finally {
    isBusy.value = false;
  }
}
const time = (value?: string) =>
  value ? new Date(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : '';
</script>
<template>
  <div v-if="isSelecting" class="message-selection-bar" role="toolbar" aria-label="消息多选操作">
    <strong>已选 {{ picked.length }} 条</strong>
    <button :disabled="!picked.length" @click="openForward"><Share2 :size="15" />转发</button>
    <button :disabled="!picked.length" @click="copy"><Copy :size="15" />复制</button>
    <button
      :disabled="!picked.length"
      class="danger"
      @click="
        modal = 'delete';
        error = '';
      "
    >
      <Trash2 :size="15" />删除
    </button>
    <button @click="cancel">取消</button>
  </div>
  <p v-if="error && !modal" class="inline-error" role="alert">{{ redactIdentifiers(error) }}</p>
  <div
    v-for="m in messages"
    :key="m.public_id"
    :class="[
      'message',
      'message-interactive',
      {
        mine: m.sender.public_id === user.public_id,
        'message-picked': picked.includes(m.public_id),
      },
    ]"
    @pointerdown="down($event, m)"
    @pointermove="move"
    @pointerup="cancelPress"
    @pointercancel="cancelPress"
    @pointerleave="cancelPress"
    @contextmenu.prevent="select(m)"
    @click.capture="capture($event, m)"
  >
    <input
      v-if="isSelecting"
      type="checkbox"
      class="message-choice"
      :checked="picked.includes(m.public_id)"
      :aria-label="'选择消息：' + (m.content || m.file?.original_filename || '附件')"
      @change="changeChoice($event, m.public_id)"
      @pointerdown.stop
    />
    <span class="avatar small peach">{{ m.sender.nickname.slice(0, 1) }}</span>
    <div class="message-body">
      <small>{{ m.sender.nickname }} · {{ time(m.created_at) }}</small>
      <p v-if="m.content">{{ m.content }}</p>
      <button v-if="m.file" class="file-message" @click="emit('download', m)">
        <FileText :size="18" /><span
          >{{ m.file.original_filename
          }}<small
            >{{ m.file.size_bytes ? Math.ceil(m.file.size_bytes / 1024) + ' KB' : '文件附件' }} ·
            点击下载</small
          ></span
        >
      </button>
      <audio
        v-if="m.media_type === 'VOICE' && /^https?:\/\//.test(m.media_url || '')"
        :src="m.media_url"
        controls
      />
    </div>
    <button
      v-if="!isSelecting"
      class="message-more icon-button"
      :aria-label="'消息操作：' + (m.content || m.file?.original_filename || '附件')"
      @pointerdown.stop
      @click.stop="select(m)"
    >
      <MoreHorizontal :size="15" />
    </button>
  </div>
  <div v-if="!messages.length" class="empty">发送第一条消息吧。</div>
  <Teleport to="body"
    ><div
      v-if="modal"
      class="modal-overlay"
      @click.self="!isBusy && (modal = '')"
      @keydown.esc.stop="!isBusy && (modal = '')"
      @keydown="trapFocus"
    >
      <section
        ref="dialog"
        class="modal card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="message-action-title"
      >
        <button
          class="icon-button close"
          aria-label="关闭消息操作"
          :disabled="isBusy"
          @click="modal = ''"
        >
          <X :size="19" />
        </button>
        <template v-if="modal === 'delete'">
          <h2 id="message-action-title">删除 {{ picked.length }} 条消息？</h2>
          <p>仅从当前账号在此浏览器的聊天记录中隐藏，不会撤回或删除其他人的消息。</p>
          <div class="form-actions">
            <button class="secondary" @click="modal = ''">取消</button
            ><button
              class="primary"
              @click="
                emit('hide', [...picked]);
                modal = '';
                cancel();
              "
            >
              确认删除消息
            </button>
          </div>
        </template>
        <template v-else>
          <h2 id="message-action-title">转发 {{ picked.length }} 条消息</h2>
          <p>选择接收会话，确认后发送。附件会重新上传至接收会话。</p>
          <div class="forward-targets">
            <label v-for="c in targets" :key="c.public_id" class="checkbox-label"
              ><input
                v-model="target"
                type="radio"
                name="forward-target"
                :value="c.public_id"
                :disabled="isBusy || completed > 0"
              />{{ c.name }}</label
            >
          </div>
          <p v-if="!targets.length" class="muted">没有其他会话，请先从好友或群聊页面发起聊天。</p>
          <p v-if="error" class="inline-error" role="alert">{{ redactIdentifiers(error) }}</p>
          <button class="primary" :disabled="isBusy || !target" @click="forward">
            {{
              isBusy
                ? `发送中 ${completed}/${picked.length}`
                : completed
                  ? '重试剩余消息'
                  : '确认转发'
            }}
          </button>
        </template>
      </section>
    </div></Teleport
  >
</template>
