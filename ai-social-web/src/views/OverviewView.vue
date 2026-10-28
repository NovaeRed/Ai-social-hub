<script setup lang="ts">
import { formatMessagePreview } from '../lib/presentation';
import {
  MessagesSquare,
  Users,
  CalendarDays,
  Sparkles,
  ArrowUpRight,
  ArrowRight,
  Plus,
  ChevronRight,
} from 'lucide-vue-next';
import { computed, toRefs } from 'vue';
import { useWorkspaceStore } from '../stores/workspace';
const {
  conversations,
  friends,
  jobs,
  tools,
  dateLabel,
  todaySchedules,
  unread,
  go,
  openConversation,
  openSchedule,
  chooseTool,
  time,
} = toRefs(useWorkspaceStore());
const completedJobsCount = computed(
  () => jobs.value.filter((job) => job.status === 'COMPLETED').length,
);
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>我的概览</h1>
    </div>
    <span class="date-chip"><CalendarDays :size="16" />{{ dateLabel }}</span>
  </div>
  <div class="stats-grid">
    <button class="stat card" @click="go('/messages')">
      <span class="stat-icon coral"><MessagesSquare :size="21" /></span>
      <div>
        <span>未读消息</span><strong>{{ unread }}<small>条</small></strong>
      </div>
      <span class="stat-foot">查看详情<ArrowUpRight :size="15" /></span></button
    ><button class="stat card" @click="go('/friends')">
      <span class="stat-icon purple"><Users :size="21" /></span>
      <div>
        <span>我的好友</span><strong>{{ friends.length }}<small>位</small></strong>
      </div>
      <span class="stat-foot">查看详情<ArrowUpRight :size="15" /></span></button
    ><button class="stat card" @click="go('/schedules')">
      <span class="stat-icon green"><CalendarDays :size="21" /></span>
      <div>
        <span>今日日程</span><strong>{{ todaySchedules.length }}<small>项</small></strong>
      </div>
      <span class="stat-foot">查看详情<ArrowUpRight :size="15" /></span></button
    ><button class="stat card" @click="go('/ai')">
      <span class="stat-icon blue"><Sparkles :size="21" /></span>
      <div>
        <span>AI 已完成任务</span><strong>{{ completedJobsCount }}<small>次</small></strong>
      </div>
      <span class="stat-foot">查看详情<ArrowUpRight :size="15" /></span>
    </button>
  </div>
  <div class="dashboard-columns">
    <section class="card recent">
      <div class="section-heading">
        <h3>最近的对话</h3>
        <button class="text-button" @click="go('/messages')">
          全部消息<ArrowRight :size="15" />
        </button>
      </div>
      <button
        v-for="(c, i) in conversations.slice(0, 4)"
        :key="c.public_id"
        class="conversation-row"
        @click="openConversation(c.public_id)"
      >
        <span :class="['avatar', ['peach', 'lavender', 'sage', 'sand'][i % 4]]"
          ><Users v-if="c.type === 'GROUP'" :size="22" /><span v-else>{{
            c.name.slice(-2)
          }}</span></span
        ><span class="conversation-info"
          ><strong
            >{{ c.name }}<span v-if="c.type === 'GROUP'" class="group-label">群聊</span></strong
          ><small>{{ formatMessagePreview(c.latest_message) }}</small></span
        ><span v-if="c.unread_count" class="count">{{ c.unread_count }}</span
        ><ChevronRight v-else :size="16" class="muted" />
      </button>
      <div v-if="!conversations.length" class="empty">暂无对话，去好友页开始聊天吧。</div>
    </section>
    <section class="card agenda">
      <div class="section-heading">
        <h3>今天的安排</h3>
        <button class="icon-button" aria-label="新建日程" @click="openSchedule()">
          <Plus :size="18" />
        </button>
      </div>
      <div class="agenda-date">{{ dateLabel }}</div>
      <button
        v-for="(s, i) in todaySchedules.slice(0, 3)"
        :key="s.public_id"
        class="agenda-row"
        @click="openSchedule(s)"
      >
        <span class="agenda-time">{{ time(s.start_time) }}</span
        ><span :class="['timeline-dot', { purplebg: i === 1 }]" /><span
          ><strong>{{ s.title }}<Sparkles v-if="s.is_ai_extracted" :size="12" /></strong
          ><small>{{ s.location || '未设置地点' }}</small></span
        >
      </button>
      <div v-if="!todaySchedules.length" class="empty">今天还没有安排</div>
      <button class="agenda-more" @click="go('/schedules')">
        查看我的日程<ArrowRight :size="15" />
      </button>
    </section>
  </div>
  <section class="tools-section">
    <div class="section-heading">
      <h3>AI 工具</h3>
    </div>
    <div class="tools-grid">
      <button v-for="t in tools" :key="t.id" class="tool-card card" @click="chooseTool(t.id)">
        <span :class="['tool-icon', t.color]"><component :is="t.icon" :size="23" /></span
        ><ArrowUpRight class="tool-arrow" :size="17" />
        <h4>{{ t.name }}</h4>
        <p>{{ t.text }}</p>
      </button>
    </div>
  </section>
</template>
