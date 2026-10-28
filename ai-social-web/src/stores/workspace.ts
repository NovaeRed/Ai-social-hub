import { fetchWorkspaceSnapshot } from '../services/workspace';
import { NAV_ITEMS } from '../constants/navigation';
import { useScheduleEditor } from '../composables/useScheduleEditor';
import { useRealtimeConnection } from '../composables/useRealtimeConnection';
import { useContacts } from '../composables/useContacts';
import { useAiTools } from '../composables/useAiTools';
import { useCalendar } from '../composables/useCalendar';
import { useDialogFocus } from '../composables/useDialogFocus';

import type { UploadedFile } from '../types/domain';
import type {
  AiJob,
  AiModel,
  FriendRequest,
  FriendRequests,
  GroupSummary,
  MessageCandidate,
  CursorPage,
} from '../types/domain';
import { defineStore } from 'pinia';

import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  IS_DEMO_MODE,
  request,
  post,
  tokens,
  clearTokens,
  downloadFile,
  type User,
  type Conversation,
  type Message,
  type Schedule,
} from '../services/http';
import * as seed from '../lib/demo';
import { normalizeCurrentUser } from '../lib/identity';
import { displayUserName, formatMessagePreview } from '../lib/presentation';

import ChatAssistant from '../components/ChatAssistant.vue';

import { useChatPreferences } from '../composables/useChatPreferences';
import { createCoalescedRefresh } from '../lib/refresh';

export const useWorkspaceStore = defineStore('workspace', () => {
  const route = useRoute(),
    router = useRouter();
  const nav = NAV_ITEMS;
  const user = ref<User>(IS_DEMO_MODE ? { ...seed.me } : { public_id: '', nickname: '' }),
    conversations = ref<Conversation[]>(IS_DEMO_MODE ? structuredClone(seed.conversations) : []),
    friends = ref<User[]>(IS_DEMO_MODE ? structuredClone(seed.friends) : []),
    schedules = ref<Schedule[]>(IS_DEMO_MODE ? structuredClone(seed.schedules) : []),
    jobs = ref<AiJob[]>(IS_DEMO_MODE ? seed.jobs : []),
    history = ref<Record<string, Message[]>>(IS_DEMO_MODE ? structuredClone(seed.messages) : {});
  const requests = ref<FriendRequests>({ incoming: [], outgoing: [] }),
    models = ref<AiModel[]>([]),
    selected = ref(''),
    query = ref(''),
    draft = ref(''),
    isBusy = ref(false),
    isLoading = ref(false),
    error = ref(''),
    toast = ref(''),
    modal = ref(''),
    isSidebarOpen = ref(false),
    isRegistering = ref(false);
  const login = ref({ username: '', nickname: '', password: '', email: '' }),
    profile = ref({ nickname: '', email: '', ai_analysis_enabled: false }),
    friendForm = ref({ target_user_public_id: '', message: '' }),
    groupForm = ref({ name: '', members: [] as string[] }),
    scheduleForm = ref({ title: '', description: '', start_time: '', end_time: '', location: '' }),
    editing = ref('');
  const lookupUser = ref<User | null>(null),
    contact = ref<User | null>(null),
    requestKeyword = ref(''),
    requestTab = ref<'incoming' | 'outgoing'>('incoming'),
    conversationFilter = ref('ALL'),
    realtime = ref('未连接');
  const friendQuery = ref(''),
    discoverableUsers = ref<User[]>([]),
    isLoadingContacts = ref(false);
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
    isLookupBusy = ref(false);
  const { lookupSequence, lookupAccount, switchContacts, chooseContact } = useContacts({
    userCode,
    lookupType,
    lookupUser,
    friendForm,
    error,
    isLookupBusy,
    modal,
    contactMode,
    user,
    friends,
    isLoadingContacts,
    friendQuery,
    discoverableUsers,
    getSessionGeneration: () => sessionGeneration,
  });
  const conversationPage = ref<{ next_cursor: number | null; has_more: boolean }>({
    next_cursor: null,
    has_more: false,
  });
  const conversationNames = ref<Record<string, string>>({});
  const conversationDrafts: Record<string, string> = {};
  const chatAssistant = ref<InstanceType<typeof ChatAssistant> | null>(null),
    isChatAiBusy = ref(false);
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
  let isPrependingMessages = false;
  watch(selected, (id, previous) => {
    chatAssistant.value?.cancel();
    if (previous) conversationDrafts[previous] = draft.value;
    draft.value = conversationDrafts[id] || '';
  });
  function normalizeConversation(c: Conversation): Conversation {
    return {
      ...c,
      name:
        c.name ||
        conversationNames.value[c.public_id] ||
        (c.type === 'GROUP' ? '未命名群聊' : '私聊会话'),
      unread_count: Number(c.unread_count || 0),
    };
  }
  function normalizeMessage(message: Message): Message {
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
      requests.value[requestTab.value]?.filter((r: FriendRequest) =>
        (r.sender?.nickname + r.sender?.public_id + r.message).includes(requestKeyword.value),
      ) || [],
  );
  async function showContact(f: User) {
    await action(async () => {
      contact.value = IS_DEMO_MODE
        ? f
        : normalizeCurrentUser(await request<User>(`/users/${encodeURIComponent(f.public_id)}`));
      modal.value = 'contact';
    });
  }
  async function deleteContact() {
    await action(async () => {
      if (!contact.value) return;
      if (!IS_DEMO_MODE)
        await request<User[]>(`/friends/${encodeURIComponent(contact.value.public_id)}`, {
          method: 'DELETE',
        });
      friends.value = friends.value.filter((f) => f.public_id !== contact.value!.public_id);
      modal.value = '';
      notify('好友已删除');
    });
  }
  async function cancelRequest(r: FriendRequest) {
    await action(async () => {
      if (!IS_DEMO_MODE) {
        await request<FriendRequests>(
          `/friends/requests/${encodeURIComponent(r.request_public_id)}`,
          {
            method: 'DELETE',
          },
        );
        requests.value = await request<FriendRequests>('/friends/requests');
      } else r.status = 'CANCELLED';
      notify('好友申请已撤销');
    });
  }
  async function moreConversations() {
    await action(async () => {
      const page = await request<CursorPage<Conversation>>(
        `/conversations?cursor=${conversationPage.value.next_cursor}`,
      );
      conversations.value.push(...page.items.map(normalizeConversation));
      conversationPage.value = page;
    });
  }
  async function enterGroup(group: { public_id: string; name: string }) {
    if (!IS_DEMO_MODE) await load();
    if (!conversations.value.some((c) => c.public_id === group.public_id))
      conversations.value.unshift({ ...group, type: 'GROUP', unread_count: 0 });
    await openConversation(group.public_id);
  }
  async function clearMessages() {
    await action(async () => {
      if (!IS_DEMO_MODE)
        await request<CursorPage<Message>>(`/conversations/${selected.value}/messages`, {
          method: 'DELETE',
        });
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
    disconnect();
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
      const data = IS_DEMO_MODE
        ? { items: currentMessages.value.map((m) => ({ content: m.content })) }
        : await request<{ items: MessageCandidate[] }>(
            '/ai/jobs/message-candidates' +
              (selected.value ? '?conversation_public_id=' + selected.value : ''),
          );
      candidates.value = (data.items || []).map((item: MessageCandidate) => ({
        ...item,
        public_id: item.public_id || crypto.randomUUID(),
      }));
      pickedCandidates.value = [];
      modal.value = 'candidates';
    });
  }
  const candidates = ref<MessageCandidate[]>([]),
    pickedCandidates = ref<number[]>([]);
  function useCandidates() {
    aiInput.value = pickedCandidates.value.map((i) => candidates.value[i].content).join('\n');
    modal.value = '';
  }
  const {
    tools,
    tool,
    aiInput,
    aiOutput,
    model,
    targetLanguage,
    isAiBusy,
    availableModels,
    chooseTool,
    returnToChat,
    runAi,
    stopAi,
  } = useAiTools(models, selected, draft, error, go);
  const {
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
  } = useCalendar(schedules, () => sessionGeneration);
  const { openSchedule, saveSchedule, removeSchedule } = useScheduleEditor({
    editing,
    scheduleForm,
    schedules,
    chosenDate,
    modal,
    action,
    notify,
  });
  function openAiSchedule() {
    chooseTool('schedule');
  }
  let toastTimer: ReturnType<typeof setTimeout>;
  const messagePages = ref<Record<string, { next_cursor: number | null; has_more: boolean }>>({});
  const { trapFocus } = useDialogFocus(modal);
  watch(modal, (value) => {
    if (value === 'friend') {
      switchContacts('lookup');
      lookupType.value = 'USERNAME';
      userCode.value = '';
    }
  });
  function expired() {
    resetSession();
    notify('登录已过期，请重新登录');
  }
  async function olderMessages() {
    const id = selected.value,
      p = messagePages.value[id];
    if (!p?.has_more) return;
    await action(async () => {
      const page = await request<CursorPage<Message>>(
        `/conversations/${encodeURIComponent(id)}/messages?before_message_id=${p.next_cursor}`,
      );
      const el = document.querySelector('.chat-messages'),
        oldHeight = el?.scrollHeight || 0,
        oldTop = el?.scrollTop || 0;
      isPrependingMessages = true;
      history.value[id] = [
        ...page.items.map(normalizeMessage).reverse(),
        ...(history.value[id] || []),
      ];
      messagePages.value[id] = page;
      await nextTick();
      if (el && selected.value === id) el.scrollTop = oldTop + el.scrollHeight - oldHeight;
      isPrependingMessages = false;
    });
  }

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
        content: formatMessagePreview(message),
        created_at: message.created_at,
      };
  }
  const filteredConversations = computed(() =>
    visibleConversations.value
      .filter(
        (c) =>
          (conversationFilter.value === 'ALL' || c.type === conversationFilter.value) &&
          ((c.name || '').includes(query.value) ||
            c.latest_message?.content?.includes(query.value)),
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
      if (isPrependingMessages) return;
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
    if (isBusy.value) return;
    isBusy.value = true;
    error.value = '';
    try {
      await fn();
    } catch (e) {
      error.value = (e as Error).message;
    } finally {
      isBusy.value = false;
    }
  }
  function go(path: string) {
    isSidebarOpen.value = false;
    query.value = '';
    router.push(path);
  }
  const load = createCoalescedRefresh(async () => {
    if (IS_DEMO_MODE) return;
    const generation = sessionGeneration;
    isLoading.value = true;
    error.value = '';
    try {
      const me = await request<User>('/users/me');
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
      const results = await fetchWorkspaceSnapshot(from, to);
      if (generation !== sessionGeneration) return;
      function applyResult<T>(result: PromiseSettledResult<T>, apply: (value: T) => void) {
        if (result.status === 'fulfilled') apply(result.value);
        else
          error.value +=
            String(result.reason instanceof Error ? result.reason.message : result.reason) + '；';
      }
      applyResult(results[0], (page) => {
        conversations.value = page.items.map(normalizeConversation);
        conversationPage.value = page;
      });
      applyResult(results[1], (people) => {
        friends.value = people.map((person) => ({
          ...person,
          nickname: displayUserName(person, '好友'),
        }));
      });
      applyResult(results[2], (items) => {
        schedules.value = items;
      });
      applyResult(results[3], (page) => {
        jobs.value = page.items;
      });
      applyResult(results[4], (items) => {
        requests.value = items;
      });
      applyResult(results[5], (data) => {
        models.value = data.models;
      });
      if (!availableModels.value.some((m) => m.option_code === model.value))
        model.value =
          availableModels.value.find((m) => m.option_code === import.meta.env.VITE_DEFAULT_MODEL)
            ?.option_code ||
          availableModels.value[0]?.option_code ||
          '';
    } catch (e) {
      if (generation === sessionGeneration) error.value = (e as Error).message;
    } finally {
      if (generation === sessionGeneration) isLoading.value = false;
    }
  });

  const refreshRealtimeState = createCoalescedRefresh(async () => {
    const generation = sessionGeneration;
    if (!sessionStorage.getItem('access_token')) return;
    await load();
    if (generation !== sessionGeneration) return;
    if (selected.value && route.path === '/messages') {
      const id = selected.value;
      const page = await request<CursorPage<Message>>(
        `/conversations/${encodeURIComponent(id)}/messages`,
      );
      if (generation !== sessionGeneration || selected.value !== id || route.path !== '/messages')
        return;
      history.value[id] = page.items.map(normalizeMessage).reverse();
      messagePages.value[id] = page;
      const active = conversations.value.find((c) => c.public_id === id);
      if (active) active.unread_count = 0;
    }
  });
  const { connect, disconnect } = useRealtimeConnection(refreshRealtimeState, realtime, notify);
  async function authenticate() {
    await action(async () => {
      const chosenNickname = login.value.nickname.trim() || login.value.username.trim();
      let nicknameError = '';
      if (
        isRegistering.value &&
        !IS_DEMO_MODE &&
        !/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/.test(
          login.value.password,
        )
      )
        throw new Error(
          '密码至少 8 位，须含大小写字母、数字与 @$!%*?& 中的特殊字符，仅支持这些字符',
        );
      if (!IS_DEMO_MODE) {
        const { username, password, email } = login.value;
        tokens(
          await post<{ access_token: string; refresh_token: string }>(
            isRegistering.value ? '/auth/register' : '/auth/login',
            isRegistering.value ? { username, password, email } : { username, password },
          ),
        );
        if (isRegistering.value) {
          try {
            await post<User>('/users/me', { nickname: chosenNickname }, 'PATCH');
          } catch (e) {
            nicknameError = (e as Error).message;
          }
        }
      } else {
        user.value = {
          ...seed.me,
          nickname: isRegistering.value ? chosenNickname : login.value.username,
        };
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
        if (!IS_DEMO_MODE)
          await post<{ access_token: string; refresh_token: string }>('/auth/logout', {});
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
    if (IS_DEMO_MODE) {
      const c = conversations.value.find((c) => c.public_id === id);
      if (c) c.unread_count = 0;
    }
    if (!IS_DEMO_MODE)
      await action(async () => {
        const page = await request<CursorPage<Message>>(
          `/conversations/${encodeURIComponent(id)}/messages`,
        );
        history.value[id] = page.items.map(normalizeMessage).reverse();
        messagePages.value[id] = page;
        const c = conversations.value.find((c) => c.public_id === id);
        if (c) c.unread_count = 0;
      });
  }
  async function send() {
    const content = draft.value.trim(),
      id = selected.value;
    if (!content || !id || isChatAiBusy.value) return;
    await action(async () => {
      const msg: Message = IS_DEMO_MODE
        ? {
            public_id: `demo-${Date.now()}`,
            content,
            sender: user.value,
            created_at: new Date().toISOString(),
          }
        : await post<Message>(`/conversations/${encodeURIComponent(id)}/messages`, {
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
      if (IS_DEMO_MODE) {
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
        id = await post<string>('/conversations', { target_user_public_id: f.public_id });
        conversationNames.value[id] = f.nickname;
        await load();
        history.value[id] = (
          await request<CursorPage<Message>>(`/conversations/${encodeURIComponent(id)}/messages`)
        ).items
          .map(normalizeMessage)
          .reverse();
      }
      if (removedChats.value[id]) {
        delete removedChats.value[id];
        saveChatPreferences();
      }
      const conversation = conversations.value.find((c) => c.public_id === id);
      if (conversation) conversation.unread_count = 0;
      selected.value = id;
      go('/messages');
    });
  }
  async function addFriend() {
    await action(async () => {
      if (!friendForm.value.target_user_public_id) throw new Error('请先选择一位联系人');
      if (!IS_DEMO_MODE) await post<FriendRequests>('/friends/requests', friendForm.value);
      else
        requests.value.outgoing.push({
          request_public_id: `demo-${Date.now()}`,
          sender: user.value,
          receiver: lookupUser.value || user.value,
          message: friendForm.value.message,
          status: 'PENDING',
        });
      if (!IS_DEMO_MODE) requests.value = await request<FriendRequests>('/friends/requests');
      modal.value = '';
      notify('好友申请已发送');
    });
  }
  async function acceptRequest(r: FriendRequest, accept: boolean) {
    await action(async () => {
      if (!IS_DEMO_MODE) {
        if (accept)
          await post<FriendRequests>(
            `/friends/requests/${encodeURIComponent(r.request_public_id)}/accept`,
            {},
          );
        else
          await request<FriendRequests>(
            `/friends/requests/${encodeURIComponent(r.request_public_id)}`,
            {
              method: 'DELETE',
            },
          );
        await load();
      } else {
        requests.value.incoming = requests.value.incoming.filter((x: FriendRequest) => x !== r);
        if (accept) friends.value.push(r.sender);
      }
      notify(accept ? '已添加好友' : '已拒绝申请');
    });
  }
  async function createGroup() {
    await action(async () => {
      const d = IS_DEMO_MODE
        ? { public_id: `demo-group-${Date.now()}` }
        : await post<GroupSummary>('/groups', {
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
    if (!file || !selected.value || isBusy.value) return;
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
    if (!file || !selected.value || isBusy.value) return;
    const id = selected.value;
    const generation = sessionGeneration;
    const tempId = fileTempId.value;
    isBusy.value = true;
    fileError.value = '';
    try {
      if (IS_DEMO_MODE) {
        notify('演示模式不上传文件，请连接后端后使用');
        return;
      }
      const form = new FormData();
      form.append('file', file);
      form.append('conversation_public_id', id);
      const f = uploadedFileId.value
        ? { public_id: uploadedFileId.value }
        : await request<UploadedFile>('/files', { method: 'POST', body: form });
      if (generation !== sessionGeneration) return;
      if (selected.value === id) uploadedFileId.value = f.public_id;
      const msg = await post<Message>(`/conversations/${encodeURIComponent(id)}/messages`, {
        media_type: 'FILE',
        file_public_id: f.public_id,
        temp_id: tempId,
      });
      if (generation !== sessionGeneration) return;
      (history.value[id] ||= []).push({ ...msg, sender: msg.sender || user.value });
      const conversation = conversations.value.find((item) => item.public_id === id);
      if (conversation) {
        conversation.latest_message = {
          public_id: msg.public_id,
          created_at: msg.created_at,
          content: formatMessagePreview({
            ...msg,
            media_type: 'FILE',
            file: { original_filename: file.name },
          }),
        };
      }
      if (pendingFile.value === file) {
        pendingFile.value = null;
        uploadedFileId.value = '';
      }
    } catch (e) {
      if (selected.value === id) fileError.value = (e as Error).message;
    } finally {
      isBusy.value = false;
    }
  }
  async function saveProfile() {
    await action(async () => {
      if (!IS_DEMO_MODE)
        user.value = normalizeCurrentUser(await post<User>('/users/me', profile.value, 'PATCH'));
      else Object.assign(user.value, profile.value);
      notify('个人资料已保存');
    });
  }
  function summarizeChat() {
    chatAssistant.value?.run('summarize');
  }
  function polishDraft() {
    chatAssistant.value?.run('polish');
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
      if (!IS_DEMO_MODE && !sessionStorage.getItem('access_token') && route.path !== '/login')
        go('/login');
    },
  );
  onMounted(async () => {
    window.addEventListener('auth-expired', expired);
    if (!IS_DEMO_MODE && !sessionStorage.getItem('access_token')) {
      go('/login');
      return;
    }
    await load();
    connect();
  });
  onUnmounted(() => {
    sessionGeneration++;
    window.removeEventListener('auth-expired', expired);
    stopAi();
    disconnect();
    clearTimeout(toastTimer);
  });

  return {
    route,
    nav,
    user,
    conversations,
    friends,
    schedules,
    jobs,
    requests,
    models,
    selected,
    query,
    draft,
    isBusy,
    isLoading,
    error,
    toast,
    modal,
    isSidebarOpen,
    isRegistering,
    login,
    profile,
    friendForm,
    groupForm,
    scheduleForm,
    editing,
    lookupUser,
    contact,
    requestKeyword,
    requestTab,
    conversationFilter,
    realtime,
    friendQuery,
    discoverableUsers,
    isLoadingContacts,
    matchingContacts,
    contactMode,
    lookupType,
    userCode,
    isLookupBusy,
    lookupSequence,
    lookupAccount,
    switchContacts,
    chooseContact,
    conversationPage,
    chatAssistant,
    isChatAiBusy,
    pendingFile,
    fileError,
    uploadedFileId,
    requestStatus,
    visibleRequests,
    showContact,
    deleteContact,
    cancelRequest,
    moreConversations,
    enterGroup,
    clearMessages,
    groupDetails,
    resetSession,
    getFile,
    messageCandidates,
    candidates,
    pickedCandidates,
    useCandidates,
    openAiSchedule,
    tools,
    tool,
    aiInput,
    aiOutput,
    model,
    targetLanguage,
    isAiBusy,
    month,
    chosenDate,
    availableModels,
    messagePages,
    trapFocus,
    olderMessages,
    calendarDays,
    today,
    dateLabel,
    todaySchedules,
    daySchedules,
    pinnedChats,
    removalTarget,
    pinConversation,
    removeConversation,
    hideMessages,
    forwarded,
    filteredConversations,
    filteredFriends,
    activeConversation,
    currentMessages,
    unread,
    pageTitle,
    notify,
    go,
    load,
    authenticate,
    logout,
    openConversation,
    send,
    chatWith,
    openSchedule,
    saveSchedule,
    removeSchedule,
    addFriend,
    acceptRequest,
    createGroup,
    upload,
    fileSize,
    sendFile,
    saveProfile,
    chooseTool,
    summarizeChat,
    polishDraft,
    returnToChat,
    runAi,
    stopAi,
    isSchedulesLoading,
    scheduleError,
    moveMonth,
    time,
    safeUrl,
  };
});
