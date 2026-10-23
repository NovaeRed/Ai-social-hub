<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue';
import {
  Sparkles,
  RefreshCw,
  Activity,
  ChartNoAxesColumn,
  UserRound,
  X,
  ChevronRight,
  CircleAlert,
} from 'lucide-vue-next';
import { demo, request } from '../lib/api';
import * as seed from '../lib/demo';
import { readableJson, redactIdentifiers } from '../lib/presentation';
const tab = ref('tasks'),
  jobs = ref<any[]>([]),
  profiles = ref<any[]>([]),
  usage = ref<any>(null),
  detail = ref<any>(null),
  error = ref(''),
  busy = ref(false),
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
async function load(more = false) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    if (tab.value === 'tasks') {
      if (demo) jobs.value = seed.jobs;
      else {
        const qs = new URLSearchParams({ limit: '20' });
        if (status.value) qs.set('status', status.value);
        if (taskType.value) qs.set('task_type', taskType.value);
        if (more && cursor.value) qs.set('cursor', String(cursor.value));
        const page = await request('/ai/jobs?' + qs);
        jobs.value = more ? [...jobs.value, ...page.items] : page.items;
        cursor.value = page.next_cursor;
        hasMore.value = page.has_more;
      }
    } else if (tab.value === 'usage') {
      if (dateFrom.value > dateTo.value) throw new Error('开始日期不能晚于结束日期');
      usage.value = demo
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
        : await request(
            '/ai/usage?' +
              new URLSearchParams({
                date_from: dateFrom.value,
                date_to: dateTo.value,
                ...(provider.value ? { provider: provider.value } : {}),
              }),
          );
    } else
      profiles.value = demo
        ? []
        : (
            await request(
              '/ai/profiles' + (profileType.value ? '?profile_type=' + profileType.value : ''),
            )
          ).items;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function changeTab(id: string) {
  tab.value = id;
  await load();
}
async function openJob(j: any) {
  error.value = '';
  try {
    detail.value = demo
      ? { ...j, output_payload: { message: '演示任务详情' } }
      : await request('/ai/jobs/' + j.public_id);
  } catch (e) {
    error.value = (e as Error).message;
  }
}
function schedulePoll() {
  poll = setTimeout(async () => {
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
onUnmounted(() => clearTimeout(poll));
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">YOUR INTELLIGENCE, IN VIEW</span>
      <h1>每一点智能，都看得见</h1>
      <p>追踪 AI 任务、查看使用情况，了解你的沟通画像。</p>
    </div>
    <button class="secondary" :disabled="busy" @click="load()"><RefreshCw :size="16" />刷新</button>
  </div>
  <div class="ai-tabs">
    <button :class="{ active: tab === 'tasks' }" @click="changeTab('tasks')">
      <Activity :size="17" />任务记录</button
    ><button :class="{ active: tab === 'usage' }" @click="changeTab('usage')">
      <ChartNoAxesColumn :size="17" />使用统计</button
    ><button :class="{ active: tab === 'profiles' }" @click="changeTab('profiles')">
      <UserRound :size="17" />沟通画像
    </button>
  </div>
  <p v-if="error" class="inline-error" role="alert">
    <CircleAlert :size="17" />{{ redactIdentifiers(error)
    }}<span v-if="tab === 'profiles'">画像服务当前不可用，其他 AI 功能可继续使用。</span>
  </p>
  <template v-if="tab === 'tasks'"
    ><form class="card filter-bar" @submit.prevent="load()">
      <label
        >任务状态<select v-model="status">
          <option value="">全部状态</option>
          <option
            v-for="s in ['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED']"
            :key="s"
            :value="s"
          >
            {{ label(s) }}
          </option>
        </select></label
      ><label
        >任务类型<select v-model="taskType">
          <option value="">全部类型</option>
          <option
            v-for="s in [
              'POLISH',
              'TRANSLATION',
              'SMART_REPLY',
              'CHAT_SUMMARY',
              'SCHEDULE_EXTRACTION',
              'PERSONA_ANALYSIS',
            ]"
            :key="s"
            :value="s"
          >
            {{ label(s) }}
          </option>
        </select></label
      ><button class="primary" :disabled="busy">筛选任务</button>
    </form>
    <section class="card jobs">
      <button v-for="j in jobs" :key="j.public_id" class="job-row job-button" @click="openJob(j)">
        <span class="tool-icon purple"><Sparkles :size="18" /></span>
        <div>
          <strong>{{ label(j.type) }}</strong
          ><small
            >{{ new Date(j.created_at).toLocaleString('zh-CN') }} ·
            {{ j.resolved_model_name || '模型处理中' }}</small
          >
        </div>
        <span :class="['pill', { failed: j.status === 'FAILED' }]">{{ label(j.status) }}</span
        ><ChevronRight :size="16" />
      </button>
      <div v-if="!jobs.length" class="empty">
        <Activity :size="38" />
        <h3>{{ busy ? '正在加载任务…' : '还没有相关任务' }}</h3>
        <p>使用 AI 工具后，可在这里追踪后台任务。</p>
      </div>
      <button v-if="hasMore" class="secondary" :disabled="busy" @click="load(true)">
        加载更多任务
      </button>
    </section></template
  >
  <template v-else-if="tab === 'usage'"
    ><form class="card filter-bar" @submit.prevent="load()">
      <label>开始日期<input v-model="dateFrom" type="date" required /></label
      ><label>结束日期<input v-model="dateTo" type="date" required /></label
      ><label
        >提供商<select v-model="provider">
          <option value="">全部提供商</option>
          <option value="dashscope">DashScope</option>
          <option value="openai">OpenAI</option>
          <option value="anthropic">Anthropic</option>
        </select></label
      ><button class="primary" :disabled="busy">查询用量</button>
    </form>
    <template v-if="usage"
      ><div class="usage-stats">
        <article class="card">
          <span>总 Token 用量</span
          ><strong>{{ Number(usage.summary?.total_tokens || 0).toLocaleString() }}</strong>
        </article>
        <article class="card">
          <span>输入 / 输出 Token</span
          ><strong
            >{{ usage.summary?.input_tokens || 0 }}
            <small>/ {{ usage.summary?.output_tokens || 0 }}</small></strong
          >
        </article>
        <article class="card">
          <span>累计费用（后端计费值）</span
          ><strong>{{ Number(usage.summary?.total_cost || 0).toFixed(4) }}</strong>
        </article>
      </div>
      <section class="card data-section">
        <h3>每日用量</h3>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>日期</th>
                <th>Token 用量</th>
                <th>费用</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in usageRows" :key="r.date">
                <td>{{ r.date }}</td>
                <td>{{ Number(r.tokens_used || 0).toLocaleString() }}</td>
                <td>{{ Number(r.cost || 0).toFixed(4) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="!usageRows.length" class="empty">所选时间段内暂无用量</p>
      </section>
      <section class="card data-section">
        <h3>按提供商统计</h3>
        <div v-for="p in usage.by_provider" :key="p.provider" class="usage-provider">
          <span>{{ p.provider }}</span
          ><strong>{{ Number(p.tokens_used || 0).toLocaleString() }} tokens</strong
          ><span>{{ Number(p.cost || 0).toFixed(4) }}</span>
        </div>
        <p v-if="!usage.by_provider?.length" class="empty">暂无提供商用量</p>
      </section></template
    ></template
  >
  <template v-else
    ><form class="card filter-bar" @submit.prevent="load()">
      <label
        >画像类型<select v-model="profileType">
          <option value="">全部画像</option>
          <option value="PERSONA">性格画像</option>
          <option value="PREFERENCES">沟通偏好</option>
          <option value="BEHAVIOR">行为分析</option>
        </select></label
      ><button class="primary" :disabled="busy">查询画像</button>
    </form>
    <div v-for="p in profiles" :key="p.profile_type" class="card data-section">
      <div class="section-heading">
        <h3>{{ label(p.profile_type) }}</h3>
        <span class="pill">{{ p.model_name }}</span>
      </div>
      <pre class="json-output">{{ readableJson(p.content) }}</pre>
      <small class="muted">更新于 {{ new Date(p.updated_at).toLocaleString('zh-CN') }}</small>
    </div>
    <div v-if="!profiles.length && !error" class="card empty">
      <UserRound :size="38" />
      <h3>还没有生成沟通画像</h3>
      <p>在个人设置中开启分析授权后，后台会根据交流生成画像。</p>
    </div></template
  >
  <div
    v-if="detail"
    class="modal-overlay"
    @click.self="detail = null"
    @keydown.esc="detail = null"
    tabindex="-1"
  >
    <section
      class="modal card wide-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="job-title"
    >
      <button class="icon-button close" aria-label="关闭任务详情" @click="detail = null">
        <X :size="20" />
      </button>
      <h2 id="job-title">{{ label(detail.type) }} · 任务详情</h2>
      <dl class="detail-list">
        <dt>状态</dt>
        <dd>{{ label(detail.status) }}</dd>
        <dt>模型</dt>
        <dd>{{ detail.resolved_model_name || '暂未解析' }}</dd>
        <dt>提供商</dt>
        <dd>{{ detail.resolved_provider || '暂未解析' }}</dd>
        <dt>创建时间</dt>
        <dd>{{ new Date(detail.created_at).toLocaleString('zh-CN') }}</dd>
        <dt v-if="detail.completed_at">完成时间</dt>
        <dd v-if="detail.completed_at">
          {{ new Date(detail.completed_at).toLocaleString('zh-CN') }}
        </dd>
      </dl>
      <p v-if="detail.error_message" class="inline-error">
        {{ redactIdentifiers(detail.error_message) }}
      </p>
      <h4>执行结果</h4>
      <pre class="json-output">{{
        detail.output_payload ? readableJson(detail.output_payload) : '任务尚无结果'
      }}</pre>
      <h4 v-if="detail.token_usage">Token 使用</h4>
      <pre v-if="detail.token_usage" class="json-output">{{
        readableJson(detail.token_usage)
      }}</pre>
    </section>
  </div>
</template>
