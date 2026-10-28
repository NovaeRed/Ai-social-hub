<script setup lang="ts">
import type { ExtractedSchedule } from '../types/domain';
import { redactIdentifiers } from '../lib/presentation';
import { ref } from 'vue';
import { CalendarPlus, Sparkles, Clock3, Check } from 'lucide-vue-next';
import { IS_DEMO_MODE, post } from '../services/http';
import DateTimeField from './DateTimeField.vue';
const props = defineProps<{ model: string }>(),
  emit = defineEmits<{ saved: []; notify: [message: string] }>();
const text = ref(''),
  isBusy = ref(false),
  error = ref(''),
  items = ref<ExtractedSchedule[]>([]);
async function extract() {
  isBusy.value = true;
  error.value = '';
  items.value = [];
  try {
    if (!props.model && !IS_DEMO_MODE) throw new Error('请先选择可用的 AI 模型');
    const result = IS_DEMO_MODE
      ? { schedules: [{ title: '产品方案讨论', time: '明天下午两点', location: '线上' }] }
      : await post<{ schedules: Partial<ExtractedSchedule>[] }>('/ai/interactions/schedule', {
          messages: [{ sender: '我', content: text.value, timestamp: new Date().toISOString() }],
          model_option_code: props.model,
          context: { timezone: 'Asia/Shanghai' },
        });
    items.value = (result.schedules || []).map((i: Partial<ExtractedSchedule>) => ({
      ...i,
      local_id: crypto.randomUUID(),
      title: i.title || '',
      time: i.time || '',
      start_time: '',
      end_time: '',
      isSaved: false,
    }));
    if (!items.value.length) error.value = '没有识别到日程，请提供活动名称、时间或地点等信息';
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    isBusy.value = false;
  }
}
async function save(item: ExtractedSchedule) {
  if (isBusy.value) return;
  isBusy.value = true;
  error.value = '';
  try {
    if (!item.title.trim() || !item.start_time || !item.end_time)
      throw new Error('请补全标题、开始时间和结束时间');
    if (new Date(item.end_time) <= new Date(item.start_time))
      throw new Error('结束时间必须晚于开始时间');
    if (!IS_DEMO_MODE)
      await post<{ schedules: Partial<ExtractedSchedule>[] }>('/schedules', {
        title: item.title,
        description: 'AI 提取时间描述：' + item.time,
        start_time: new Date(item.start_time).toISOString(),
        end_time: new Date(item.end_time).toISOString(),
        location: item.location,
        is_ai_extracted: true,
      });
    item.isSaved = true;
    emit('saved');
    emit('notify', '日程已确认并保存');
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    isBusy.value = false;
  }
}
</script>
<template>
  <section class="card extraction-editor">
    <div class="section-heading">
      <h3><CalendarPlus :size="18" />把聊天中的约定记下来</h3>
      <span class="pill">{{ IS_DEMO_MODE ? '演示提取' : '智能日程' }}</span>
    </div>
    <form @submit.prevent="extract">
      <label
        >包含约定的聊天内容<textarea
          v-model="text"
          required
          placeholder="例如：明天下午两点一起开产品方案讨论会，在线上碰面。"
          rows="5"
        /></label
      ><button class="primary" :disabled="isBusy || !text.trim() || (!IS_DEMO_MODE && !model)">
        <Sparkles :size="16" />{{ isBusy ? '正在处理…' : '提取日程' }}
      </button>
    </form>
    <p class="field-note">AI 返回的是时间描述，请核对并填写准确的起止时间后保存。</p>
    <p v-if="!IS_DEMO_MODE && !model" class="field-note" role="status">
      AI 暂时不可用，你可以前往日程页面手动添加约定。
    </p>
    <p v-if="error" class="inline-error" role="alert">{{ redactIdentifiers(error) }}</p>
  </section>
  <section v-for="(item, i) in items" :key="item.local_id" class="card extraction-editor">
    <div class="section-heading">
      <h3>待确认日程 {{ i + 1 }}</h3>
      <span class="pill"><Clock3 :size="12" />{{ item.time }}</span>
    </div>
    <form @submit.prevent="save(item)">
      <label>日程标题<input v-model="item.title" :disabled="item.isSaved" required /></label>
      <div class="two-fields">
        <DateTimeField v-model="item.start_time" label="确认开始时间" :disabled="item.isSaved" />
        <DateTimeField v-model="item.end_time" label="确认结束时间" :disabled="item.isSaved" />
      </div>
      <label>日程地点<input v-model="item.location" :disabled="item.isSaved" /></label
      ><button class="primary" :disabled="isBusy || item.isSaved">
        <Check :size="16" />{{ item.isSaved ? '已保存到日程' : '确认并保存日程' }}
      </button>
    </form>
  </section>
</template>
