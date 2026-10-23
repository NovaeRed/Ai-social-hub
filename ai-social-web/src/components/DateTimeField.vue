<script setup lang="ts">
import { computed, ref } from 'vue';
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-vue-next';
const props = defineProps<{ modelValue: string; label: string }>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
const expanded = ref(false),
  month = ref(new Date());
const text = computed({
  get: () => props.modelValue.replace('T', ' '),
  set: (value: string) => emit('update:modelValue', value.replace(' ', 'T')),
});
const days = computed(() => {
  const y = month.value.getFullYear(),
    m = month.value.getMonth();
  const offset = (new Date(y, m, 1).getDay() + 6) % 7;
  return Array.from({ length: 42 }, (_, i) => {
    const date = new Date(y, m, i - offset + 1);
    return {
      value: date.toLocaleDateString('sv-SE'),
      day: date.getDate(),
      outside: date.getMonth() !== m,
    };
  });
});
function toggle() {
  expanded.value = !expanded.value;
  const date = new Date(props.modelValue);
  month.value = Number.isNaN(date.getTime()) ? new Date() : date;
}
function choose(value: string) {
  emit('update:modelValue', value + 'T' + (props.modelValue.split('T')[1] || '10:00'));
}
function setTime(part: number, value: string) {
  const time = (props.modelValue.split('T')[1] || '10:00').split(':');
  time[part] = value;
  emit(
    'update:modelValue',
    (props.modelValue.split('T')[0] || new Date().toLocaleDateString('sv-SE')) +
      'T' +
      time.join(':'),
  );
}
function closeOnEscape(event: KeyboardEvent) {
  if (expanded.value) {
    expanded.value = false;
    event.stopPropagation();
  }
}
function closeOnBlur(event: FocusEvent) {
  if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node | null))
    expanded.value = false;
}
</script>
<template>
  <div class="date-time-field" @keydown.esc="closeOnEscape" @focusout="closeOnBlur">
    <label
      >{{ label }}
      <span class="date-time-input">
        <input
          v-model="text"
          type="text"
          required
          pattern="[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}"
          placeholder="YYYY-MM-DD HH:mm"
          :aria-label="label"
          title="格式：2026-10-23 14:00"
          autocomplete="off"
        />
        <button
          type="button"
          class="icon-button"
          :aria-label="'选择' + label"
          :aria-expanded="expanded"
          @click="toggle"
        >
          <CalendarDays :size="18" />
        </button>
      </span>
    </label>
    <section v-if="expanded" class="date-time-picker" :aria-label="label + '选择器'">
      <div class="section-heading">
        <button
          type="button"
          class="icon-button"
          aria-label="日期选择器上个月"
          @click="month = new Date(month.getFullYear(), month.getMonth() - 1, 1)"
        >
          <ChevronLeft :size="17" />
        </button>
        <strong>{{ month.getFullYear() }} 年 {{ month.getMonth() + 1 }} 月</strong>
        <button
          type="button"
          class="icon-button"
          aria-label="日期选择器下个月"
          @click="month = new Date(month.getFullYear(), month.getMonth() + 1, 1)"
        >
          <ChevronRight :size="17" />
        </button>
      </div>
      <div class="date-picker-grid">
        <span v-for="day in ['一', '二', '三', '四', '五', '六', '日']" :key="day">{{ day }}</span>
        <button
          v-for="day in days"
          :key="day.value"
          type="button"
          :aria-label="day.value"
          :aria-pressed="modelValue.startsWith(day.value)"
          :class="{ outside: day.outside, chosen: modelValue.startsWith(day.value) }"
          @click="choose(day.value)"
        >
          {{ day.day }}
        </button>
      </div>
      <div class="date-picker-time">
        <label
          >时<select
            :value="modelValue.split('T')[1]?.split(':')[0] || '10'"
            @change="setTime(0, ($event.target as HTMLSelectElement).value)"
          >
            <option v-for="h in 24" :key="h" :value="String(h - 1).padStart(2, '0')">
              {{ String(h - 1).padStart(2, '0') }}
            </option>
          </select></label
        >
        <label
          >分<select
            :value="modelValue.split('T')[1]?.split(':')[1] || '00'"
            @change="setTime(1, ($event.target as HTMLSelectElement).value)"
          >
            <option v-for="m in 60" :key="m" :value="String(m - 1).padStart(2, '0')">
              {{ String(m - 1).padStart(2, '0') }}
            </option>
          </select></label
        >
        <button type="button" class="secondary" @click="expanded = false">确定时间</button>
      </div>
    </section>
  </div>
</template>
