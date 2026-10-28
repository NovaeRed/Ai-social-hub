import { ref, watch, type Ref } from 'vue';
import { IS_DEMO_MODE, request, type User } from '../services/http';
import type { CursorPage, GroupSummary } from '../types/domain';
import { displayUserName } from '../lib/presentation';
interface ContactContext {
  userCode: Ref<string>;
  lookupType: Ref<string>;
  lookupUser: Ref<User | null>;
  friendForm: Ref<{ target_user_public_id: string; message: string }>;
  error: Ref<string>;
  isLookupBusy: Ref<boolean>;
  modal: Ref<string>;
  contactMode: Ref<string>;
  user: Ref<User>;
  friends: Ref<User[]>;
  isLoadingContacts: Ref<boolean>;
  friendQuery: Ref<string>;
  discoverableUsers: Ref<User[]>;
  getSessionGeneration: () => number;
}
export function useContacts(context: ContactContext) {
  const {
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
    getSessionGeneration,
  } = context;
  const lookupSequence = ref(0);
  watch(lookupType, () => {
    lookupSequence.value++;
    isLookupBusy.value = false;
    userCode.value = '';
    lookupUser.value = null;
    friendForm.value.target_user_public_id = '';
    error.value = '';
  });
  async function lookupAccount() {
    const code = userCode.value.trim(),
      sequence = ++lookupSequence.value,
      generation = getSessionGeneration();
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
    isLookupBusy.value = true;
    try {
      const person = IS_DEMO_MODE
        ? { public_id: 'demo-new-friend', nickname: '顾星河', username: 'xinghe' }
        : await request<User>(
            '/users/lookup?' + new URLSearchParams({ type: lookupType.value, value: code }),
          );
      if (
        sequence !== lookupSequence.value ||
        generation !== getSessionGeneration() ||
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
      if (sequence === lookupSequence.value && generation === getSessionGeneration())
        error.value = (e as Error).message;
    } finally {
      if (sequence === lookupSequence.value) isLookupBusy.value = false;
    }
  }
  function switchContacts(mode: string) {
    contactMode.value = mode;
    lookupSequence.value++;
    isLookupBusy.value = false;
    lookupUser.value = null;
    friendForm.value.target_user_public_id = '';
    error.value = '';
    if (mode === 'group') void discoverContacts();
  }
  async function discoverContacts() {
    const generation = getSessionGeneration();
    isLoadingContacts.value = true;
    error.value = '';
    lookupUser.value = null;
    friendQuery.value = '';
    friendForm.value = { target_user_public_id: '', message: '' };
    discoverableUsers.value = [];
    try {
      if (IS_DEMO_MODE) {
        discoverableUsers.value = [
          { public_id: 'demo-new-friend', nickname: '顾星河', username: 'xinghe' },
        ];
        return;
      }
      const groups: GroupSummary[] = [];
      let cursor: number | undefined;
      const seen = new Set<number>();
      let hasNextPage = true;
      do {
        const page = await request<CursorPage<GroupSummary>>(
          '/groups?' +
            new URLSearchParams({ limit: '100', ...(cursor ? { cursor: String(cursor) } : {}) }),
        );
        groups.push(...page.items);
        if (!page.has_more || !page.next_cursor || seen.has(page.next_cursor)) break;
        cursor = page.next_cursor;
        seen.add(page.next_cursor);
        hasNextPage = page.has_more;
      } while (hasNextPage);
      const people = new Map<string, User>();
      let failed = 0;
      for (let i = 0; i < groups.length; i += 8) {
        const result = await Promise.allSettled(
          groups
            .slice(i, i + 8)
            .map((g) => request<User[]>(`/groups/${encodeURIComponent(g.public_id)}/members`)),
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
      if (generation === getSessionGeneration()) {
        discoverableUsers.value = [...people.values()];
        if (failed) error.value = '部分群联系人暂时无法加载，请稍后重试。';
      }
    } catch (e) {
      if (generation === getSessionGeneration()) error.value = (e as Error).message;
    } finally {
      isLoadingContacts.value = false;
    }
  }
  function chooseContact(person: User) {
    lookupUser.value = person;
    friendForm.value.target_user_public_id = person.public_id;
  }

  return { lookupSequence, lookupAccount, switchContacts, discoverContacts, chooseContact };
}
