<script setup lang="ts">
import { IS_DEMO_MODE } from '../services/http';
import { ArrowRight } from 'lucide-vue-next';
import { toRefs } from 'vue';
import { useWorkspaceStore } from '../stores/workspace';
const { isBusy, isRegistering, login, authenticate } = toRefs(useWorkspaceStore());
</script>
<template>
  <div class="login-wrap">
    <section class="card login-card">
      <h1>{{ isRegistering ? '注册账号' : '登录共鸣' }}</h1>
      <form @submit.prevent="authenticate">
        <label>用户名<input v-model="login.username" autocomplete="username" required /></label
        ><label v-if="isRegistering"
          >昵称（选填）<input
            v-model="login.nickname"
            autocomplete="nickname"
            maxlength="40"
            placeholder="留空则使用用户名" /></label
        ><label v-if="isRegistering"
          >邮箱<input v-model="login.email" type="email" required /></label
        ><label
          >密码<input
            v-model="login.password"
            type="password"
            :autocomplete="isRegistering ? 'new-password' : 'current-password'"
            required
            :minlength="isRegistering && !IS_DEMO_MODE ? 8 : 1" /></label
        ><button class="primary" :disabled="isBusy">
          {{ isRegistering ? '注册并登录' : '登录' }}<ArrowRight :size="17" />
        </button>
      </form>
      <button class="text-button" @click="isRegistering = !isRegistering">
        {{ isRegistering ? '已有账号？返回登录' : '还没有账号？立即注册' }}
      </button>
      <p v-if="isRegistering && !IS_DEMO_MODE" class="field-note">
        密码至少 8 位，含大小写字母、数字及 @$!%*?& 中的特殊字符。
      </p>
      <p v-if="IS_DEMO_MODE" class="muted">演示模式：填写任意用户名与密码。</p>
    </section>
  </div>
</template>

<style scoped>
.login-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 76px);
  padding: 30px;
}
.login-card {
  width: 430px;
  padding: 35px;
}
.login-card > h1 {
  margin-bottom: var(--space-lg);
}
.login-card > p {
  font-size: 12px;
  color: var(--theme-text-9298a2, #9298a2);
  margin: 15px 0 25px;
}
.login-card .text-button {
  margin-top: 20px;
}
@media (max-width: 600px) {
  .login-wrap {
    padding: 20px;
    min-height: calc(100vh - 64px);
  }
}
@media (max-width: 600px) {
  .login-card {
    padding: 27px;
  }
}
@media (max-width: 600px) {
  .login-card h1 {
    font-size: 25px;
  }
}
</style>
