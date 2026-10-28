<script setup lang="ts">
import { computed } from 'vue';
import { formatJobType, formatJobStatus } from '../lib/job-presentation';
import { redactIdentifiers } from '../lib/presentation';
import { IS_DEMO_MODE } from '../services/http';
import ScheduleExtraction from '../components/ScheduleExtraction.vue';
import { CalendarDays, Sparkles, ArrowRight } from 'lucide-vue-next';
import { toRefs } from 'vue';
import { useWorkspaceStore } from '../stores/workspace';
const {
  jobs,
  models,
  selected,
  isBusy,
  messageCandidates,
  openAiSchedule,
  tools,
  tool,
  aiInput,
  aiOutput,
  model,
  targetLanguage,
  isAiBusy,
  availableModels,
  notify,
  go,
  load,
  chooseTool,
  returnToChat,
  runAi,
  stopAi,
} = toRefs(useWorkspaceStore());
const activeToolName = computed(
  () => tools.value.find((item) => item.id === tool.value)?.name || '',
);
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>AI 助手</h1>
    </div>
  </div>
  <p v-if="!IS_DEMO_MODE && !models.length" class="inline-error" role="status">
    AI 暂时不可用，请稍后再试。聊天和手动日程仍可正常使用。
  </p>
  <div class="ai-tabs">
    <button :class="{ active: tool === 'schedule' }" :disabled="isAiBusy" @click="openAiSchedule">
      <CalendarDays :size="18" />日程提取
    </button>
    <button
      v-for="t in tools"
      :key="t.id"
      :class="{ active: tool === t.id }"
      :disabled="isAiBusy"
      @click="chooseTool(t.id)"
    >
      <component :is="t.icon" :size="18" />{{ t.name }}
    </button>
  </div>
  <template v-if="tool === 'schedule'"
    ><label class="schedule-model-label"
      >提取模型<select v-model="model" aria-label="提取模型">
        <option v-if="IS_DEMO_MODE" value="">演示模型</option>
        <option v-for="m in availableModels" :key="m.option_code" :value="m.option_code">
          {{ m.display_name }}
        </option>
      </select></label
    ><ScheduleExtraction :model="model" @saved="load" @notify="notify"
  /></template>
  <div v-else class="ai-layout">
    <section class="card ai-editor">
      <div class="section-heading">
        <h3>{{ activeToolName }}</h3>
        <select v-model="model" aria-label="选择模型">
          <option v-if="IS_DEMO_MODE" value="">演示模型</option>
          <option v-for="m in availableModels" :key="m.option_code" :value="m.option_code">
            {{ m.display_name }}
          </option>
          <option v-if="!IS_DEMO_MODE && !availableModels.length" value="">暂无可用模型</option>
        </select>
      </div>
      <label for="ai-input">{{
        tool === 'smart-reply' ? '对方发来的消息' : '写下你想处理的内容'
      }}</label
      ><button
        v-if="tool === 'summarize'"
        class="text-button"
        :disabled="isBusy"
        @click="messageCandidates"
      >
        从我的消息中选择总结样本</button
      ><textarea id="ai-input" v-model="aiInput" placeholder="把想说的话放在这里，剩下的交给 AI…" />
      <div class="ai-editor-bottom">
        <select v-if="tool === 'translate'" v-model="targetLanguage" aria-label="目标语言">
          <option value="en">翻译为英语</option>
          <option value="zh">翻译为中文</option>
          <option value="ja">翻译为日语</option></select
        ><span v-else class="muted">{{ aiInput.length }} 字</span
        ><button v-if="isAiBusy" class="secondary" @click="stopAi">停止生成</button
        ><button
          v-else
          class="primary"
          :disabled="!aiInput.trim() || (!IS_DEMO_MODE && !availableModels.length)"
          @click="runAi"
        >
          <Sparkles :size="16" />开始生成
        </button>
      </div>
    </section>
    <section class="card ai-result">
      <div class="section-heading">
        <h3>生成结果</h3>
        <span class="pill">{{ isAiBusy ? '正在生成' : '结果' }}</span>
      </div>
      <pre v-if="aiOutput">{{ aiOutput }}</pre>
      <div v-else class="empty">
        <Sparkles :size="38" />
        <h3>暂无结果</h3>
        <p>输入内容并选择工具后生成结果。</p>
      </div>
      <button v-if="aiOutput && selected" class="secondary" @click="returnToChat">
        带回聊天编辑框<ArrowRight :size="16" />
      </button>
    </section>
  </div>
  <section class="card jobs">
    <div class="section-heading">
      <h3>最近的 AI 任务</h3>
      <button class="text-button" @click="go('/insights')">
        全部任务与用量<ArrowRight :size="15" />
      </button>
    </div>
    <div v-for="j in jobs" :key="j.public_id" class="job-row">
      <span class="tool-icon purple"><Sparkles :size="18" /></span>
      <div>
        <strong>{{ formatJobType(j.type) }}</strong
        ><small>{{ new Date(j.created_at).toLocaleString('zh-CN') }}</small>
        <p v-if="j.error_message">{{ redactIdentifiers(j.error_message) }}</p>
      </div>
      <span class="pill">{{ formatJobStatus(j.status) }}</span>
    </div>
    <div v-if="!jobs.length" class="empty">暂无 AI 任务</div>
  </section>
</template>
