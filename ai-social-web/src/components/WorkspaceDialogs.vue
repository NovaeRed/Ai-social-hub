<script setup lang="ts">
import { displayUserName } from '../lib/presentation';
import { redactIdentifiers } from '../lib/presentation';
import { IS_DEMO_MODE } from '../services/http';
import DateTimeField from '../components/DateTimeField.vue';
import { Search, ArrowRight, Send, X, Check } from 'lucide-vue-next';
import { toRefs } from 'vue';
import { useWorkspaceStore } from '../stores/workspace';
const {
  friends,
  isBusy,
  error,
  modal,
  friendForm,
  groupForm,
  scheduleForm,
  editing,
  lookupUser,
  contact,
  requestKeyword,
  requestTab,
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
  requestStatus,
  visibleRequests,
  deleteContact,
  cancelRequest,
  clearMessages,
  candidates,
  pickedCandidates,
  useCandidates,
  trapFocus,
  removalTarget,
  removeConversation,
  chatWith,
  saveSchedule,
  removeSchedule,
  addFriend,
  acceptRequest,
  createGroup,
} = toRefs(useWorkspaceStore());
</script>
<template>
  <div
    v-if="modal"
    class="modal-overlay"
    tabindex="-1"
    @click.self="modal = ''"
    @keydown="trapFocus"
    @keydown.esc="modal = ''"
  >
    <section class="modal card" role="dialog" aria-modal="true" aria-labelledby="modal-title">
      <button class="close icon-button" aria-label="关闭弹窗" @click="modal = ''">
        <X :size="21" />
      </button>
      <template v-if="modal === 'schedule'"
        ><h2 id="modal-title">{{ editing ? '编辑日程' : '新建日程' }}</h2>
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
              :disabled="isBusy"
              @click="removeSchedule"
            >
              删除此日程</button
            ><button class="primary" :disabled="isBusy">保存日程</button>
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
                isLookupBusy = false;
              "
          /></label>
          <p class="field-note">精确查找后请核对昵称；联系方式不会展示在查找结果中。</p>
          <button class="secondary" :disabled="isLookupBusy || !userCode.trim()">
            {{ isLookupBusy ? '查找中…' : '查找用户' }}
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
              <p v-if="isLoadingContacts" class="muted" role="status">正在查找同群联系人…</p>
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
              <p v-if="!isLoadingContacts && !matchingContacts.length" class="field-note">
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
              isBusy ||
              isLookupBusy ||
              (contactMode === 'group' && isLoadingContacts) ||
              !lookupUser
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
            ><input v-model="groupForm.members" type="checkbox" :value="f.public_id" />{{
              f.nickname
            }}</label
          >
          <p v-if="!friends.length">可以先创建群聊，稍后再邀请好友。</p>
          <button class="primary" :disabled="isBusy">创建群聊</button>
        </form></template
      >
      <template v-else-if="modal === 'requests'"
        ><h2 id="modal-title">添加好友</h2>
        <div class="conversation-tabs">
          <button :class="{ active: requestTab === 'incoming' }" @click="requestTab = 'incoming'">
            收到的申请</button
          ><button :class="{ active: requestTab === 'outgoing' }" @click="requestTab = 'outgoing'">
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
            ·
            {{ new Date(r.created_at).toLocaleString('zh-CN') }}</small
          >
          <p>{{ r.message }}</p>
          <div v-if="r.status === 'PENDING' && requestTab === 'incoming'" class="inline-actions">
            <button class="primary" :disabled="isBusy" @click="acceptRequest(r, true)">接受</button
            ><button class="secondary" :disabled="isBusy" @click="acceptRequest(r, false)">
              拒绝
            </button>
          </div>
          <button
            v-else-if="r.status === 'PENDING'"
            class="secondary"
            :disabled="isBusy"
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
          ><button class="primary" :disabled="isBusy" @click="deleteContact">确认删除好友</button>
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
          ><button class="primary" :disabled="isBusy" @click="clearMessages">确认清空</button>
        </div></template
      >
      <template v-else-if="modal === 'candidates'"
        ><h2 id="modal-title">选择要总结的消息</h2>
        <div class="pick-list candidate-list">
          <label v-for="(m, i) in candidates" :key="m.public_id || m.content" class="checkbox-label"
            ><input v-model="pickedCandidates" type="checkbox" :value="i" /><span
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
            IS_DEMO_MODE
              ? '当前为演示模式，数据仅保留在本次页面会话中。AI 结果为固定示例。'
              : '当前为真实 API 模式，操作将保存到后端。'
          }}
        </p>
        <button class="primary" @click="modal = ''">开始探索<ArrowRight :size="16" /></button
      ></template>
      <p v-if="error" class="modal-error" role="alert">{{ redactIdentifiers(error) }}</p>
    </section>
  </div>
</template>
