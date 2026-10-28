<script setup lang="ts">
import { redactIdentifiers } from '../lib/presentation';
import { Search, Plus, ChevronRight, MessageCircle } from 'lucide-vue-next';
import { toRefs } from 'vue';
import { useWorkspaceStore } from '../stores/workspace';
const { friends, requests, query, isBusy, modal, showContact, filteredFriends, chatWith, safeUrl } =
  toRefs(useWorkspaceStore());
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>
        好友 <span class="heading-count">{{ friends.length }}</span>
      </h1>
    </div>
    <div class="inline-actions">
      <button class="secondary" @click="modal = 'requests'">
        好友申请 {{ requests.incoming.length }}</button
      ><button class="primary" @click="modal = 'friend'"><Plus :size="17" />添加好友</button>
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
        ><img v-if="safeUrl(f.avatar_url)" :src="safeUrl(f.avatar_url)" alt="好友头像" /><span
          v-else
          >{{ f.nickname.slice(-2) }}</span
        ></span
      >
      <h3>{{ f.nickname }}</h3>
      <p>{{ f.username ? `@${redactIdentifiers(f.username)}` : '好友' }}</p>
      <button class="secondary" :disabled="isBusy" @click="chatWith(f)">
        <MessageCircle :size="16" />发消息
      </button>
      <button class="text-button friend-details" :disabled="isBusy" @click="showContact(f)">
        查看资料<ChevronRight :size="13" />
      </button>
    </article>
  </div>
  <div v-if="!filteredFriends.length" class="empty card">
    暂无匹配好友，点击「添加好友」发送申请。
  </div>
</template>
