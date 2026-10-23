<script setup lang="ts">
import { redactIdentifiers } from '../lib/presentation';
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Users,
  Plus,
  Search,
  MessageCircle,
  ArrowRight,
  Crown,
  Shield,
  UserPlus,
  Settings,
  X,
  Check,
  RefreshCw,
  LogOut,
} from 'lucide-vue-next';
import { demo, request, post, type User } from '../lib/api';
import { displayUserName } from '../lib/presentation';
interface Member {
  public_id: string;
  nickname: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER';
  joined_at?: string;
}
interface Group {
  public_id: string;
  name: string;
  member_count: number;
  created_at?: string;
  announcement?: string;
  members?: Member[];
}
const props = defineProps<{ user: User; friends: User[] }>();
const emit = defineEmits<{ chat: [group: Group]; changed: []; notify: [message: string] }>();
const route = useRoute(),
  router = useRouter(),
  groups = ref<Group[]>([]),
  detail = ref<Group | null>(null),
  busy = ref(false),
  loading = ref(false),
  error = ref(''),
  keyword = ref(''),
  memberKeyword = ref(''),
  modal = ref(''),
  newName = ref(''),
  picked = ref<string[]>([]),
  target = ref<Member | null>(null),
  cursor = ref<number | null>(null),
  hasMore = ref(false);
const role = computed(
  () => detail.value?.members?.find((m) => m.public_id === props.user.public_id)?.role,
);
const actualCount = computed(
  () => detail.value?.members?.length ?? detail.value?.member_count ?? 0,
);
watch(
  () => [detail.value?.public_id, actualCount.value],
  () => {
    const g = groups.value.find((g) => g.public_id === detail.value?.public_id);
    if (g) g.member_count = actualCount.value;
  },
);
const members = computed(
  () =>
    detail.value?.members?.filter((m) => displayUserName(m).includes(memberKeyword.value)) || [],
);
const invitees = computed(() =>
  props.friends.filter((f) => !detail.value?.members?.some((m) => m.public_id === f.public_id)),
);
const confirmTitle = computed(
  () =>
    (
      ({
        promote: '设置管理员',
        demote: '取消管理员',
        transfer: '转让群主',
        remove: '移出成员',
        leave: '退出群聊',
        dissolve: '解散群聊',
      }) as Record<string, string>
    )[modal.value],
);
const roleName = (role: string) =>
  ({ OWNER: '群主', ADMIN: '管理员', MEMBER: '成员' })[role] || role;
async function act(fn: () => Promise<void>) {
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
async function load(more = false) {
  loading.value = true;
  error.value = '';
  try {
    if (demo) {
      if (!groups.value.length)
        groups.value = [
          {
            public_id: 'demo-conv-2',
            name: '灵感碰撞小分队',
            member_count: props.friends.length + 1,
            created_at: new Date().toISOString(),
            members: [
              { ...props.user, role: 'OWNER' },
              ...props.friends.map(
                (f, i) => ({ ...f, role: i === 0 ? 'ADMIN' : 'MEMBER' }) as Member,
              ),
            ],
          },
        ];
      return;
    }
    const qs = new URLSearchParams({ limit: '20', keyword: keyword.value });
    if (more && cursor.value) qs.set('cursor', String(cursor.value));
    const page = await request('/groups?' + qs);
    groups.value = (more ? [...groups.value, ...page.items] : page.items).map((g: Group) =>
      g.public_id === detail.value?.public_id ? { ...g, member_count: actualCount.value } : g,
    );
    cursor.value = page.next_cursor;
    hasMore.value = page.has_more;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}
async function open(group: Group) {
  await act(async () => {
    const d = demo
      ? groups.value.find((item) => item.public_id === group.public_id) || group
      : await request(`/groups/${group.public_id}`);
    if (!d.members?.some((m: Member) => m.public_id === props.user.public_id))
      throw new Error('你不在此群聊中，无法查看群空间');
    d.member_count = d.members.length;
    const summary = groups.value.find((g) => g.public_id === d.public_id);
    if (summary) summary.member_count = d.member_count;
    detail.value = d;
    await router.replace({ path: '/groups', force: true, state: { activeGroup: d.public_id } });
    memberKeyword.value = '';
    newName.value = d.name;
  });
}
function create() {
  picked.value = [];
  newName.value = '';
  modal.value = 'create';
}
function closeDetail() {
  detail.value = null;
  void router.replace({ path: '/groups', force: true, state: { activeGroup: null } });
}
async function save() {
  await act(async () => {
    if (!newName.value.trim()) throw new Error('请填写群名称');
    if (modal.value === 'create') {
      const result = demo
        ? { public_id: 'demo-group-' + Date.now() }
        : await post('/groups', { name: newName.value.trim(), member_public_ids: picked.value });
      const group: Group = {
        ...result,
        name: newName.value,
        member_count: picked.value.length + 1,
        members: [
          { ...props.user, role: 'OWNER' },
          ...props.friends
            .filter((f) => picked.value.includes(f.public_id))
            .map((f) => ({ ...f, role: 'MEMBER' }) as Member),
        ],
      };
      if (demo) groups.value.unshift(group);
      else await load();
      detail.value = demo ? group : await request(`/groups/${result.public_id}`);
      if (detail.value?.members) detail.value.member_count = detail.value.members.length;
    } else if (detail.value) {
      if (!demo)
        await post(`/groups/${detail.value.public_id}`, { name: newName.value.trim() }, 'PATCH');
      detail.value.name = newName.value;
      const g = groups.value.find((g) => g.public_id === detail.value!.public_id);
      if (g) g.name = newName.value;
    }
    modal.value = '';
    if (detail.value)
      await router.replace({
        path: '/groups',
        force: true,
        state: { activeGroup: detail.value.public_id },
      });
    emit('changed');
    emit('notify', '群聊已保存');
  });
}
async function invite() {
  await act(async () => {
    if (!picked.value.length) throw new Error('请至少选择一位好友');
    if (!detail.value) return;
    if (!demo) {
      await post(`/groups/${detail.value.public_id}/members`, { user_public_ids: picked.value });
      detail.value = await request(`/groups/${detail.value.public_id}`);
    } else {
      detail.value.members?.push(
        ...props.friends
          .filter((f) => picked.value.includes(f.public_id))
          .map((f) => ({ ...f, role: 'MEMBER' }) as Member),
      );
      detail.value.member_count = detail.value.members?.length || 0;
    }
    modal.value = '';
    await load();
    emit('notify', '已邀请好友加入群聊');
  });
}
function confirm(kind: string, member?: Member) {
  target.value = member || null;
  modal.value = kind;
}
async function mutate() {
  await act(async () => {
    if (!detail.value) return;
    const id = detail.value.public_id,
      m = target.value;
    const path = `/groups/${id}`;
    if (!demo) {
      if (modal.value === 'promote')
        await post(path + '/members/admins', { user_public_ids: [m!.public_id] });
      if (modal.value === 'demote')
        await post(path + '/members/admins', { user_public_ids: [m!.public_id] }, 'DELETE');
      if (modal.value === 'transfer')
        await post(path + '/members/owner', { new_owner_public_id: m!.public_id });
      if (modal.value === 'remove')
        await request(path + '/members/' + m!.public_id, { method: 'DELETE' });
      if (modal.value === 'leave' || modal.value === 'dissolve')
        await request(path + '/members/' + (modal.value === 'leave' ? 'me' : 'owner'), {
          method: 'DELETE',
        });
    } else {
      if (modal.value === 'promote') m!.role = 'ADMIN';
      if (modal.value === 'demote') m!.role = 'MEMBER';
      if (modal.value === 'transfer') {
        detail.value.members!.find((x) => x.public_id === props.user.public_id)!.role = 'MEMBER';
        m!.role = 'OWNER';
      }
      if (modal.value === 'remove') {
        detail.value.members = detail.value.members?.filter((x) => x.public_id !== m!.public_id);
        detail.value.member_count = detail.value.members?.length || 0;
      }
    }
    if (['leave', 'dissolve'].includes(modal.value)) {
      groups.value = groups.value.filter((g) => g.public_id !== id);
      closeDetail();
    } else if (!demo) detail.value = await request(path);
    modal.value = '';
    if (!demo) await load();
    emit('changed');
    emit('notify', '群聊已更新');
  });
}
const canRemove = (m: Member) =>
  m.public_id !== props.user.public_id &&
  m.role !== 'OWNER' &&
  (role.value === 'OWNER' || (role.value === 'ADMIN' && m.role === 'MEMBER'));
watch(
  () => route.query.group,
  async (id) => {
    if (typeof id === 'string') await open({ public_id: id } as Group);
  },
);
onMounted(async () => {
  await load();
  const id = route.query.group || window.history.state.activeGroup;
  if (typeof id === 'string') await open({ public_id: id } as Group);
});
</script>
<template>
  <div class="page-heading">
    <div><h1>群聊</h1></div>
    <button class="primary" @click="create"><Plus :size="17" />创建群聊</button>
  </div>
  <p v-if="error" class="inline-error" role="alert">{{ redactIdentifiers(error) }}</p>
  <div class="group-toolbar">
    <form class="search-field" @submit.prevent="load()">
      <Search :size="17" /><input
        v-model="keyword"
        placeholder="搜索群聊名称"
        aria-label="搜索群聊名称"
      /><button class="text-button" :disabled="loading">搜索</button>
    </form>
    <button class="secondary" :disabled="loading" @click="load()">
      <RefreshCw :size="15" />刷新
    </button>
  </div>
  <div class="group-workspace" :class="{ 'has-detail': detail }">
    <section class="group-grid">
      <button
        v-for="(g, i) in demo ? groups.filter((g) => g.name.includes(keyword)) : groups"
        :key="g.public_id"
        :class="['card group-card', { selected: detail?.public_id === g.public_id }]"
        @click="open(g)"
      >
        <span :class="['group-art', ['lavender', 'sage', 'peach'][i % 3]]"
          ><Users :size="31" /><span class="orbit"
        /></span>
        <h3>{{ g.name }}</h3>
        <p><Users :size="13" />{{ g.member_count }} 位成员</p>
        <span class="group-open">进入群空间<ArrowRight :size="15" /></span>
      </button>
      <div v-if="!groups.length" class="card empty">
        <Users :size="40" />
        <h3>{{ loading ? '正在加载群聊…' : '给好想法一个聚集地' }}</h3>
        <p>创建群聊，邀请好友加入。</p>
        <button class="primary" @click="create">创建第一个群聊</button>
      </div>
      <button v-if="hasMore" class="secondary" :disabled="loading" @click="load(true)">
        加载更多群聊
      </button>
    </section>
    <section v-if="detail" class="card group-detail">
      <div class="section-heading">
        <span class="pill">群空间</span
        ><button class="icon-button" aria-label="关闭群详情" @click="closeDetail">
          <X :size="19" />
        </button>
      </div>
      <span class="avatar large lavender"><Users :size="30" /></span>
      <h2>{{ detail.name }}</h2>
      <p class="muted detail-description">
        {{ actualCount }} 位成员 · {{ roleName(role || 'MEMBER') }}
      </p>
      <div class="detail-actions">
        <button class="primary" :disabled="busy" @click="emit('chat', detail)">
          <MessageCircle :size="16" />进入群聊</button
        ><button
          class="secondary"
          @click="
            newName = detail.name;
            modal = 'rename';
          "
        >
          <Settings :size="16" />编辑群名
        </button>
      </div>
      <div class="group-announcement">
        <strong>群公告</strong>
        <p>{{ detail.announcement || '暂时没有群公告' }}</p>
      </div>
      <div class="section-heading">
        <h3>
          群成员 <span class="muted">{{ actualCount }}</span>
        </h3>
        <button
          class="text-button"
          @click="
            picked = [];
            modal = 'invite';
          "
        >
          <UserPlus :size="15" />邀请好友
        </button>
      </div>
      <div class="search-field">
        <Search :size="15" /><input
          v-model="memberKeyword"
          aria-label="搜索群成员"
          placeholder="搜索群成员"
        />
      </div>
      <div class="member-list">
        <article v-for="m in members" :key="m.public_id" class="member-row">
          <span class="avatar small sage">{{ displayUserName(m, '群成员').slice(-1) }}</span>
          <div class="member-identity">
            <strong
              >{{ displayUserName(m, '群成员')
              }}<small v-if="m.public_id === user.public_id">我</small></strong
            ><span :class="['role', m.role.toLowerCase()]"
              ><Crown v-if="m.role === 'OWNER'" :size="11" /><Shield
                v-else-if="m.role === 'ADMIN'"
                :size="11"
              />{{ roleName(m.role) }}</span
            >
          </div>
          <div class="member-actions">
            <button
              v-if="role === 'OWNER' && m.role === 'MEMBER'"
              class="text-button"
              :disabled="busy"
              @click="confirm('promote', m)"
            >
              设为管理员</button
            ><button
              v-if="role === 'OWNER' && m.role === 'ADMIN'"
              class="text-button"
              :disabled="busy"
              @click="confirm('demote', m)"
            >
              取消管理员</button
            ><button
              v-if="role === 'OWNER' && m.public_id !== user.public_id"
              class="text-button"
              @click="confirm('transfer', m)"
            >
              转让群主</button
            ><button v-if="canRemove(m)" class="text-button danger" @click="confirm('remove', m)">
              移出
            </button>
          </div>
        </article>
        <p v-if="!members.length" class="empty">没有匹配的成员</p>
      </div>
      <button
        v-if="role === 'OWNER'"
        class="danger secondary group-leave"
        @click="confirm('dissolve')"
      >
        解散群聊</button
      ><button v-else class="danger secondary group-leave" @click="confirm('leave')">
        <LogOut :size="15" />退出群聊
      </button>
    </section>
  </div>
  <div
    v-if="modal"
    class="modal-overlay"
    @click.self="modal = ''"
    @keydown.esc="modal = ''"
    tabindex="-1"
  >
    <section
      class="modal card"
      role="dialog"
      aria-modal="true"
      aria-labelledby="group-dialog-title"
    >
      <button class="icon-button close" aria-label="关闭弹窗" @click="modal = ''">
        <X :size="20" /></button
      ><template v-if="modal === 'create' || modal === 'rename'"
        ><h2 id="group-dialog-title">
          {{ modal === 'create' ? '创建新的群空间' : '编辑群聊名称' }}
        </h2>
        <form @submit.prevent="save">
          <label
            >群聊名称<input
              v-model="newName"
              required
              maxlength="100"
              autofocus
              placeholder="为你们的群聊起个名字" /></label
          ><template v-if="modal === 'create'"
            ><p class="muted">邀请好友（可稍后邀请）</p>
            <div class="pick-list">
              <label v-for="f in friends" :key="f.public_id" class="checkbox-label"
                ><input v-model="picked" type="checkbox" :value="f.public_id" />{{
                  f.nickname
                }}</label
              >
            </div></template
          ><button class="primary" :disabled="busy">
            <Check :size="16" />{{ modal === 'create' ? '创建群聊' : '保存群名' }}
          </button>
        </form></template
      ><template v-else-if="modal === 'invite'"
        ><h2 id="group-dialog-title">邀请好友加入</h2>
        <div class="pick-list">
          <label v-for="f in invitees" :key="f.public_id" class="checkbox-label"
            ><input v-model="picked" type="checkbox" :value="f.public_id" />{{ f.nickname }}</label
          >
        </div>
        <p v-if="!invitees.length" class="muted">好友都已在群内，可先前往好友页添加新好友。</p>
        <button class="primary" :disabled="busy || !picked.length" @click="invite">
          邀请 {{ picked.length }} 位好友
        </button></template
      ><template v-else
        ><h2 id="group-dialog-title">确认{{ confirmTitle }}？</h2>
        <p v-if="target">操作对象：{{ displayUserName(target, '群成员') }}</p>
        <p v-if="modal === 'transfer'">转让后你将成为普通成员，群主管理权将交给对方。</p>
        <p v-if="modal === 'dissolve'">解散后所有成员都将失去此群聊的访问权限。</p>
        <p v-if="modal === 'leave'">退出后需要由其他成员重新邀请，才能加入群聊。</p>
        <div class="form-actions">
          <button class="secondary" :disabled="busy" @click="modal = ''">取消</button
          ><button class="primary" :disabled="busy" @click="mutate">确认操作</button>
        </div></template
      >
      <p v-if="error" class="inline-error" role="alert">{{ redactIdentifiers(error) }}</p>
    </section>
  </div>
</template>
