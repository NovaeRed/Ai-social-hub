<script setup lang="ts">
import { computed, ref, onBeforeUnmount } from 'vue';
import { Sparkles, X, RotateCcw, Copy } from 'lucide-vue-next';
import { demo, stream, type Message } from '../lib/api';
import { redactIdentifiers } from '../lib/presentation';
type Tool = 'polish' | 'smart-reply' | 'summarize';
const props = defineProps<{
  conversationId: string;
  draft: string;
  messages: Message[];
  models: any[];
  userId: string;
}>();
const emit = defineEmits<{ 'update:draft': [text: string]; busy: [value: boolean] }>();
const tool = ref<Tool>('polish'),
  visible = ref(false),
  busy = ref(false),
  output = ref(''),
  error = ref(''),
  model = ref(''),
  original = ref(''),
  applied = ref(false),
  copied = ref(false);
const labels: Record<Tool, string> = {
  polish: 'AI 润色',
  'smart-reply': '回复建议',
  summarize: '对话总结',
};
const capabilities: Record<Tool, string> = {
  polish: 'POLISH',
  'smart-reply': 'SMART_REPLY',
  summarize: 'CHAT_SUMMARY',
};
const available = computed(() =>
  props.models.filter(
    (m) =>
      !m.capabilities?.length ||
      m.capabilities.includes(capabilities[tool.value]) ||
      m.capabilities.includes('text'),
  ),
);
let controller: AbortController | undefined,
  generation = 0,
  disposed = false;
function cancel() {
  generation++;
  controller?.abort();
  busy.value = false;
  emit('busy', false);
}
function close() {
  cancel();
  visible.value = false;
}
async function run(next: Tool) {
  if (busy.value) return;
  tool.value = next;
  visible.value = true;
  output.value = '';
  error.value = '';
  applied.value = false;
  copied.value = false;
  original.value = props.draft;
  if (next === 'polish' && !props.draft.trim()) {
    error.value = '先写下想说的话，再点润色。';
    return;
  }
  if (next !== 'polish' && !props.messages.length) {
    error.value = '当前还没有可供参考的聊天内容。';
    return;
  }
  const preferred = import.meta.env.VITE_DEFAULT_MODEL;
  if (!available.value.some((m) => m.option_code === model.value))
    model.value =
      available.value.find((m) => m.option_code === preferred)?.option_code ||
      available.value[0]?.option_code ||
      '';
  if (!demo && !model.value) {
    error.value = 'AI 暂时不可用，请稍后再试。你的草稿已保留。';
    return;
  }
  const id = ++generation;
  controller = new AbortController();
  busy.value = true;
  emit('busy', true);
  try {
    if (demo)
      output.value =
        next === 'polish'
          ? '【演示润色】很期待和你交流这个想法，我们找个时间好好聊聊吧。'
          : next === 'smart-reply'
            ? '【演示回复】听起来很棒！有什么需要提前准备的吗？'
            : '【演示总结】大家分享了近期想法，并约定下一次交流的时间。';
    else {
      const latest =
        [...props.messages]
          .reverse()
          .find((m) => m.content && m.sender.public_id !== props.userId) ||
        props.messages[props.messages.length - 1];
      const body =
        next === 'polish'
          ? { message: props.draft.trim() }
          : next === 'smart-reply'
            ? { message: latest.content || '', conversation_public_id: props.conversationId }
            : {
                conversation_public_id: props.conversationId,
                summary_type: 'chat',
                target_length: 'short',
              };
      await stream(
        '/ai/interactions/' + next,
        { ...body, model_option_code: model.value },
        (chunk) => {
          if (!disposed && generation === id) output.value += chunk;
        },
        controller.signal,
      );
    }
    if (disposed || generation !== id) return;
    if (!output.value.trim()) throw new Error('没有生成可用内容，请重试。');
    if (next === 'polish') {
      emit('update:draft', output.value);
      applied.value = true;
    }
  } catch (e) {
    if (!disposed && generation === id && (e as Error).name !== 'AbortError') {
      error.value =
        next === 'polish'
          ? '暂时无法润色，原文已保留，请稍后重试。'
          : next === 'smart-reply'
            ? '暂时无法生成回复建议，草稿已保留，请稍后重试。'
            : '暂时无法生成总结，请稍后重试。';
      output.value = '';
    }
  } finally {
    if (!disposed && generation === id) {
      busy.value = false;
      emit('busy', false);
    }
  }
}
function undo() {
  emit('update:draft', original.value);
  visible.value = false;
}
function applyReply() {
  emit('update:draft', output.value);
  visible.value = false;
}
async function copy() {
  try {
    await navigator.clipboard.writeText(output.value);
    copied.value = true;
  } catch {
    error.value = '复制失败，请选择上方内容手动复制。';
  }
}
defineExpose({ run, cancel });
onBeforeUnmount(() => {
  disposed = true;
  generation++;
  controller?.abort();
  emit('busy', false);
});
</script>
<template>
  <section v-if="visible" class="chat-ai-tray" aria-label="聊天内 AI 助手" :aria-busy="busy">
    <div class="chat-ai-heading">
      <strong><Sparkles :size="15" />{{ labels[tool] }}<small v-if="demo">演示</small></strong
      ><button class="icon-button" aria-label="关闭聊天助手" @click="close">
        <X :size="16" />
      </button>
    </div>
    <p v-if="busy" class="chat-ai-progress" role="status">
      {{ tool === 'polish' ? '正在润色，完成后填入编辑框…' : '正在整理…'
      }}<button type="button" class="text-button" @click="cancel">停止</button>
    </p>
    <p v-if="error" class="chat-ai-error" role="alert">{{ redactIdentifiers(error) }}</p>
    <p v-if="output && (!applied || tool !== 'polish')" class="chat-ai-output" aria-live="polite">
      {{ output }}
    </p>
    <div v-if="applied" class="chat-ai-applied" role="status">
      <span>已填入编辑框，确认后再发送。</span
      ><button type="button" class="text-button" @click="undo">
        <RotateCcw :size="13" />撤销润色
      </button>
    </div>
    <div v-if="!busy" class="chat-ai-actions">
      <button v-if="error || output" type="button" class="text-button" @click="run(tool)">
        重新生成</button
      ><button
        v-if="output && tool === 'smart-reply'"
        type="button"
        class="secondary"
        @click="applyReply"
      >
        使用回复</button
      ><button
        v-if="output && tool === 'summarize'"
        type="button"
        class="text-button"
        @click="copy"
      >
        <Copy :size="13" />{{ copied ? '已复制' : '复制总结' }}
      </button>
    </div>
  </section>
</template>
