import type { Ref } from 'vue';
import { IS_DEMO_MODE, post, request, type Schedule } from '../services/http';
interface ScheduleForm {
  title: string;
  description: string;
  start_time: string;
  end_time: string;
  location: string;
}
interface ScheduleEditorContext {
  editing: Ref<string>;
  scheduleForm: Ref<ScheduleForm>;
  schedules: Ref<Schedule[]>;
  chosenDate: Ref<string>;
  modal: Ref<string>;
  action: (callback: () => Promise<void>) => Promise<void>;
  notify: (text: string) => void;
}
export function useScheduleEditor(context: ScheduleEditorContext) {
  const { editing, scheduleForm, schedules, chosenDate, modal, action, notify } = context;
  function openSchedule(s?: Schedule) {
    editing.value = s?.public_id || '';
    const local = (x: string) => {
      const d = new Date(x);
      return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    };
    scheduleForm.value = s
      ? {
          title: s.title,
          description: s.description || '',
          start_time: local(s.start_time),
          end_time: local(s.end_time),
          location: s.location || '',
        }
      : {
          title: '',
          description: '',
          start_time: `${chosenDate.value}T10:00`,
          end_time: `${chosenDate.value}T11:00`,
          location: '',
        };
    modal.value = 'schedule';
  }
  async function saveSchedule() {
    await action(async () => {
      const f = scheduleForm.value;
      const validDateTime = (value: string) => {
        if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) return false;
        const date = new Date(value);
        return (
          !Number.isNaN(date.getTime()) &&
          new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16) ===
            value
        );
      };
      if (!validDateTime(f.start_time) || !validDateTime(f.end_time))
        throw new Error('请填写有效日期和时间，格式如 2026-10-23 14:00');
      if (!f.title.trim() || new Date(f.end_time) <= new Date(f.start_time))
        throw new Error('请填写标题，并确保结束时间晚于开始时间');
      const data = {
        ...f,
        start_time: new Date(f.start_time).toISOString(),
        end_time: new Date(f.end_time).toISOString(),
      };
      const saved: Schedule = IS_DEMO_MODE
        ? { ...data, public_id: editing.value || `demo-sch-${Date.now()}` }
        : await post<Schedule>(
            '/schedules' + (editing.value ? `/${encodeURIComponent(editing.value)}` : ''),
            data,
            editing.value ? 'PUT' : 'POST',
          );
      const i = schedules.value.findIndex((s) => s.public_id === editing.value);
      if (i >= 0) schedules.value[i] = saved;
      else schedules.value.push(saved);
      modal.value = '';
      notify('日程已保存');
    });
  }
  async function removeSchedule() {
    await action(async () => {
      if (!IS_DEMO_MODE)
        await request<Schedule[]>(`/schedules/${encodeURIComponent(editing.value)}`, {
          method: 'DELETE',
        });
      schedules.value = schedules.value.filter((s) => s.public_id !== editing.value);
      modal.value = '';
      notify('日程已删除');
    });
  }

  return { openSchedule, saveSchedule, removeSchedule };
}
