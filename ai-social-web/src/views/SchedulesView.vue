<script setup lang="ts">
import { redactIdentifiers } from '../lib/presentation';
import {
  CalendarDays,
  Sparkles,
  Plus,
  ChevronRight,
  ChevronLeft,
  Clock3,
  MapPin,
} from 'lucide-vue-next';
import { toRefs } from 'vue';
import { useWorkspaceStore } from '../stores/workspace';
const {
  schedules,
  month,
  chosenDate,
  calendarDays,
  today,
  daySchedules,
  openSchedule,
  isSchedulesLoading,
  scheduleError,
  moveMonth,
  time,
} = toRefs(useWorkspaceStore());
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>日程</h1>
    </div>
    <button class="primary" @click="openSchedule()"><Plus :size="17" />新建日程</button>
  </div>
  <div class="schedule-layout">
    <section class="card calendar">
      <p v-if="scheduleError" class="inline-error" role="alert">
        {{ redactIdentifiers(scheduleError) }}
      </p>
      <div class="section-heading">
        <h3>{{ month.getFullYear() }} 年 {{ month.getMonth() + 1 }} 月</h3>
        <div class="inline-actions">
          <button class="icon-button" aria-label="上个月" @click="moveMonth(-1)">
            <ChevronLeft :size="19" /></button
          ><button class="icon-button" aria-label="下个月" @click="moveMonth(1)">
            <ChevronRight :size="19" />
          </button>
        </div>
      </div>
      <div class="calendar-grid">
        <span v-for="d in ['一', '二', '三', '四', '五', '六', '日']" :key="d" class="weekday">{{
          d
        }}</span
        ><button
          v-for="d in calendarDays"
          :key="d.date"
          :class="{
            outside: !d.current,
            chosen: d.date === chosenDate,
            today: d.date === today,
          }"
          @click="chosenDate = d.date"
        >
          {{ d.day
          }}<i
            v-if="
              schedules.some((s) => new Date(s.start_time).toLocaleDateString('sv-SE') === d.date)
            "
          />
        </button>
      </div>
      <p class="muted calendar-note" role="status">
        {{ isSchedulesLoading ? '正在更新日程…' : '带圆点的日期有已安排日程' }}
      </p>
    </section>
    <section class="card day-agenda">
      <div class="section-heading">
        <h3>{{ chosenDate }} 的安排</h3>
        <span class="pill">{{ daySchedules.length }} 项</span>
      </div>
      <button
        v-for="s in daySchedules"
        :key="s.public_id"
        class="schedule-item"
        @click="openSchedule(s)"
      >
        <span v-if="s.is_ai_extracted" class="pill"><Sparkles :size="12" />AI 提取</span>
        <h3>{{ s.title }}</h3>
        <p><Clock3 :size="15" />{{ time(s.start_time) }} – {{ time(s.end_time) }}</p>
        <p><MapPin :size="15" />{{ s.location || '未设置地点' }}</p>
        <small>{{ s.description }}</small>
      </button>
      <div v-if="!daySchedules.length" class="empty">
        <CalendarDays :size="35" />
        <h3>暂无日程</h3>
        <p>所选日期暂无日程。</p>
        <button class="secondary" @click="openSchedule()">添加日程</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.schedule-layout {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 25px;
}
.calendar,
.day-agenda {
  padding: 25px;
}
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 9px;
  margin-top: 30px;
}
.calendar-grid button {
  position: relative;
  height: 50px;
  border-radius: 9px;
  font-size: 12px;
}
.calendar-grid .outside {
  color: var(--theme-text-c7cbd0, #c7cbd0);
}
.calendar-grid .today {
  border: 1px solid var(--theme-border-eac6b6, #eac6b6);
  color: var(--accent);
}
.calendar-grid .chosen {
  background: var(--action-background, var(--accent));
  color: var(--theme-text-fff, #fff);
}
.calendar-grid button:hover {
  background: var(--theme-background-fff0e8, #fff0e8);
  color: var(--accent);
}
.calendar-grid i {
  position: absolute;
  bottom: 6px;
  left: calc(50% - 2px);
  width: 4px;
  height: 4px;
  background: var(--theme-background-b49acc, #b49acc);
  border-radius: 50%;
}
.calendar-note {
  font-size: 10px;
  margin-top: var(--space-lg);
}
@media (max-width: 1150px) {
  .schedule-layout {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 600px) {
  .calendar,
  .day-agenda {
    padding: 19px;
  }
}
@media (max-width: 600px) {
  .calendar-grid {
    gap: var(--space-xs);
    margin-top: 15px;
  }
}
@media (max-width: 600px) {
  .calendar-grid button {
    height: 42px;
  }
}
@media (max-width: 600px) {
  .calendar-note {
    margin-top: 18px;
  }
}
</style>
