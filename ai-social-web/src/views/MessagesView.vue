<script setup lang="ts">
import { redactIdentifiers } from '../lib/presentation';
import { IS_DEMO_MODE } from '../services/http';
import MessageActions from '../components/MessageActions.vue';
import ConversationRow from '../components/ConversationRow.vue';
import ChatAssistant from '../components/ChatAssistant.vue';
import {
  MessagesSquare,
  Users,
  Sparkles,
  Search,
  Plus,
  Send,
  Paperclip,
  X,
  FileText,
} from 'lucide-vue-next';
import { toRefs } from 'vue';
import { useWorkspaceStore } from '../stores/workspace';
const {
  user,
  conversations,
  models,
  selected,
  query,
  draft,
  isBusy,
  modal,
  conversationFilter,
  conversationPage,
  chatAssistant,
  isChatAiBusy,
  pendingFile,
  fileError,
  uploadedFileId,
  moreConversations,
  groupDetails,
  getFile,
  messagePages,
  olderMessages,
  pinnedChats,
  removalTarget,
  pinConversation,
  hideMessages,
  forwarded,
  filteredConversations,
  activeConversation,
  currentMessages,
  notify,
  go,
  openConversation,
  send,
  upload,
  fileSize,
  sendFile,
  summarizeChat,
  polishDraft,
} = toRefs(useWorkspaceStore());
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>消息</h1>
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
        <Search :size="17" /><input v-model="query" placeholder="搜索会话" aria-label="搜索会话" />
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
        v-if="conversationPage.has_more && !IS_DEMO_MODE"
        class="text-button"
        :disabled="isBusy"
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
        <button class="text-button" :disabled="isChatAiBusy" @click="summarizeChat">
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
          :disabled="isBusy"
          @click="olderMessages"
        >
          加载更早消息
        </button>
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
        @busy="isChatAiBusy = $event"
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
            :disabled="isBusy"
            @click="
              pendingFile = null;
              uploadedFileId = '';
              fileError = '';
            "
          >
            <X :size="17" />
          </button>
          <button type="button" class="primary" :disabled="isBusy" @click="sendFile">
            {{ isBusy ? '正在发送…' : '发送文件' }}
          </button>
        </section>
        <p v-if="fileError" class="inline-error" role="alert">
          {{ redactIdentifiers(fileError) }}
        </p>
        <textarea
          v-model="draft"
          :readonly="isChatAiBusy"
          placeholder="输入消息…"
          aria-label="消息内容"
          @keydown.ctrl.enter.prevent="send"
        />
        <div>
          <label class="icon-button upload" title="上传文件"
            ><Paperclip :size="19" /><input
              type="file"
              :disabled="isBusy"
              aria-label="上传聊天文件"
              @change="upload" /></label
          ><button type="button" class="text-button" :disabled="isChatAiBusy" @click="polishDraft">
            <Sparkles :size="16" />AI 润色</button
          ><button
            type="button"
            class="text-button"
            :disabled="isChatAiBusy"
            @click="chatAssistant?.run('smart-reply')"
          >
            回复建议</button
          ><span class="muted">Ctrl + Enter 发送</span
          ><button class="primary" :disabled="isBusy || isChatAiBusy || !draft.trim()">
            发送<Send :size="16" />
          </button>
        </div>
      </form>
    </div>
    <div v-else class="empty chat-placeholder">
      <MessagesSquare :size="48" />
      <h3>选择一段对话</h3>
      <p>从左侧选择会话，或从好友列表发起聊天。</p>
      <button class="primary" @click="go('/friends')">查看好友</button>
    </div>
  </section>
</template>
