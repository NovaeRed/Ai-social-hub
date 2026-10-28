import type { CursorPage } from '../types/domain';

import type { AiJob, AiProfile, AiUsage } from '../types/domain';
import { ref, onMounted, onUnmounted, computed } from 'vue';

import { IS_DEMO_MODE, request } from '../services/http';
import * as seed from '../lib/demo';

export function useInsights() {
  const tab = ref('tasks'),
    jobs = ref<AiJob[]>([]),
    profiles = ref<AiProfile[]>([]),
    usage = ref<AiUsage | null>(null),
    detail = ref<AiJob | null>(null),
    error = ref(''),
    isBusy = ref(false),
    status = ref(''),
    taskType = ref(''),
    cursor = ref<number | null>(null),
    hasMore = ref(false),
    profileType = ref(''),
    provider = ref(''),
    dateFrom = ref(
      new Date(new Date().getFullYear(), new Date().getMonth(), 1).toLocaleDateString('sv-SE'),
    ),
    dateTo = ref(new Date().toLocaleDateString('sv-SE'));
  const labels: Record<string, string> = {
    PENDING: '排队中',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
    POLISH: '表达润色',
    TRANSLATION: '智能翻译',
    SMART_REPLY: '智能回复',
    CHAT_SUMMARY: '内容总结',
    SPEECH_TO_TEXT: '语音转写',
    SCHEDULE_EXTRACTION: '日程提取',
    PERSONA_ANALYSIS: '画像分析',
    PERSONA: '性格画像',
    PREFERENCES: '沟通偏好',
    BEHAVIOR: '行为分析',
  };
  const label = (s: string) => labels[s] || s;
  const usageRows = computed(() => usage.value?.daily_breakdown || []);
  let poll: ReturnType<typeof setTimeout> | undefined;
  let isDisposed = false;
  async function load(more = false) {
    if (isBusy.value) return;
    isBusy.value = true;
    error.value = '';
    try {
      if (tab.value === 'tasks') {
        if (IS_DEMO_MODE) jobs.value = seed.jobs;
        else {
          const qs = new URLSearchParams({ limit: '20' });
          if (status.value) qs.set('status', status.value);
          if (taskType.value) qs.set('task_type', taskType.value);
          if (more && cursor.value) qs.set('cursor', String(cursor.value));
          const page = await request<CursorPage<AiJob>>('/ai/jobs?' + qs);
          jobs.value = more ? [...jobs.value, ...page.items] : page.items;
          cursor.value = page.next_cursor;
          hasMore.value = page.has_more;
        }
      } else if (tab.value === 'usage') {
        if (dateFrom.value > dateTo.value) throw new Error('开始日期不能晚于结束日期');
        usage.value = IS_DEMO_MODE
          ? {
              summary: {
                total_tokens: 15000,
                total_cost: 0.25,
                input_tokens: 9000,
                output_tokens: 6000,
              },
              daily_breakdown: [{ date: dateTo.value, tokens_used: 15000, cost: 0.25 }],
              by_provider: [{ provider: 'demo', tokens_used: 15000, cost: 0.25 }],
            }
          : await request<AiUsage>(
              '/ai/usage?' +
                new URLSearchParams({
                  date_from: dateFrom.value,
                  date_to: dateTo.value,
                  ...(provider.value ? { provider: provider.value } : {}),
                }),
            );
      } else
        profiles.value = IS_DEMO_MODE
          ? []
          : (
              await request<{ items: AiProfile[] }>(
                '/ai/profiles' + (profileType.value ? '?profile_type=' + profileType.value : ''),
              )
            ).items;
    } catch (e) {
      error.value = (e as Error).message;
    } finally {
      isBusy.value = false;
    }
  }
  async function changeTab(id: string) {
    tab.value = id;
    await load();
  }
  async function openJob(j: AiJob) {
    error.value = '';
    try {
      detail.value = IS_DEMO_MODE
        ? { ...j, output_payload: { message: '演示任务详情' } }
        : await request<AiJob>('/ai/jobs/' + j.public_id);
    } catch (e) {
      error.value = (e as Error).message;
    }
  }
  function schedulePoll() {
    // 首次加载或轮询尚未完成就离开页面时，禁止重新创建定时器。
    if (isDisposed) return;
    poll = setTimeout(async () => {
      if (isDisposed) return;
      if (
        tab.value === 'tasks' &&
        jobs.value.some((j) => ['PENDING', 'PROCESSING'].includes(j.status))
      ) {
        if (detail.value && ['PENDING', 'PROCESSING'].includes(detail.value.status))
          await openJob(detail.value);
        await load();
      }
      schedulePoll();
    }, 10000);
  }
  onMounted(async () => {
    await load();
    schedulePoll();
  });
  onUnmounted(() => {
    isDisposed = true;
    clearTimeout(poll);
  });

  return {
    tab,
    jobs,
    profiles,
    usage,
    detail,
    error,
    isBusy,
    status,
    taskType,
    hasMore,
    profileType,
    provider,
    dateFrom,
    dateTo,
    label,
    usageRows,
    load,
    changeTab,
    openJob,
  };
}
