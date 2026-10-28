import { markRaw } from 'vue';
import {
  LayoutDashboard,
  MessagesSquare,
  Users,
  CalendarDays,
  Sparkles,
  FileText,
} from 'lucide-vue-next';
export const NAV_ITEMS = markRaw([
  { path: '/', label: '我的概览', icon: LayoutDashboard },
  { path: '/messages', label: '消息', icon: MessagesSquare },
  { path: '/friends', label: '好友', icon: Users },
  { path: '/groups', label: '群聊', icon: Users },
  { path: '/schedules', label: '日程', icon: CalendarDays },
  { path: '/ai', label: 'AI 助手', icon: Sparkles },
  { path: '/insights', label: 'AI 活动', icon: FileText },
]);
