<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  LayoutDashboard,
  MessagesSquare,
  Users,
  CalendarDays,
  Sparkles,
  Settings,
  Search,
  ArrowUpRight,
  ArrowRight,
  Plus,
  ChevronRight,
  ChevronLeft,
  Bell,
  Send,
  Paperclip,
  X,
  LogOut,
  Check,
  Clock3,
  MapPin,
  WandSparkles,
  Languages,
  FileText,
  RefreshCw,
  Menu,
  MessageCircle,
  Flower2,
} from 'lucide-vue-next';
import {
  demo,
  request,
  post,
  tokens,
  clearTokens,
  stream,
  subscribe,
  downloadFile,
  type User,
  type Conversation,
  type Message,
  type Schedule,
} from './lib/api';
import * as seed from './lib/demo';
import { normalizeCurrentUser } from './lib/identity';
import { displayUserName, redactIdentifiers } from './lib/presentation';
import GroupsPage from './components/GroupsPage.vue';
import ChatAssistant from './components/ChatAssistant.vue';
import InsightsPage from './components/InsightsPage.vue';
import AccountSecurity from './components/AccountSecurity.vue';
import ScheduleExtraction from './components/ScheduleExtraction.vue';
import DateTimeField from './components/DateTimeField.vue';
import ConversationRow from './components/ConversationRow.vue';
import MessageActions from './components/MessageActions.vue';
import { useChatPreferences } from './lib/chatPreferences';
const route = useRoute(),
  router = useRouter();
const nav = [
  { path: '/', label: '我的概览', icon: LayoutDashboard },
  { path: '/messages', label: '消息', icon: MessagesSquare },
  { path: '/friends', label: '好友', icon: Users },
  { path: '/groups', label: '群聊', icon: Users },
  { path: '/schedules', label: '日程', icon: CalendarDays },
  { path: '/ai', label: 'AI 助手', icon: Sparkles },
  { path: '/insights', label: 'AI 活动', icon: FileText },
];
const user = ref<User>(demo ? { ...seed.me } : { public_id: '', nickname: '' }),
  conversations = ref<Conversation[]>(demo ? structuredClone(seed.conversations) : []),
  friends = ref<User[]>(demo ? structuredClone(seed.friends) : []),
  schedules = ref<Schedule[]>(demo ? structuredClone(seed.schedules) : []),
  jobs = ref<any[]>(demo ? seed.jobs : []),
  history = ref<Record<string, Message[]>>(demo ? structuredClone(seed.messages) : {});
const requests = ref<any>({ incoming: [], outgoing: [] }),
  models = ref<any[]>([]),
  selected = ref(''),
  query = ref(''),
  draft = ref(''),
  busy = ref(false),
  loading = ref(false),
  error = ref(''),
  toast = ref(''),
  modal = ref(''),
  mobile = ref(false),
  register = ref(false);
const login = ref({ username: '', nickname: '', password: '', email: '' }),
  profile = ref({ nickname: '', email: '', ai_analysis_enabled: false }),
  friendForm = ref({ target_user_public_id: '', message: '' }),
  groupForm = ref({ name: '', members: [] as string[] }),
  scheduleForm = ref({ title: '', description: '', start_time: '', end_time: '', location: '' }),
  editing = ref('');
const lookupUser = ref<User | null>(null),
  contact = ref<User | null>(null),
  requestKeyword = ref(''),
  requestTab = ref('incoming'),
  conversationFilter = ref('ALL'),
  realtime = ref('未连接');
const friendQuery = ref(''),
  discoverableUsers = ref<User[]>([]),
  loadingContacts = ref(false);
const matchingContacts = computed(() =>
  discoverableUsers.value.filter((person) =>
    (person.nickname + ' ' + (person.username || ''))
      .toLowerCase()
      .includes(friendQuery.value.toLowerCase()),
  ),
);
const contactMode = ref('lookup'),
  lookupType = ref('USERNAME'),
  userCode = ref(''),
  lookupBusy = ref(false);
let lookupSequence = 0;
watch(lookupType, () => {
  lookupSequence++;
  lookupBusy.value = false;
  userCode.value = '';
  lookupUser.value = null;
  friendForm.value.target_user_public_id = '';
  error.value = '';
});
async function lookupAccount() {
  const code = userCode.value.trim(),
    sequence = ++lookupSequence,
    generation = sessionGeneration;
  lookupUser.value = null;
  friendForm.value.target_user_public_id = '';
  error.value = '';
  if (!code) {
    error.value = '请输入需要查找的账号';
    return;
  }
  if (lookupType.value === 'PHONE' && !/^1[3-9]\d{9}$/.test(code)) {
    error.value = '请填写有效的中国大陆手机号';
    return;
  }
  lookupBusy.value = true;
  try {
    const person = demo
      ? { public_id: 'demo-new-friend', nickname: '顾星河', username: 'xinghe' }
      : await request(
          lookupType.value === 'PUBLIC_ID'
            ? `/users/${encodeURIComponent(code)}`
            : '/users/lookup?' + new URLSearchParams({ type: lookupType.value, value: code }),
        );
    if (
      sequence !== lookupSequence ||
      generation !== sessionGeneration ||
      modal.value !== 'friend' ||
      contactMode.value !== 'lookup'
    )
      return;
    if (!person?.public_id) throw new Error('没有找到该用户，请核对后重试');
    if (person.public_id === user.value.public_id) throw new Error('不能添加自己为好友');
    if (friends.value.some((f) => f.public_id === person.public_id))
      throw new Error('你们已经是好友了，可直接发起聊天');
    chooseContact({ ...person, nickname: displayUserName(person, '用户') });
    userCode.value = '';
  } catch (e) {
    if (sequence === lookupSequence && generation === sessionGeneration)
      error.value = (e as Error).message;
  } finally {
    if (sequence === lookupSequence) lookupBusy.value = false;
  }
}
function switchContacts(mode: string) {
  contactMode.value = mode;
  lookupSequence++;
  lookupBusy.value = false;
  lookupUser.value = null;
  friendForm.value.target_user_public_id = '';
  error.value = '';
  if (mode === 'group') void discoverContacts();
}
async function discoverContacts() {
  const generation = sessionGeneration;
  loadingContacts.value = true;
  error.value = '';
  lookupUser.value = null;
  friendQuery.value = '';
  friendForm.value = { target_user_public_id: '', message: '' };
  discoverableUsers.value = [];
  try {
    if (demo) {
      discoverableUsers.value = [
        { public_id: 'demo-new-friend', nickname: '顾星河', username: 'xinghe' },
      ];
      return;
    }
    const groups: any[] = [];
    let cursor: number | undefined;
    const seen = new Set<number>();
    do {
      const page = await request(
        '/groups?' +
          new URLSearchParams({ limit: '100', ...(cursor ? { cursor: String(cursor) } : {}) }),
      );
      groups.push(...page.items);
      if (!page.has_more || !page.next_cursor || seen.has(page.next_cursor)) break;
      cursor = page.next_cursor;
      seen.add(page.next_cursor);
    } while (true);
    const people = new Map<string, User>();
    let failed = 0;
    for (let i = 0; i < groups.length; i += 8) {
      const result = await Promise.allSettled(
        groups
          .slice(i, i + 8)
          .map((g) => request(`/groups/${encodeURIComponent(g.public_id)}/members`)),
      );
      for (const entry of result) {
        if (entry.status === 'fulfilled')
          for (const person of entry.value) {
            if (
              person.public_id !== user.value.public_id &&
              !friends.value.some((f) => f.public_id === person.public_id)
            )
              people.set(person.public_id, {
                ...person,
                nickname: displayUserName(person, '群成员'),
              });
          }
        else failed++;
      }
    }
    if (generation === sessionGeneration) {
      discoverableUsers.value = [...people.values()];
      if (failed) error.value = '部分群联系人暂时无法加载，请稍后重试。';
    }
  } catch (e) {
    if (generation === sessionGeneration) error.value = (e as Error).message;
  } finally {
    loadingContacts.value = false;
  }
}
function chooseContact(person: User) {
  lookupUser.value = person;
  friendForm.value.target_user_public_id = person.public_id;
}
let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
let reconnectAttempt = 0,
  connectEpoch = 0;
const conversationPage = ref<{ next_cursor: number | null; has_more: boolean }>({
  next_cursor: null,
  has_more: false,
});
const conversationNames = ref<Record<string, string>>({});
const conversationDrafts: Record<string, string> = {};
const chatAssistant = ref<InstanceType<typeof ChatAssistant> | null>(null),
  chatAiBusy = ref(false);
let sessionGeneration = 0;
const pendingFile = ref<File | null>(null),
  fileError = ref(''),
  uploadedFileId = ref(''),
  fileTempId = ref('');
watch(selected, () => {
  pendingFile.value = null;
  fileError.value = '';
  uploadedFileId.value = '';
  fileTempId.value = '';
});
watch(
  scheduleForm,
  () => {
    if (modal.value === 'schedule') error.value = '';
  },
  { deep: true },
);
let prependingMessages = false;
watch(selected, (id, previous) => {
  chatAssistant.value?.cancel();
  if (previous) conversationDrafts[previous] = draft.value;
  draft.value = conversationDrafts[id] || '';
});
function normalizeConversation(c: any): Conversation {
  return {
    ...c,
    name:
      c.name ||
      conversationNames.value[c.public_id] ||
      (c.type === 'GROUP' ? '未命名群聊' : '私聊会话'),
    unread_count: Number(c.unread_count || 0),
  };
}
function normalizeMessage(message: any): Message {
  const sender = message.sender || { public_id: 'unknown', nickname: '未知成员' };
  return { ...message, sender: { ...sender, nickname: displayUserName(sender, '未知成员') } };
}
const requestStatus = (status: string) =>
  ({
    PENDING: '待处理',
    ACCEPTED: '已接受',
    REJECTED: '已拒绝',
    CANCELLED: '已撤销',
    CANCELED: '已撤销',
  })[status] || status;
const visibleRequests = computed(
  () =>
    requests.value[requestTab.value]?.filter((r: any) =>
      (r.sender?.nickname + r.sender?.public_id + r.message).includes(requestKeyword.value),
    ) || [],
);
async function showContact(f: User) {
  await action(async () => {
    contact.value = demo
      ? f
      : normalizeCurrentUser(await request(`/users/${encodeURIComponent(f.public_id)}`));
    modal.value = 'contact';
  });
}
async function deleteContact() {
  await action(async () => {
    if (!contact.value) return;
    if (!demo)
      await request(`/friends/${encodeURIComponent(contact.value.public_id)}`, {
        method: 'DELETE',
      });
    friends.value = friends.value.filter((f) => f.public_id !== contact.value!.public_id);
    modal.value = '';
    notify('好友已删除');
  });
}
async function cancelRequest(r: any) {
  await action(async () => {
    if (!demo) {
      await request(`/friends/requests/${encodeURIComponent(r.request_public_id)}`, {
        method: 'DELETE',
      });
      requests.value = await request('/friends/requests');
    } else r.status = 'CANCELLED';
    notify('好友申请已撤销');
  });
}
async function moreConversations() {
  await action(async () => {
    const page = await request(`/conversations?cursor=${conversationPage.value.next_cursor}`);
    conversations.value.push(...page.items.map(normalizeConversation));
    conversationPage.value = page;
  });
}
async function enterGroup(group: { public_id: string; name: string }) {
  if (!demo) await load();
  if (!conversations.value.some((c) => c.public_id === group.public_id))
    conversations.value.unshift({ ...group, type: 'GROUP', unread_count: 0 });
  await openConversation(group.public_id);
}
async function clearMessages() {
  await action(async () => {
    if (!demo) await request(`/conversations/${selected.value}/messages`, { method: 'DELETE' });
    history.value[selected.value] = [];
    messagePages.value[selected.value] = { next_cursor: null, has_more: false };
    modal.value = '';
    notify('已清空当前账号的消息视图');
  });
}
function groupDetails() {
  router.push({ path: '/groups', state: { activeGroup: selected.value } });
}
function resetSession() {
  sessionGeneration++;
  clearTokens();
  sseController?.abort();
  clearTimeout(reconnectTimer);
  connectEpoch++;
  selected.value = '';
  history.value = {};
  user.value = { public_id: '', nickname: '' };
  conversations.value = [];
  friends.value = [];
  schedules.value = [];
  jobs.value = [];
  requests.value = { incoming: [], outgoing: [] };
  conversationNames.value = {};
  for (const key of Object.keys(conversationDrafts)) delete conversationDrafts[key];
  draft.value = '';
  aiInput.value = '';
  aiOutput.value = '';
  profile.value = { nickname: '', email: '', ai_analysis_enabled: false };
  go('/login');
}
async function getFile(m: Message) {
  await action(async () => {
    if (!m.file?.public_id) throw new Error('暂时无法下载此文件，请稍后重试');
    await downloadFile(m.file.public_id, m.file.original_filename);
  });
}
async function messageCandidates() {
  await action(async () => {
    const data = demo
      ? { items: currentMessages.value.map((m) => ({ content: m.content })) }
      : await request(
          '/ai/jobs/message-candidates' +
            (selected.value ? '?conversation_public_id=' + selected.value : ''),
        );
    candidates.value = data.items || [];
    pickedCandidates.value = [];
    modal.value = 'candidates';
  });
}
const candidates = ref<any[]>([]),
  pickedCandidates = ref<number[]>([]);
function useCandidates() {
  aiInput.value = pickedCandidates.value.map((i) => candidates.value[i].content).join('\n');
  modal.value = '';
}
function openAiSchedule() {
  chooseTool('schedule');
}
const tools = [
  {
    id: 'polish',
    name: '表达润色',
    text: '让每一句话，都恰到好处',
    icon: WandSparkles,
    color: 'coral',
  },
  {
    id: 'smart-reply',
    name: '智能回复',
    text: '接住话题，让交流更自然',
    icon: MessageCircle,
    color: 'purple',
  },
  {
    id: 'summarize',
    name: '内容总结',
    text: '提炼重点，不错过好想法',
    icon: FileText,
    color: 'green',
  },
  {
    id: 'translate',
    name: '智能翻译',
    text: '跨越语言，拉近彼此距离',
    icon: Languages,
    color: 'blue',
  },
];
const tool = ref('polish'),
  aiInput = ref(''),
  aiOutput = ref(''),
  model = ref(''),
  targetLanguage = ref('en'),
  aiBusy = ref(false),
  month = ref(new Date()),
  chosenDate = ref(new Date().toLocaleDateString('sv-SE'));
const modelCapability = computed(
  () =>
    ({
      polish: 'POLISH',
      translate: 'TRANSLATION',
      'smart-reply': 'SMART_REPLY',
      summarize: 'CHAT_SUMMARY',
      schedule: 'SCHEDULE_EXTRACTION',
    })[tool.value] || 'POLISH',
);
const availableModels = computed(() =>
  models.value.filter(
    (m) =>
      !m.capabilities?.length ||
      m.capabilities.includes(modelCapability.value) ||
      m.capabilities.includes('text'),
  ),
);
watch([tool, availableModels], () => {
  if (!availableModels.value.some((m) => m.option_code === model.value))
    model.value = availableModels.value[0]?.option_code || '';
});
let aiController: AbortController | undefined,
  sseController: AbortController | undefined,
  toastTimer: ReturnType<typeof setTimeout>;
const messagePages = ref<Record<string, { next_cursor: number | null; has_more: boolean }>>({});
let lastFocus: HTMLElement | null = null;
watch(modal, async (value) => {
  if (value === 'friend') {
    switchContacts('lookup');
    lookupType.value = 'USERNAME';
    userCode.value = '';
  }
  if (value) {
    lastFocus = document.activeElement as HTMLElement;
    await nextTick();
    document.querySelector<HTMLElement>('.modal input,.modal button')?.focus();
  } else lastFocus?.focus();
});
function trapFocus(e: KeyboardEvent) {
  if (e.key !== 'Tab') return;
  const nodes = Array.from(
    document.querySelectorAll<HTMLElement>(
      '.modal button:not(:disabled),.modal input,.modal textarea,.modal select,.modal a[href]',
    ),
  );
  const first = nodes[0],
    last = nodes[nodes.length - 1];
  if (e.shiftKey && document.activeElement === first) {
    e.preventDefault();
    last?.focus();
  } else if (!e.shiftKey && document.activeElement === last) {
    e.preventDefault();
    first?.focus();
  }
}
function expired() {
  resetSession();
  notify('登录已过期，请重新登录');
}
async function olderMessages() {
  const id = selected.value,
    p = messagePages.value[id];
  if (!p?.has_more) return;
  await action(async () => {
    const page = await request(
      `/conversations/${encodeURIComponent(id)}/messages?before_message_id=${p.next_cursor}`,
    );
    const el = document.querySelector('.chat-messages'),
      oldHeight = el?.scrollHeight || 0,
      oldTop = el?.scrollTop || 0;
    prependingMessages = true;
    history.value[id] = [
      ...page.items.map(normalizeMessage).reverse(),
      ...(history.value[id] || []),
    ];
    messagePages.value[id] = page;
    await nextTick();
    if (el && selected.value === id) el.scrollTop = oldTop + el.scrollHeight - oldHeight;
    prependingMessages = false;
  });
}

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
const {
  pinned: pinnedChats,
  removed: removedChats,
  hidden: hiddenMessages,
  save: saveChatPreferences,
} = useChatPreferences(user);
const removalTarget = ref<Conversation | null>(null);
const signature = (c: Conversation) =>
  c.latest_message?.public_id ||
  c.latest_message?.created_at ||
  c.latest_message?.content ||
  'empty';
const visibleConversations = computed(() =>
  conversations.value.filter((c) => removedChats.value[c.public_id] !== signature(c)),
);
function pinConversation(c: Conversation) {
  pinnedChats.value = pinnedChats.value.includes(c.public_id)
    ? pinnedChats.value.filter((id) => id !== c.public_id)
    : [...pinnedChats.value, c.public_id];
  if (saveChatPreferences() === false) notify('浏览器无法保存设置，本次置顶仅在当前页面有效');
}
function removeConversation() {
  if (!removalTarget.value) return;
  const id = removalTarget.value.public_id;
  removedChats.value[id] = signature(removalTarget.value);
  if (selected.value === id) selected.value = '';
  removalTarget.value = null;
  modal.value = '';
  if (saveChatPreferences() === false) notify('浏览器无法保存设置，本次移除仅在当前页面有效');
}
function hideMessages(ids: string[]) {
  hiddenMessages.value[selected.value] = [
    ...new Set([...(hiddenMessages.value[selected.value] || []), ...ids]),
  ];
  if (saveChatPreferences() === false) notify('浏览器无法保存设置，本次隐藏仅在当前页面有效');
}
function forwarded(target: string, message: Message) {
  (history.value[target] ||= []).push(normalizeMessage(message));
  const c = conversations.value.find((c) => c.public_id === target);
  if (c)
    c.latest_message = {
      public_id: message.public_id,
      content: message.content || '[文件] ' + (message.file?.original_filename || '附件'),
      created_at: message.created_at,
    };
}
const filteredConversations = computed(() =>
  visibleConversations.value
    .filter(
      (c) =>
        (conversationFilter.value === 'ALL' || c.type === conversationFilter.value) &&
        ((c.name || '').includes(query.value) || c.latest_message?.content?.includes(query.value)),
    )
    .sort(
      (a, b) =>
        Number(pinnedChats.value.includes(b.public_id)) -
        Number(pinnedChats.value.includes(a.public_id)),
    ),
);
const filteredFriends = computed(() =>
  friends.value.filter((f) =>
    (f.nickname + f.username).toLowerCase().includes(query.value.toLowerCase()),
  ),
);
const activeConversation = computed(() =>
    conversations.value.find((c) => c.public_id === selected.value),
  ),
  currentMessages = computed(() =>
    (history.value[selected.value] || []).filter(
      (m) => !(hiddenMessages.value[selected.value] || []).includes(m.public_id),
    ),
  ),
  unread = computed(() => conversations.value.reduce((n, c) => n + c.unread_count, 0)),
  pageTitle = computed(
    () =>
      nav.find((n) => n.path === route.path)?.label ||
      (route.path === '/settings' ? '个人设置' : '欢迎回来'),
  );
watch(
  () => currentMessages.value.length,
  async () => {
    if (prependingMessages) return;
    await nextTick();
    const el = document.querySelector('.chat-messages');
    if (el) el.scrollTop = el.scrollHeight;
  },
);
function notify(text: string) {
  toast.value = text;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (toast.value = ''), 3500);
}
async function action(fn: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    await fn();
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
function go(path: string) {
  mobile.value = false;
  query.value = '';
  router.push(path);
}
async function load() {
  if (demo) return;
  const generation = sessionGeneration;
  loading.value = true;
  error.value = '';
  try {
    const me = await request('/users/me');
    if (generation !== sessionGeneration) return;
    user.value = normalizeCurrentUser(me);
    if (route.path === '/settings' && !profile.value.nickname)
      profile.value = {
        nickname: user.value.nickname,
        email: user.value.email || '',
        ai_analysis_enabled: user.value.ai_analysis_enabled ?? false,
      };
    const from = new Date(month.value.getFullYear(), month.value.getMonth(), 1).toISOString(),
      to = new Date(month.value.getFullYear(), month.value.getMonth() + 1, 1).toISOString();
    const results = await Promise.allSettled([
      request('/conversations'),
      request('/friends'),
      request(
        `/schedules?start_time=${encodeURIComponent(from)}&end_time=${encodeURIComponent(to)}`,
      ),
      request('/ai/jobs'),
      request('/friends/requests'),
      request('/ai/models'),
    ]);
    const setters = [
      (d: any) => {
        conversations.value = (d.items || []).map(normalizeConversation);
        conversationPage.value = d;
      },
      (d: any) =>
        (friends.value = d.map((person: User) => ({
          ...person,
          nickname: displayUserName(person, '好友'),
        }))),
      (d: any) => (schedules.value = d),
      (d: any) => (jobs.value = d.items),
      (d: any) => (requests.value = d),
      (d: any) => (models.value = d.models),
    ];
    if (generation !== sessionGeneration) return;
    results.forEach((r, i) => {
      if (r.status === 'fulfilled') setters[i](r.value);
      else error.value += `${r.reason.message}；`;
    });
    model.value ||=
      import.meta.env.VITE_DEFAULT_MODEL || availableModels.value[0]?.option_code || '';
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}
function connect() {
  if (demo || !sessionStorage.getItem('access_token')) return;
  const epoch = ++connectEpoch;
  clearTimeout(reconnectTimer);
  sseController?.abort();
  sseController = new AbortController();
  realtime.value = '连接中';
  subscribe(async (e) => {
    if (e.type === 'CONNECTION_ESTABLISHED') {
      realtime.value = '实时在线';
      reconnectAttempt = 0;
    }
    if (e.type === 'MESSAGE_CREATED') {
      await load();
      if (selected.value && route.path === '/messages') {
        try {
          const id = selected.value;
          const page = await request(`/conversations/${encodeURIComponent(id)}/messages`);
          history.value[id] = page.items.map(normalizeMessage).reverse();
          messagePages.value[id] = page;
          const active = conversations.value.find((c) => c.public_id === id);
          if (active) active.unread_count = 0;
        } catch (e) {
          notify((e as Error).message);
        }
      }
    }
  }, sseController.signal).catch((e) => {
    if (
      e.name !== 'AbortError' &&
      epoch === connectEpoch &&
      sessionStorage.getItem('access_token')
    ) {
      realtime.value = '正在重连';
      reconnectTimer = setTimeout(connect, Math.min(30000, 1000 * 2 ** reconnectAttempt++));
    }
  });
}
async function authenticate() {
  await action(async () => {
    const chosenNickname = login.value.nickname.trim() || login.value.username.trim();
    let nicknameError = '';
    if (
      register.value &&
      !demo &&
      !/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/.test(
        login.value.password,
      )
    )
      throw new Error('密码至少 8 位，须含大小写字母、数字与 @$!%*?& 中的特殊字符，仅支持这些字符');
    if (!demo) {
      const { username, password, email } = login.value;
      tokens(
        await post(
          register.value ? '/auth/register' : '/auth/login',
          register.value ? { username, password, email } : { username, password },
        ),
      );
      if (register.value) {
        try {
          await post('/users/me', { nickname: chosenNickname }, 'PATCH');
        } catch (e) {
          nicknameError = (e as Error).message;
        }
      }
    } else {
      user.value = { ...seed.me, nickname: register.value ? chosenNickname : login.value.username };
      conversations.value = structuredClone(seed.conversations);
      friends.value = structuredClone(seed.friends);
      history.value = structuredClone(seed.messages);
      schedules.value = structuredClone(seed.schedules);
    }
    login.value.password = '';
    await router.push(nicknameError ? '/settings' : '/');
    await load();
    connect();
    if (nicknameError) {
      profile.value.nickname = chosenNickname;
      error.value = '账号已创建，但昵称保存失败，请在个人设置中重试：' + nicknameError;
    }
  });
}
async function logout() {
  await action(async () => {
    try {
      if (!demo) await post('/auth/logout', {});
    } finally {
      modal.value = '';
      resetSession();
    }
  });
}
async function openConversation(id: string) {
  if (removedChats.value[id]) {
    delete removedChats.value[id];
    saveChatPreferences();
  }
  selected.value = id;
  go('/messages');
  if (demo) {
    const c = conversations.value.find((c) => c.public_id === id);
    if (c) c.unread_count = 0;
  }
  if (!demo)
    await action(async () => {
      const page = await request(`/conversations/${encodeURIComponent(id)}/messages`);
      history.value[id] = page.items.map(normalizeMessage).reverse();
      messagePages.value[id] = page;
      const c = conversations.value.find((c) => c.public_id === id);
      if (c) c.unread_count = 0;
    });
}
async function send() {
  const content = draft.value.trim(),
    id = selected.value;
  if (!content || !id || chatAiBusy.value) return;
  await action(async () => {
    const msg: Message = demo
      ? {
          public_id: `demo-${Date.now()}`,
          content,
          sender: user.value,
          created_at: new Date().toISOString(),
        }
      : await post(`/conversations/${encodeURIComponent(id)}/messages`, {
          content,
          media_type: 'TEXT',
          temp_id: crypto.randomUUID(),
        });
    (history.value[id] ||= []).push({ ...msg, sender: msg.sender || user.value });
    draft.value = '';
    const c = conversations.value.find((c) => c.public_id === id);
    if (c) c.latest_message = { content };
  });
}
async function chatWith(f: User) {
  await action(async () => {
    let id: string;
    if (demo) {
      let c = conversations.value.find((c) => c.name === f.nickname);
      if (!c) {
        c = {
          public_id: `demo-conv-${Date.now()}`,
          name: f.nickname,
          type: 'PRIVATE',
          unread_count: 0,
        };
        conversations.value.unshift(c);
      }
      id = c.public_id;
    } else {
      id = await post('/conversations', { target_user_public_id: f.public_id });
      conversationNames.value[id] = f.nickname;
      await load();
      history.value[id] = (await request(`/conversations/${encodeURIComponent(id)}/messages`)).items
        .map(normalizeMessage)
        .reverse();
    }
    selected.value = id;
    go('/messages');
  });
}
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
    const saved: Schedule = demo
      ? { ...data, public_id: editing.value || `demo-sch-${Date.now()}` }
      : await post(
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
    if (!demo)
      await request(`/schedules/${encodeURIComponent(editing.value)}`, { method: 'DELETE' });
    schedules.value = schedules.value.filter((s) => s.public_id !== editing.value);
    modal.value = '';
    notify('日程已删除');
  });
}
async function addFriend() {
  await action(async () => {
    if (!friendForm.value.target_user_public_id) throw new Error('请先选择一位联系人');
    if (!demo) await post('/friends/requests', friendForm.value);
    else
      requests.value.outgoing.push({
        request_public_id: `demo-${Date.now()}`,
        message: friendForm.value.message,
        status: 'PENDING',
      });
    if (!demo) requests.value = await request('/friends/requests');
    modal.value = '';
    notify('好友申请已发送');
  });
}
async function acceptRequest(r: any, accept: boolean) {
  await action(async () => {
    if (!demo) {
      if (accept)
        await post(`/friends/requests/${encodeURIComponent(r.request_public_id)}/accept`, {});
      else
        await request(`/friends/requests/${encodeURIComponent(r.request_public_id)}`, {
          method: 'DELETE',
        });
      await load();
    } else {
      requests.value.incoming = requests.value.incoming.filter((x: any) => x !== r);
      if (accept) friends.value.push(r.sender);
    }
    notify(accept ? '已添加好友' : '已拒绝申请');
  });
}
async function createGroup() {
  await action(async () => {
    const d = demo
      ? { public_id: `demo-group-${Date.now()}` }
      : await post('/groups', {
          name: groupForm.value.name,
          member_public_ids: groupForm.value.members,
        });
    conversations.value.unshift({
      public_id: d.public_id,
      name: groupForm.value.name,
      type: 'GROUP',
      unread_count: 0,
    });
    modal.value = '';
    selected.value = d.public_id;
    go('/messages');
  });
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement,
    file = input.files?.[0];
  input.value = '';
  if (!file || !selected.value || busy.value) return;
  fileError.value = '';
  pendingFile.value = null;
  uploadedFileId.value = '';
  if (!file.size || file.size > 1024 ** 3) {
    fileError.value = file.size ? '文件不能超过 1 GB，请重新选择。' : '文件为空，请重新选择。';
    return;
  }
  pendingFile.value = file;
  uploadedFileId.value = '';
  fileTempId.value = crypto.randomUUID();
}
const fileSize = (size: number) =>
  size < 1024
    ? `${size} B`
    : size < 1024 ** 2
      ? `${(size / 1024).toFixed(1)} KB`
      : `${(size / 1024 ** 2).toFixed(1)} MB`;
async function sendFile() {
  const file = pendingFile.value;
  if (!file || !selected.value || busy.value) return;
  const id = selected.value;
  const generation = sessionGeneration;
  const tempId = fileTempId.value;
  busy.value = true;
  fileError.value = '';
  try {
    if (demo) {
      notify('演示模式不上传文件，请连接后端后使用');
      return;
    }
    const form = new FormData();
    form.append('file', file);
    form.append('conversation_public_id', id);
    const f = uploadedFileId.value
      ? { public_id: uploadedFileId.value }
      : await request('/files', { method: 'POST', body: form });
    if (generation !== sessionGeneration) return;
    if (selected.value === id) uploadedFileId.value = f.public_id;
    const msg = await post(`/conversations/${encodeURIComponent(id)}/messages`, {
      media_type: 'FILE',
      file_public_id: f.public_id,
      temp_id: tempId,
    });
    if (generation !== sessionGeneration) return;
    (history.value[id] ||= []).push({ ...msg, sender: msg.sender || user.value });
    if (pendingFile.value === file) {
      pendingFile.value = null;
      uploadedFileId.value = '';
    }
  } catch (e) {
    if (selected.value === id) fileError.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function saveProfile() {
  await action(async () => {
    if (!demo) user.value = normalizeCurrentUser(await post('/users/me', profile.value, 'PATCH'));
    else Object.assign(user.value, profile.value);
    notify('个人资料已保存');
  });
}
function chooseTool(id: string) {
  tool.value = id;
  aiOutput.value = '';
  go('/ai');
}
function summarizeChat() {
  chatAssistant.value?.run('summarize');
}
function polishDraft() {
  chatAssistant.value?.run('polish');
}
function returnToChat() {
  draft.value = aiOutput.value;
  go('/messages');
}
async function runAi() {
  if (aiBusy.value || !aiInput.value.trim()) return;
  aiBusy.value = true;
  aiOutput.value = '';
  error.value = '';
  aiController = new AbortController();
  try {
    if (demo)
      aiOutput.value = `【演示结果 · 未调用 AI 服务】\n${tool.value === 'translate' ? 'Example translation: Looking forward to connecting with you.' : tool.value === 'polish' ? '示例表达：很期待与你交流这个想法，我们一起找个合适的时间聊聊吧。' : tool.value === 'smart-reply' ? '示例回复：听起来很棒！有什么需要提前准备的吗？' : '示例总结：明确沟通主题，确认参与人员，并约定下一步安排。'}`;
    else {
      if (!model.value) throw new Error('暂无可用模型，请检查后端模型配置');
      const v = aiInput.value,
        body =
          tool.value === 'polish'
            ? { message: v }
            : tool.value === 'smart-reply'
              ? {
                  message: v,
                  ...(selected.value
                    ? { conversation_public_id: selected.value }
                    : { conversation_history: [] }),
                }
              : tool.value === 'summarize'
                ? { content: v, summary_type: 'chat', target_length: 'short' }
                : { text: v, target_language: targetLanguage.value };
      await stream(
        `/ai/interactions/${tool.value}`,
        { ...body, model_option_code: model.value },
        (chunk) => (aiOutput.value += chunk),
        aiController.signal,
      );
    }
  } catch (e) {
    if ((e as Error).name !== 'AbortError') error.value = (e as Error).message;
  } finally {
    aiBusy.value = false;
  }
}
function stopAi() {
  aiController?.abort();
}
let scheduleRequest = 0;
const schedulesLoading = ref(false),
  scheduleError = ref('');
async function moveMonth(n: number) {
  month.value = new Date(month.value.getFullYear(), month.value.getMonth() + n, 1);
  if (demo) return;
  const sequence = ++scheduleRequest,
    generation = sessionGeneration;
  const from = new Date(month.value.getFullYear(), month.value.getMonth(), 1).toISOString();
  const to = new Date(month.value.getFullYear(), month.value.getMonth() + 1, 1).toISOString();
  schedulesLoading.value = true;
  scheduleError.value = '';
  try {
    const result = await request(
      `/schedules?start_time=${encodeURIComponent(from)}&end_time=${encodeURIComponent(to)}`,
    );
    if (sequence === scheduleRequest && generation === sessionGeneration) schedules.value = result;
  } catch (e) {
    if (sequence === scheduleRequest && generation === sessionGeneration)
      scheduleError.value = (e as Error).message;
  } finally {
    if (sequence === scheduleRequest) schedulesLoading.value = false;
  }
}
const time = (s: string) =>
  new Date(s).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
function safeUrl(url?: string) {
  if (!url) return undefined;
  try {
    const u = new URL(url, location.origin);
    return ['https:', 'http:'].includes(u.protocol) ? u.href : undefined;
  } catch {
    return undefined;
  }
}
watch(
  () => route.path,
  () => {
    if (route.path !== '/messages') chatAssistant.value?.cancel();
    error.value = '';
    query.value = '';
    if (route.path === '/settings')
      profile.value = {
        nickname: user.value.nickname,
        email: user.value.email || '',
        ai_analysis_enabled: user.value.ai_analysis_enabled ?? false,
      };
    if (!demo && !sessionStorage.getItem('access_token') && route.path !== '/login') go('/login');
  },
);
onMounted(async () => {
  window.addEventListener('auth-expired', expired);
  if (!demo && !sessionStorage.getItem('access_token')) {
    go('/login');
    return;
  }
  await load();
  connect();
});
onUnmounted(() => {
  window.removeEventListener('auth-expired', expired);
  aiController?.abort();
  sseController?.abort();
  clearTimeout(toastTimer);
  clearTimeout(reconnectTimer);
  connectEpoch++;
});
</script>

<template>
  <div class="app-shell" @keydown.esc="mobile = false">
    <button
      v-if="mobile"
      class="sidebar-backdrop"
      aria-label="收起侧边栏"
      @click="mobile = false"
    />
    <aside id="main-sidebar" :class="['sidebar', { expanded: mobile }]">
      <a class="brand" href="#/"
        ><img src="/favicon.svg" alt="共鸣标志" /><span>共鸣<small>AI SOCIAL HUB</small></span></a
      >
      <div class="workspace-label">你的社交空间 <span>PERSONAL</span></div>
      <nav aria-label="主要导航">
        <button
          v-for="n in nav"
          :key="n.path"
          :class="{ active: route.path === n.path }"
          @click="go(n.path)"
        >
          <component :is="n.icon" :size="20" /><span>{{ n.label }}</span
          ><b v-if="n.path === '/messages' && unread" class="count">{{ unread }}</b
          ><small v-if="n.path === '/ai'" class="new-label">NEW</small>
        </button>
      </nav>
      <div class="sidebar-promo">
        <Sparkles :size="24" />
        <h4>让连接，多一点温度</h4>
        <p>有 AI 相伴，让每一次表达<br />都更贴近你的心意。</p>
        <button @click="go('/ai')">探索 AI 助手<ArrowUpRight :size="16" /></button>
      </div>
      <div class="sidebar-bottom">
        <button :class="{ active: route.path === '/settings' }" @click="go('/settings')">
          <Settings :size="19" />个人设置</button
        ><button @click="modal = 'help'">
          <Flower2 :size="19" />使用帮助<ArrowUpRight :size="15" />
        </button>
        <div class="sidebar-user">
          <span class="avatar self">{{ user.nickname.slice(0, 1) || '访' }}</span
          ><span
            ><strong>{{ user.nickname || '访客' }}</strong
            ><small>{{ demo ? '演示体验中' : '个人账户' }}</small></span
          ><button aria-label="退出登录" @click="logout"><LogOut :size="17" /></button>
        </div>
      </div>
    </aside>
    <main>
      <header class="topbar">
        <div class="breadcrumb">
          <button
            class="mobile-toggle icon-button"
            aria-label="展开菜单"
            aria-controls="main-sidebar"
            :aria-expanded="mobile"
            @click="mobile = !mobile"
          >
            <Menu :size="20" /></button
          ><span>我的工作台</span><ChevronRight :size="14" /><strong>{{ pageTitle }}</strong>
        </div>
        <div class="top-actions">
          <span class="connection">{{ demo ? '演示模式' : realtime }}</span
          ><button class="icon-button" aria-label="好友申请" @click="modal = 'requests'">
            <Bell :size="20" /></button
          ><button class="avatar self small" aria-label="个人设置" @click="go('/settings')">
            {{ user.nickname.slice(0, 1) || '访' }}
          </button>
        </div>
      </header>
      <div v-if="error && !modal" class="error" role="alert">
        {{ redactIdentifiers(error)
        }}<button aria-label="关闭错误" @click="error = ''"><X :size="16" /></button>
      </div>
      <div v-if="loading" class="loading" role="status">正在加载你的空间…</div>
      <div v-if="route.path === '/login'" class="login-wrap">
        <section class="card login-card">
          <span class="eyebrow">WELCOME TO RESONANCE</span>
          <h1>{{ register ? '开启新的连接' : '欢迎回到共鸣' }}</h1>
          <p>让交流自然发生，让彼此更加靠近。</p>
          <form @submit.prevent="authenticate">
            <label>用户名<input v-model="login.username" autocomplete="username" required /></label
            ><label v-if="register"
              >昵称（选填）<input
                v-model="login.nickname"
                autocomplete="nickname"
                maxlength="40"
                placeholder="留空则使用用户名" /></label
            ><label v-if="register">邮箱<input v-model="login.email" type="email" required /></label
            ><label
              >密码<input
                v-model="login.password"
                type="password"
                :autocomplete="register ? 'new-password' : 'current-password'"
                required
                :minlength="register && !demo ? 8 : 1" /></label
            ><button class="primary" :disabled="busy">
              {{ register ? '注册并登录' : '登录' }}<ArrowRight :size="17" />
            </button>
          </form>
          <button class="text-button" @click="register = !register">
            {{ register ? '已有账号？返回登录' : '还没有账号？立即注册' }}
          </button>
          <p v-if="register && !demo" class="field-note">
            密码至少 8 位，含大小写字母、数字及 @$!%*?& 中的特殊字符。
          </p>
          <p v-if="demo" class="muted">演示模式：填写任意用户名与密码。</p>
        </section>
      </div>
      <div v-else class="page-content">
        <template v-if="route.path === '/'">
          <div class="page-heading">
            <div>
              <span class="eyebrow">YOUR DAILY CONNECTION</span>
              <h1>你好，{{ user.nickname }} <span class="greeting-dot">✦</span></h1>
              <p>每一次真诚的交流，都值得被好好对待。</p>
            </div>
            <span class="date-chip"><CalendarDays :size="16" />{{ dateLabel }}</span>
          </div>
          <section class="hero">
            <img src="/images/social-hero.png" alt="珊瑚橙与淡紫色对话气泡相连的立体插画" />
            <div class="hero-copy">
              <span class="hero-tag"><Sparkles :size="13" />AI 让沟通更有温度</span>
              <h2>好的连接，<br />从一句话开始。</h2>
              <p>把想说的话说得更好，把重要的人放在心上。<br />你的 AI 社交伙伴，随时在这里。</p>
              <button class="primary" @click="go('/messages')">
                开启一段对话<ArrowRight :size="17" />
              </button>
            </div>
            <span class="hero-caption">A LITTLE CLOSER, EVERY DAY.</span>
          </section>
          <div class="stats-grid">
            <button class="stat card" @click="go('/messages')">
              <span class="stat-icon coral"><MessagesSquare :size="21" /></span>
              <div>
                <span>未读消息</span><strong>{{ unread }}<small>条</small></strong>
              </div>
              <span class="stat-foot">别让关心等太久<ArrowUpRight :size="15" /></span></button
            ><button class="stat card" @click="go('/friends')">
              <span class="stat-icon purple"><Users :size="21" /></span>
              <div>
                <span>我的好友</span><strong>{{ friends.length }}<small>位</small></strong>
              </div>
              <span class="stat-foot">每一份连接都珍贵<ArrowUpRight :size="15" /></span></button
            ><button class="stat card" @click="go('/schedules')">
              <span class="stat-icon green"><CalendarDays :size="21" /></span>
              <div>
                <span>今日日程</span><strong>{{ todaySchedules.length }}<small>项</small></strong>
              </div>
              <span class="stat-foot">为重要的事留出时间<ArrowUpRight :size="15" /></span></button
            ><button class="stat card" @click="go('/ai')">
              <span class="stat-icon blue"><Sparkles :size="21" /></span>
              <div>
                <span>AI 已完成任务</span
                ><strong
                  >{{ jobs.filter((j) => j.status === 'COMPLETED').length
                  }}<small>次</small></strong
                >
              </div>
              <span class="stat-foot">一点智能，一点轻松<ArrowUpRight :size="15" /></span>
            </button>
          </div>
          <div class="dashboard-columns">
            <section class="card recent">
              <div class="section-heading">
                <h3>最近的对话 <span class="subtle">RECENT CHATS</span></h3>
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
                    >{{ c.name
                    }}<span v-if="c.type === 'GROUP'" class="group-label">群聊</span></strong
                  ><small>{{ c.latest_message?.content || '开启你们的第一段对话' }}</small></span
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
              <div class="agenda-date">{{ dateLabel }}<span>把日子过成喜欢的样子</span></div>
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
              <h3>沟通的小帮手 <span class="subtle">MADE FOR YOUR EVERYDAY</span></h3>
              <span class="muted helper-caption">把复杂留给 AI，把真诚留给你</span>
            </div>
            <div class="tools-grid">
              <button
                v-for="t in tools"
                :key="t.id"
                class="tool-card card"
                @click="chooseTool(t.id)"
              >
                <span :class="['tool-icon', t.color]"><component :is="t.icon" :size="23" /></span
                ><ArrowUpRight class="tool-arrow" :size="17" />
                <h4>{{ t.name }}</h4>
                <p>{{ t.text }}</p>
              </button>
            </div>
          </section>
          <footer>
            <span><Flower2 :size="15" />在共鸣，让每一份心意都有回响。</span
            ><span>AI SOCIAL HUB · CONNECT WITH CARE</span>
          </footer>
        </template>
        <GroupsPage
          v-else-if="route.path === '/groups' && user.public_id"
          :user="user"
          :friends="friends"
          @chat="enterGroup"
          @changed="load"
          @notify="notify"
        />
        <InsightsPage v-else-if="route.path === '/insights'" />
        <template v-else-if="route.path === '/messages'">
          <div class="page-heading">
            <div>
              <h1>每一句，都有回响</h1>
              <p>与朋友聊聊，让灵感与心意自由流动。</p>
            </div>
            <button class="primary" @click="modal = 'group'"><Plus :size="17" />发起群聊</button>
          </div>
          <section class="chat-layout card">
            <div class="chat-list">
              <div class="conversation-tabs">
                <button
                  v-for="f in [
                    { id: 'ALL', name: '全部' },
                    { id: 'PRIVATE', name: '私聊' },
                    { id: 'GROUP', name: '群聊' },
                  ]"
                  :key="f.id"
                  :class="{ active: conversationFilter === f.id }"
                  @click="conversationFilter = f.id"
                >
                  {{ f.name }}
                </button>
              </div>
              <div class="search-field">
                <Search :size="17" /><input
                  v-model="query"
                  placeholder="搜索会话"
                  aria-label="搜索会话"
                />
              </div>
              <ConversationRow
                v-for="(c, i) in filteredConversations"
                :key="c.public_id"
                :conversation="c"
                :index="i"
                :selected="selected === c.public_id"
                :pinned="pinnedChats.includes(c.public_id)"
                @open="openConversation(c.public_id)"
                @pin="pinConversation(c)"
                @remove="
                  removalTarget = c;
                  modal = 'removeConversation';
                "
              />
              <p class="local-chat-note">置顶和移除仅在此浏览器保存</p>
              <div v-if="!filteredConversations.length" class="empty">暂无匹配的会话</div>
              <button
                v-if="conversationPage.has_more && !demo"
                class="text-button"
                :disabled="busy"
                @click="moreConversations"
              >
                加载更多会话
              </button>
            </div>
            <div v-if="activeConversation" class="chat-panel">
              <div class="chat-heading">
                <div>
                  <h3>{{ activeConversation.name }}</h3>
                  <small>{{ activeConversation.type === 'GROUP' ? '群组会话' : '私聊会话' }}</small>
                  <button
                    v-if="activeConversation.type === 'GROUP'"
                    class="text-button"
                    @click="groupDetails"
                  >
                    <Users :size="14" />群资料与成员
                  </button>
                </div>
                <button class="text-button" :disabled="chatAiBusy" @click="summarizeChat">
                  <Sparkles :size="16" />总结对话
                </button>
                <button
                  class="icon-button"
                  aria-label="清空我的消息视图"
                  title="清空我的消息视图"
                  @click="modal = 'clearMessages'"
                >
                  <X :size="16" />
                </button>
              </div>
              <div class="chat-messages">
                <button
                  v-if="messagePages[selected]?.has_more"
                  class="text-button"
                  :disabled="busy"
                  @click="olderMessages"
                >
                  加载更早消息
                </button>
                <div class="chat-date">用心交流，从这里开始</div>
                <MessageActions
                  :key="selected"
                  :messages="currentMessages"
                  :user="user"
                  :conversation-id="selected"
                  :conversations="conversations"
                  @download="getFile"
                  @hide="hideMessages"
                  @notify="notify"
                  @sent="forwarded"
                />
              </div>
              <ChatAssistant
                :key="selected"
                ref="chatAssistant"
                v-model:draft="draft"
                :conversation-id="selected"
                :messages="currentMessages"
                :models="models"
                :user-id="user.public_id"
                @busy="chatAiBusy = $event"
              />
              <form class="composer" @submit.prevent="send">
                <section v-if="pendingFile" class="attachment-preview" aria-label="待发送文件">
                  <FileText :size="22" />
                  <div>
                    <strong>{{ pendingFile.name }}</strong
                    ><small>{{ fileSize(pendingFile.size) }} · 待发送，请核对文件</small>
                  </div>
                  <button
                    type="button"
                    class="icon-button"
                    aria-label="移除待发送文件"
                    :disabled="busy"
                    @click="
                      pendingFile = null;
                      uploadedFileId = '';
                      fileError = '';
                    "
                  >
                    <X :size="17" />
                  </button>
                  <button type="button" class="primary" :disabled="busy" @click="sendFile">
                    {{ busy ? '正在发送…' : '发送文件' }}
                  </button>
                </section>
                <p v-if="fileError" class="inline-error" role="alert">
                  {{ redactIdentifiers(fileError) }}
                </p>
                <textarea
                  v-model="draft"
                  :readonly="chatAiBusy"
                  placeholder="说点什么，让彼此更近一点…"
                  aria-label="消息内容"
                  @keydown.ctrl.enter.prevent="send"
                />
                <div>
                  <label class="icon-button upload" title="上传文件"
                    ><Paperclip :size="19" /><input
                      type="file"
                      :disabled="busy"
                      @change="upload"
                      aria-label="上传聊天文件" /></label
                  ><button
                    type="button"
                    class="text-button"
                    :disabled="chatAiBusy"
                    @click="polishDraft"
                  >
                    <Sparkles :size="16" />AI 润色</button
                  ><button
                    type="button"
                    class="text-button"
                    :disabled="chatAiBusy"
                    @click="chatAssistant?.run('smart-reply')"
                  >
                    回复建议</button
                  ><span class="muted">Ctrl + Enter 发送</span
                  ><button class="primary" :disabled="busy || chatAiBusy || !draft.trim()">
                    发送<Send :size="16" />
                  </button>
                </div>
              </form>
            </div>
            <div v-else class="empty chat-placeholder">
              <MessagesSquare :size="48" />
              <h3>选择一段对话</h3>
              <p>让今天的第一句问候，从这里开始。</p>
              <button class="primary" @click="go('/friends')">找好友聊聊</button>
            </div>
          </section>
        </template>
        <template v-else-if="route.path === '/friends'"
          ><div class="page-heading">
            <div>
              <h1>熟悉的人，温暖的连接</h1>
              <p>{{ friends.length }} 位好友，每一次相遇都值得珍惜。</p>
            </div>
            <div class="inline-actions">
              <button class="secondary" @click="modal = 'requests'">
                好友申请 {{ requests.incoming.length }}</button
              ><button class="primary" @click="modal = 'friend'">
                <Plus :size="17" />添加好友
              </button>
            </div>
          </div>
          <div class="search-field friends-search">
            <Search :size="18" /><input
              v-model="query"
              placeholder="搜索昵称或用户名"
              aria-label="搜索好友"
            />
          </div>
          <div class="friend-grid">
            <article v-for="(f, i) in filteredFriends" :key="f.public_id" class="card friend-card">
              <span :class="['avatar large', ['peach', 'lavender', 'sage', 'sand'][i % 4]]"
                ><img
                  v-if="safeUrl(f.avatar_url)"
                  :src="safeUrl(f.avatar_url)"
                  alt="好友头像"
                /><span v-else>{{ f.nickname.slice(-2) }}</span></span
              >
              <h3>{{ f.nickname }}</h3>
              <p>{{ f.username ? `@${redactIdentifiers(f.username)}` : '好友' }}</p>
              <button class="secondary" :disabled="busy" @click="chatWith(f)">
                <MessageCircle :size="16" />发消息
              </button>
              <button class="text-button friend-details" :disabled="busy" @click="showContact(f)">
                查看资料<ChevronRight :size="13" />
              </button>
            </article>
          </div>
          <div v-if="!filteredFriends.length" class="empty card">
            暂无匹配好友，点击「添加好友」发送申请。
          </div></template
        >
        <template v-else-if="route.path === '/schedules'"
          ><div class="page-heading">
            <div>
              <h1>把时间，留给重要的事</h1>
              <p>记录每一份约定，让生活从容一点。</p>
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
                <span
                  v-for="d in ['一', '二', '三', '四', '五', '六', '日']"
                  :key="d"
                  class="weekday"
                  >{{ d }}</span
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
                      schedules.some(
                        (s) => new Date(s.start_time).toLocaleDateString('sv-SE') === d.date,
                      )
                    "
                  />
                </button>
              </div>
              <p class="muted calendar-note" role="status">
                {{ schedulesLoading ? '正在更新日程…' : '带圆点的日期有已安排日程' }}
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
                <h3>留白，也是不错的安排</h3>
                <p>今天还没有日程，添加一份新约定吧。</p>
                <button class="secondary" @click="openSchedule()">添加日程</button>
              </div>
            </section>
          </div></template
        >
        <template v-else-if="route.path === '/ai'"
          ><div class="page-heading">
            <div>
              <span class="eyebrow">A LITTLE HELP, A BETTER CONNECTION</span>
              <h1>你的沟通灵感搭子</h1>
              <p>更好的表达，从一点点灵感开始。</p>
            </div>
            <span class="pill"><Sparkles :size="14" />AI WORKSPACE</span>
          </div>
          <div class="ai-tabs">
            <button
              :class="{ active: tool === 'schedule' }"
              :disabled="aiBusy"
              @click="openAiSchedule"
            >
              <CalendarDays :size="18" />日程提取
            </button>
            <button
              v-for="t in tools"
              :key="t.id"
              :class="{ active: tool === t.id }"
              :disabled="aiBusy"
              @click="chooseTool(t.id)"
            >
              <component :is="t.icon" :size="18" />{{ t.name }}
            </button>
          </div>
          <template v-if="tool === 'schedule'"
            ><label class="schedule-model-label"
              >提取模型<select v-model="model" aria-label="提取模型">
                <option v-if="demo" value="">演示模型</option>
                <option v-for="m in availableModels" :key="m.option_code" :value="m.option_code">
                  {{ m.display_name }}
                </option>
              </select></label
            ><ScheduleExtraction :model="model" @saved="load" @notify="notify"
          /></template>
          <div v-else class="ai-layout">
            <section class="card ai-editor">
              <div class="section-heading">
                <h3>{{ tools.find((t) => t.id === tool)?.name }}</h3>
                <select v-model="model" aria-label="选择模型">
                  <option v-if="demo" value="">演示模型</option>
                  <option v-for="m in availableModels" :key="m.option_code" :value="m.option_code">
                    {{ m.display_name }}
                  </option>
                  <option v-if="!demo && !availableModels.length" value="">暂无可用模型</option>
                </select>
              </div>
              <label for="ai-input">{{
                tool === 'smart-reply' ? '对方发来的消息' : '写下你想处理的内容'
              }}</label
              ><button
                v-if="tool === 'summarize'"
                class="text-button"
                :disabled="busy"
                @click="messageCandidates"
              >
                从我的消息中选择总结样本</button
              ><textarea
                id="ai-input"
                v-model="aiInput"
                placeholder="把想说的话放在这里，剩下的交给 AI…"
              />
              <div class="ai-editor-bottom">
                <select v-if="tool === 'translate'" v-model="targetLanguage" aria-label="目标语言">
                  <option value="en">翻译为英语</option>
                  <option value="zh">翻译为中文</option>
                  <option value="ja">翻译为日语</option></select
                ><span v-else class="muted">{{ aiInput.length }} 字</span
                ><button v-if="aiBusy" class="secondary" @click="stopAi">停止生成</button
                ><button v-else class="primary" :disabled="!aiInput.trim()" @click="runAi">
                  <Sparkles :size="16" />开始生成
                </button>
              </div>
            </section>
            <section class="card ai-result">
              <div class="section-heading">
                <h3>一点新灵感</h3>
                <span class="pill">{{ aiBusy ? '正在生成' : 'AI OUTPUT' }}</span>
              </div>
              <pre v-if="aiOutput">{{ aiOutput }}</pre>
              <div v-else class="empty">
                <Sparkles :size="38" />
                <h3>让想法，多一种表达</h3>
                <p>输入内容，选择工具，<br />你的沟通灵感即将出现。</p>
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
                <strong>{{
                  j.type === 'POLISH'
                    ? '表达润色'
                    : j.type === 'SCHEDULE_EXTRACTION'
                      ? '日程提取'
                      : j.type
                }}</strong
                ><small>{{ new Date(j.created_at).toLocaleString('zh-CN') }}</small>
                <p v-if="j.error_message">{{ redactIdentifiers(j.error_message) }}</p>
              </div>
              <span class="pill">{{
                j.status === 'COMPLETED'
                  ? '已完成'
                  : j.status === 'FAILED'
                    ? '失败'
                    : j.status === 'PROCESSING'
                      ? '处理中'
                      : j.status === 'PENDING'
                        ? '等待中'
                        : j.status
              }}</span>
            </div>
            <div v-if="!jobs.length" class="empty">暂无 AI 任务</div>
          </section></template
        >
        <template v-else-if="route.path === '/settings'"
          ><div class="page-heading">
            <div>
              <h1>做自己，就很好</h1>
              <p>管理你的个人资料与 AI 隐私偏好。</p>
            </div>
          </div>
          <section class="card settings-card">
            <div class="section-heading">
              <h3>个人资料</h3>
              <span class="avatar self">{{ user.nickname.slice(0, 1) }}</span>
            </div>
            <form @submit.prevent="saveProfile">
              <label>昵称<input v-model="profile.nickname" required maxlength="40" /></label
              ><label>邮箱<input v-model="profile.email" type="email" /></label>
              <div class="privacy">
                <div>
                  <strong>允许 AI 分析个人沟通偏好</strong>
                  <p>开启后，AI 可根据聊天内容生成个人画像，提供更贴合你的建议。</p>
                </div>
                <input
                  v-model="profile.ai_analysis_enabled"
                  type="checkbox"
                  aria-label="AI 分析授权"
                />
              </div>
              <button class="primary" :disabled="busy"><Check :size="16" />保存设置</button>
            </form>
          </section>
          <AccountSecurity :user="user" @changed="load" @notify="notify" @logout="resetSession"
        /></template>
      </div>
    </main>
    <Transition name="toast"
      ><div v-if="toast" class="toast" role="status">
        <Check :size="17" />{{ toast }}
      </div></Transition
    >
    <div
      v-if="modal"
      class="modal-overlay"
      @click.self="modal = ''"
      @keydown="trapFocus"
      @keydown.esc="modal = ''"
      tabindex="-1"
    >
      <section class="modal card" role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <button class="close icon-button" aria-label="关闭弹窗" @click="modal = ''">
          <X :size="21" />
        </button>
        <template v-if="modal === 'schedule'"
          ><h2 id="modal-title">{{ editing ? '编辑日程' : '新的一份约定' }}</h2>
          <form @submit.prevent="saveSchedule">
            <label>日程标题<input v-model="scheduleForm.title" required maxlength="200" /></label
            ><DateTimeField v-model="scheduleForm.start_time" label="开始时间" />
            <DateTimeField v-model="scheduleForm.end_time" label="结束时间" />
            <label>地点<input v-model="scheduleForm.location" maxlength="200" /></label
            ><label>备注<textarea v-model="scheduleForm.description" /></label>
            <div class="form-actions">
              <button
                v-if="editing"
                type="button"
                class="danger secondary"
                :disabled="busy"
                @click="removeSchedule"
              >
                删除此日程</button
              ><button class="primary" :disabled="busy">保存日程</button>
            </div>
          </form></template
        >
        <template v-else-if="modal === 'friend'"
          ><h2 id="modal-title">认识新的朋友</h2>
          <p>先查找并核对对方昵称，再发送好友申请。</p>
          <div class="conversation-tabs">
            <button :class="{ active: contactMode === 'lookup' }" @click="switchContacts('lookup')">
              查找用户</button
            ><button :class="{ active: contactMode === 'group' }" @click="switchContacts('group')">
              同群联系人
            </button>
          </div>
          <form v-if="contactMode === 'lookup'" @submit.prevent="lookupAccount">
            <label
              >查找方式<select v-model="lookupType" aria-label="查找方式">
                <option value="USERNAME">用户名</option>
                <option value="PHONE">手机号</option>
                <option value="PUBLIC_ID">用户 ID</option>
              </select></label
            >
            <label
              >{{
                lookupType === 'PHONE'
                  ? '对方手机号'
                  : lookupType === 'PUBLIC_ID'
                    ? '用户 ID'
                    : '对方用户名'
              }}<input
                v-model="userCode"
                :placeholder="
                  lookupType === 'PHONE'
                    ? '输入完整手机号'
                    : lookupType === 'PUBLIC_ID'
                      ? '输入对方提供的用户 ID'
                      : '输入对方的注册用户名'
                "
                :type="lookupType === 'PHONE' ? 'tel' : 'text'"
                autocomplete="off"
                maxlength="100"
                @input="
                  lookupUser = null;
                  friendForm.target_user_public_id = '';
                  lookupSequence++;
                  lookupBusy = false;
                "
            /></label>
            <p class="field-note">精确查找后请核对昵称；联系方式不会展示在查找结果中。</p>
            <button class="secondary" :disabled="lookupBusy || !userCode.trim()">
              {{ lookupBusy ? '查找中…' : '查找用户' }}
            </button>
          </form>
          <form @submit.prevent="addFriend">
            <template v-if="contactMode === 'group'">
              <label
                >搜索联系人<input
                  v-model="friendQuery"
                  placeholder="输入同群联系人的昵称或用户名"
                  autocomplete="off"
              /></label>
              <div class="contact-picker" aria-label="可添加的联系人">
                <p v-if="loadingContacts" class="muted" role="status">正在查找同群联系人…</p>
                <button
                  v-for="person in matchingContacts"
                  :key="person.public_id"
                  type="button"
                  :class="[
                    'contact-option',
                    { selected: lookupUser?.public_id === person.public_id },
                  ]"
                  :aria-pressed="lookupUser?.public_id === person.public_id"
                  @click="chooseContact(person)"
                >
                  <span class="avatar small lavender">{{ person.nickname.slice(-1) }}</span
                  ><span>{{ person.nickname }}</span
                  ><Check v-if="lookupUser?.public_id === person.public_id" :size="16" />
                </button>
                <p v-if="!loadingContacts && !matchingContacts.length" class="field-note">
                  {{
                    discoverableUsers.length
                      ? '没有匹配的联系人。'
                      : '暂无可添加的同群联系人，加入群聊后可在这里查找。'
                  }}
                </p>
              </div>
            </template>
            <p v-if="lookupUser" class="pill">已选择：{{ lookupUser.nickname }}</p>
            <label
              >打招呼<textarea
                v-model="friendForm.message"
                placeholder="你好，很高兴认识你！"
              /></label
            ><button
              class="primary"
              :disabled="
                busy || lookupBusy || (contactMode === 'group' && loadingContacts) || !lookupUser
              "
            >
              发送申请<Send :size="16" />
            </button></form
        ></template>
        <template v-else-if="modal === 'group'"
          ><h2 id="modal-title">把有趣的人聚在一起</h2>
          <form @submit.prevent="createGroup">
            <label>群聊名称<input v-model="groupForm.name" required maxlength="100" /></label
            ><label v-for="f in friends" :key="f.public_id" class="checkbox-label"
              ><input type="checkbox" v-model="groupForm.members" :value="f.public_id" />{{
                f.nickname
              }}</label
            >
            <p v-if="!friends.length">可以先创建群聊，稍后再邀请好友。</p>
            <button class="primary" :disabled="busy">创建群聊</button>
          </form></template
        >
        <template v-else-if="modal === 'requests'"
          ><h2 id="modal-title">新的连接</h2>
          <div class="conversation-tabs">
            <button :class="{ active: requestTab === 'incoming' }" @click="requestTab = 'incoming'">
              收到的申请</button
            ><button
              :class="{ active: requestTab === 'outgoing' }"
              @click="requestTab = 'outgoing'"
            >
              发出的申请
            </button>
          </div>
          <div class="search-field">
            <Search :size="16" /><input
              v-model="requestKeyword"
              aria-label="搜索好友申请"
              placeholder="搜索好友申请"
            />
          </div>
          <div v-for="r in visibleRequests" :key="r.request_public_id" class="request-item">
            <strong>{{ displayUserName(r.sender || {}, '好友申请') }}</strong>
            <small v-if="r.created_at" class="muted">
              · {{ new Date(r.created_at).toLocaleString('zh-CN') }}</small
            >
            <p>{{ r.message }}</p>
            <div v-if="r.status === 'PENDING' && requestTab === 'incoming'" class="inline-actions">
              <button class="primary" :disabled="busy" @click="acceptRequest(r, true)">接受</button
              ><button class="secondary" :disabled="busy" @click="acceptRequest(r, false)">
                拒绝
              </button>
            </div>
            <button
              v-else-if="r.status === 'PENDING'"
              class="secondary"
              :disabled="busy"
              @click="cancelRequest(r)"
            >
              撤销申请
            </button>
            <span v-else class="pill">{{ requestStatus(r.status) }}</span>
          </div>
          <p v-if="!visibleRequests.length" class="empty">暂无匹配的好友申请。</p></template
        >
        <template v-else-if="modal === 'contact' && contact"
          ><h2 id="modal-title">好友资料</h2>
          <span class="avatar large lavender">{{ contact.nickname.slice(-2) }}</span>
          <h3 class="contact-name">{{ contact.nickname }}</h3>
          <dl class="detail-list">
            <dt>用户名</dt>
            <dd>{{ contact.username ? redactIdentifiers(contact.username) : '未提供' }}</dd>
            <dt>邮箱</dt>
            <dd>{{ contact.email || '未提供' }}</dd>
            <dt>电话</dt>
            <dd>{{ contact.phone || '未提供' }}</dd>
          </dl>
          <div class="form-actions">
            <button class="secondary danger" @click="modal = 'deleteContact'">删除好友</button
            ><button
              class="primary"
              @click="
                chatWith(contact);
                modal = '';
              "
            >
              发消息
            </button>
          </div></template
        >
        <template v-else-if="modal === 'deleteContact'"
          ><h2 id="modal-title">确认删除好友？</h2>
          <p>将解除你与 {{ contact?.nickname }} 的好友关系。</p>
          <div class="form-actions">
            <button class="secondary" @click="modal = 'contact'">取消</button
            ><button class="primary" :disabled="busy" @click="deleteContact">确认删除好友</button>
          </div></template
        >
        <template v-else-if="modal === 'removeConversation' && removalTarget">
          <h2 id="modal-title">移除这段会话？</h2>
          <p>
            将“{{
              removalTarget.name
            }}”从此浏览器的会话列表移除，不删除聊天记录，也不会退出群聊。收到新消息或重新发起聊天时会再次显示。
          </p>
          <div class="form-actions">
            <button class="secondary" @click="modal = ''">取消</button
            ><button class="primary" @click="removeConversation">确认移除会话</button>
          </div>
        </template>
        <template v-else-if="modal === 'clearMessages'"
          ><h2 id="modal-title">清空我的消息视图？</h2>
          <p>此操作只清空当前账号在本会话中的历史消息视图，不影响其他成员。</p>
          <div class="form-actions">
            <button class="secondary" @click="modal = ''">取消</button
            ><button class="primary" :disabled="busy" @click="clearMessages">确认清空</button>
          </div></template
        >
        <template v-else-if="modal === 'candidates'"
          ><h2 id="modal-title">选择要总结的消息</h2>
          <div class="pick-list candidate-list">
            <label v-for="(m, i) in candidates" :key="i" class="checkbox-label"
              ><input type="checkbox" v-model="pickedCandidates" :value="i" /><span
                ><small>{{ redactIdentifiers(m.sender || '我') }}</small
                >{{ m.content }}</span
              ></label
            >
          </div>
          <p v-if="!candidates.length" class="empty">暂无可供选择的消息</p>
          <button class="primary" :disabled="!pickedCandidates.length" @click="useCandidates">
            使用已选 {{ pickedCandidates.length }} 条消息
          </button></template
        >
        <template v-else
          ><h2 id="modal-title">欢迎来到共鸣</h2>
          <p>在消息页与好友私聊或创建群聊，在日程页记录约定，在 AI 助手中润色、总结和翻译内容。</p>
          <p>
            {{
              demo
                ? '当前为演示模式，数据仅保留在本次页面会话中。AI 结果为固定示例。'
                : '当前为真实 API 模式，操作将保存到后端。'
            }}
          </p>
          <button class="primary" @click="modal = ''">开始探索<ArrowRight :size="16" /></button
        ></template>
        <p v-if="error" class="modal-error" role="alert">{{ redactIdentifiers(error) }}</p>
      </section>
    </div>
  </div>
</template>
