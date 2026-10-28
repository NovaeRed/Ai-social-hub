<script setup lang="ts">
import AccountSecurity from '../components/AccountSecurity.vue';
import ThemePreferences from '../components/ThemePreferences.vue';
import { Check } from 'lucide-vue-next';
import { toRefs } from 'vue';
import { useWorkspaceStore } from '../stores/workspace';
const { user, isBusy, profile, resetSession, notify, load, saveProfile } =
  toRefs(useWorkspaceStore());
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>个人设置</h1>
    </div>
  </div>
  <ThemePreferences />
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
        <input v-model="profile.ai_analysis_enabled" type="checkbox" aria-label="AI 分析授权" />
      </div>
      <button class="primary" :disabled="isBusy"><Check :size="16" />保存设置</button>
    </form>
  </section>
  <AccountSecurity :user="user" @changed="load" @notify="notify" @logout="resetSession" />
</template>
