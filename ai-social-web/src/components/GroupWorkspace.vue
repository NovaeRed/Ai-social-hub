<script setup lang="ts">
import { displayUserName } from '../lib/presentation';
import { IS_DEMO_MODE } from '../services/http';
import { redactIdentifiers } from '../lib/presentation';
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
import { useGroupWorkspace } from '../composables/useGroupWorkspace';
import type { User } from '../services/http';
import type { Group } from '../composables/useGroupWorkspace';
const props = defineProps<{ user: User; friends: User[] }>();
const emit = defineEmits<{ chat: [group: Group]; changed: []; notify: [message: string] }>();
const {
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
} = useGroupWorkspace(props, emit);
</script>
<template>
  <div class="page-heading">
    <div><h1>群聊</h1></div>
    <button class="primary" @click="create"><Plus :size="17" />创建群聊</button>
  </div>
  <p v-if="error" class="inline-error" role="alert">{{ redactIdentifiers(error) }}</p>
  <div class="group-toolbar">
    <button v-if="detail" class="secondary" @click="closeDetail">返回群列表</button>
    <form v-else class="search-field" @submit.prevent="load()">
      <Search :size="17" /><input
        v-model="keyword"
        placeholder="搜索群聊名称"
        aria-label="搜索群聊名称"
      /><button class="text-button" :disabled="isLoading">搜索</button>
    </form>
    <button class="secondary" :disabled="isLoading" @click="load()">
      <RefreshCw :size="15" />刷新
    </button>
  </div>
  <div class="group-workspace" :class="{ 'has-detail': detail }">
    <section v-if="!detail" class="group-grid">
      <button
        v-for="(g, i) in IS_DEMO_MODE ? groups.filter((g) => g.name.includes(keyword)) : groups"
        :key="g.public_id"
        class="card group-card"
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
        <h3>{{ isLoading ? '正在加载群聊…' : '给好想法一个聚集地' }}</h3>
        <p>创建群聊，邀请好友加入。</p>
        <button class="primary" @click="create">创建第一个群聊</button>
      </div>
      <button v-if="hasMore" class="secondary" :disabled="isLoading" @click="load(true)">
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
        <button class="primary" :disabled="isBusy" @click="emit('chat', detail)">
          <MessageCircle :size="16" />进入群聊</button
        ><button class="secondary" @click="openRename"><Settings :size="16" />编辑群名</button>
      </div>
      <div class="group-announcement">
        <div class="section-heading">
          <strong>群公告</strong>
          <button
            v-if="role === 'OWNER'"
            class="text-button"
            :disabled="isBusy"
            @click="editAnnouncement"
          >
            编辑公告
          </button>
        </div>
        <p>{{ detail.announcement || '暂时没有群公告' }}</p>
      </div>
      <div class="section-heading">
        <h3>
          群成员 <span class="muted">{{ actualCount }}</span>
        </h3>
        <button class="text-button" @click="openInvite"><UserPlus :size="15" />邀请好友</button>
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
              :disabled="isBusy"
              @click="confirm('promote', m)"
            >
              设为管理员</button
            ><button
              v-if="role === 'OWNER' && m.role === 'ADMIN'"
              class="text-button"
              :disabled="isBusy"
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
    tabindex="-1"
    @click.self="modal = ''"
    @keydown.esc="modal = ''"
  >
    <section
      class="modal card"
      role="dialog"
      aria-modal="true"
      aria-labelledby="group-dialog-title"
    >
      <button class="icon-button close" aria-label="关闭弹窗" @click="modal = ''">
        <X :size="20" /></button
      ><template v-if="modal === 'announcement'">
        <h2 id="group-dialog-title">编辑群公告</h2>
        <form @submit.prevent="saveAnnouncement">
          <label
            >公告内容<textarea
              v-model="announcementDraft"
              maxlength="2000"
              rows="6"
              placeholder="填写成员需要了解的事项"
            />
          </label>
          <p class="muted">{{ announcementDraft.length }} / 2000 字，留空可清除公告。</p>
          <button class="primary" :disabled="isBusy">保存公告</button>
        </form> </template
      ><template v-else-if="modal === 'create' || modal === 'rename'"
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
          ><button class="primary" :disabled="isBusy">
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
        <button class="primary" :disabled="isBusy || !picked.length" @click="invite">
          邀请 {{ picked.length }} 位好友
        </button></template
      ><template v-else
        ><h2 id="group-dialog-title">确认{{ confirmTitle }}？</h2>
        <p v-if="target">操作对象：{{ displayUserName(target, '群成员') }}</p>
        <p v-if="modal === 'transfer'">转让后你将成为普通成员，群主管理权将交给对方。</p>
        <p v-if="modal === 'dissolve'">解散后所有成员都将失去此群聊的访问权限。</p>
        <p v-if="modal === 'leave'">退出后需要由其他成员重新邀请，才能加入群聊。</p>
        <div class="form-actions">
          <button class="secondary" :disabled="isBusy" @click="modal = ''">取消</button
          ><button class="primary" :disabled="isBusy" @click="mutate">确认操作</button>
        </div></template
      >
      <p v-if="error" class="inline-error" role="alert">{{ redactIdentifiers(error) }}</p>
    </section>
  </div>
</template>

<style scoped src="../styles/group-workspace.css"></style>
