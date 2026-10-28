import { computed, ref, type Ref } from 'vue';
import { IS_DEMO_MODE, request, type Schedule } from '../services/http';
export function useCalendar(schedules: Ref<Schedule[]>, getSessionGeneration: () => number) {
  const month = ref(new Date());
  const chosenDate = ref(new Date().toLocaleDateString('sv-SE'));
  const calendarDays = computed(() => {
    const y = month.value.getFullYear(),
      m = month.value.getMonth(),
      offset = (new Date(y, m, 1).getDay() + 6) % 7;
    return Array.from({ length: 42 }, (_, i) => {
      const d = new Date(y, m, i - offset + 1);
      return { day: d.getDate(), date: d.toLocaleDateString('sv-SE'), current: d.getMonth() === m };
    });
  });
  const today = new Date().toLocaleDateString('sv-SE'),
    dateLabel = new Date().toLocaleDateString('zh-CN', {
      month: 'long',
      day: 'numeric',
      weekday: 'long',
    });
  const todaySchedules = computed(() =>
    schedules.value.filter((s) => new Date(s.start_time).toLocaleDateString('sv-SE') === today),
  );
  const daySchedules = computed(() =>
    schedules.value
      .filter((s) => new Date(s.start_time).toLocaleDateString('sv-SE') === chosenDate.value)
      .sort((a, b) => a.start_time.localeCompare(b.start_time)),
  );

  let scheduleRequest = 0;
  const isSchedulesLoading = ref(false),
    scheduleError = ref('');
  async function moveMonth(n: number) {
    month.value = new Date(month.value.getFullYear(), month.value.getMonth() + n, 1);
    if (IS_DEMO_MODE) return;
    const sequence = ++scheduleRequest,
      generation = getSessionGeneration();
    const from = new Date(month.value.getFullYear(), month.value.getMonth(), 1).toISOString();
    const to = new Date(month.value.getFullYear(), month.value.getMonth() + 1, 1).toISOString();
    isSchedulesLoading.value = true;
    scheduleError.value = '';
    try {
      const result = await request<Schedule[]>(
        `/schedules?start_time=${encodeURIComponent(from)}&end_time=${encodeURIComponent(to)}`,
      );
      if (sequence === scheduleRequest && generation === getSessionGeneration())
        schedules.value = result;
    } catch (e) {
      if (sequence === scheduleRequest && generation === getSessionGeneration())
        scheduleError.value = (e as Error).message;
    } finally {
      if (sequence === scheduleRequest) isSchedulesLoading.value = false;
    }
  }

  return {
    month,
    chosenDate,
    calendarDays,
    today,
    dateLabel,
    todaySchedules,
    daySchedules,
    isSchedulesLoading,
    scheduleError,
    moveMonth,
  };
}
