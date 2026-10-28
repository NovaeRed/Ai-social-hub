<script setup lang="ts">
import { readableJson } from '../lib/presentation';
import { redactIdentifiers } from '../lib/presentation';
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
import { useInsights } from '../composables/useInsights';

const {
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
} = useInsights();
</script>
<template>
  <div class="page-heading">
    <div><h1>AI 活动</h1></div>
    <button class="secondary" :disabled="isBusy" @click="load()">
      <RefreshCw :size="16" />刷新
    </button>
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
      ><button class="primary" :disabled="isBusy">筛选任务</button>
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
        <h3>{{ isBusy ? '正在加载任务…' : '还没有相关任务' }}</h3>
        <p>使用 AI 工具后，可在这里追踪后台任务。</p>
      </div>
      <button v-if="hasMore" class="secondary" :disabled="isBusy" @click="load(true)">
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
      ><button class="primary" :disabled="isBusy">查询用量</button>
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
                <td>
                  {{ Number(r.tokens_used || 0).toLocaleString() }}
                </td>
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
      ><button class="primary" :disabled="isBusy">查询画像</button>
    </form>
    <div v-for="p in profiles" :key="p.profile_type" class="card data-section">
      <div class="section-heading">
        <h3>{{ label(p.profile_type) }}</h3>
        <span class="pill">{{ p.model_name }}</span>
      </div>
      <pre class="json-output">{{ readableJson(p.content) }}</pre>
      <small class="muted">更新于 {{ new Date(p.updated_at || 0).toLocaleString('zh-CN') }}</small>
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
    tabindex="-1"
    @click.self="detail = null"
    @keydown.esc="detail = null"
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
        <dd>
          {{ new Date(detail.created_at).toLocaleString('zh-CN') }}
        </dd>
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
