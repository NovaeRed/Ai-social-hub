import type { CursorPage } from '../types/domain';

import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IS_DEMO_MODE, request, post, type User } from '../services/http';
import { displayUserName } from '../lib/presentation';

export interface Member {
  public_id: string;
  nickname: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER';
  joined_at?: string;
}
export interface Group {
  public_id: string;
  name: string;
  member_count: number;
  created_at?: string;
  announcement?: string;
  members?: Member[];
}
export function useGroupWorkspace(
  props: Readonly<{ user: User; friends: User[] }>,
  emit: {
    (event: 'chat', group: Group): void;
    (event: 'changed'): void;
    (event: 'notify', message: string): void;
  },
) {
  const announcementDraft = ref('');
  function editAnnouncement() {
    if (!detail.value || role.value !== 'OWNER') return;
    announcementDraft.value = detail.value.announcement || '';
    modal.value = 'announcement';
  }
  async function saveAnnouncement() {
    await act(async () => {
      if (!detail.value || role.value !== 'OWNER') throw new Error('只有群主才能编辑群公告');
      if (announcementDraft.value.length > 2000) throw new Error('公告不能超过 2000 字');
      const announcement = announcementDraft.value.trim();
      if (!IS_DEMO_MODE) {
        await post(`/groups/${detail.value.public_id}/announcement`, { announcement }, 'PATCH');
      }
      detail.value.announcement = announcement;
      modal.value = '';
      emit('notify', announcement ? '群公告已保存' : '群公告已清除');
    });
  }
  const route = useRoute(),
    router = useRouter(),
    groups = ref<Group[]>([]),
    detail = ref<Group | null>(null),
    isBusy = ref(false),
    isLoading = ref(false),
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
  let loadVersion = 0;
  async function act(fn: () => Promise<void>) {
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
  async function load(more = false) {
    const version = ++loadVersion;
    isLoading.value = true;
    error.value = '';
    try {
      if (IS_DEMO_MODE) {
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
      const page = await request<CursorPage<Group>>('/groups?' + qs);
      if (version !== loadVersion) return;
      groups.value = more ? [...groups.value, ...page.items] : page.items;
      cursor.value = page.next_cursor;
      hasMore.value = page.has_more;
      // 刷新列表时同步刷新展开的成员资料，不能用旧详情覆盖最新人数。
      const activeId = detail.value?.public_id;
      if (!more && activeId) {
        if (
          !hasMore.value &&
          !keyword.value.trim() &&
          !groups.value.some((g) => g.public_id === activeId)
        ) {
          closeDetail();
          emit('notify', '你已不在此群聊中，或群聊已解散');
          return;
        }
        const freshDetail = await request<Group>(`/groups/${activeId}`);
        if (version === loadVersion && detail.value?.public_id === activeId)
          detail.value = freshDetail;
      }
    } catch (e) {
      if (version === loadVersion) error.value = (e as Error).message;
    } finally {
      if (version === loadVersion) isLoading.value = false;
    }
  }
  async function open(group: Group) {
    await act(async () => {
      const d = IS_DEMO_MODE
        ? groups.value.find((item) => item.public_id === group.public_id) || group
        : await request<Group>(`/groups/${group.public_id}`);
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
  function openRename() {
    if (!detail.value) return;
    newName.value = detail.value.name;
    modal.value = 'rename';
  }
  function openInvite() {
    picked.value = [];
    modal.value = 'invite';
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
        const result = IS_DEMO_MODE
          ? { public_id: 'demo-group-' + Date.now() }
          : await post<Group>('/groups', {
              name: newName.value.trim(),
              member_public_ids: picked.value,
            });
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
        if (IS_DEMO_MODE) groups.value.unshift(group);
        else await load();
        detail.value = IS_DEMO_MODE ? group : await request<Group>(`/groups/${result.public_id}`);
        if (detail.value?.members) detail.value.member_count = detail.value.members.length;
      } else if (detail.value) {
        if (!IS_DEMO_MODE)
          await post<Group>(
            `/groups/${detail.value.public_id}`,
            { name: newName.value.trim() },
            'PATCH',
          );
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
      if (!IS_DEMO_MODE) {
        await post<Group>(`/groups/${detail.value.public_id}/members`, {
          user_public_ids: picked.value,
        });
        detail.value = await request<Group>(`/groups/${detail.value.public_id}`);
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
      if (!IS_DEMO_MODE) {
        if (modal.value === 'promote')
          await post<Group>(path + '/members/admins', { user_public_ids: [m!.public_id] });
        if (modal.value === 'demote')
          await post<Group>(
            path + '/members/admins',
            { user_public_ids: [m!.public_id] },
            'DELETE',
          );
        if (modal.value === 'transfer')
          await post<Group>(path + '/members/owner', { new_owner_public_id: m!.public_id });
        if (modal.value === 'remove')
          await request<Group>(path + '/members/' + m!.public_id, { method: 'DELETE' });
        if (modal.value === 'leave' || modal.value === 'dissolve')
          await request<CursorPage<Group>>(
            path + '/members/' + (modal.value === 'leave' ? 'me' : 'owner'),
            {
              method: 'DELETE',
            },
          );
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
      } else if (!IS_DEMO_MODE) detail.value = await request<Group>(path);
      modal.value = '';
      if (!IS_DEMO_MODE) await load();
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

  return {
    announcementDraft,
    editAnnouncement,
    saveAnnouncement,
    openRename,
    openInvite,
    groups,
    detail,
    isBusy,
    isLoading,
    error,
    keyword,
    memberKeyword,
    modal,
    newName,
    picked,
    target,
    hasMore,
    role,
    actualCount,
    members,
    invitees,
    confirmTitle,
    roleName,
    load,
    open,
    create,
    closeDetail,
    save,
    invite,
    confirm,
    mutate,
    canRemove,
  };
}
