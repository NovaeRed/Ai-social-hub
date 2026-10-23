<script setup lang="ts">
import { redactIdentifiers } from '../lib/presentation';
import { ref, watch } from 'vue';
import { LockKeyhole, ShieldCheck, X, Image } from 'lucide-vue-next';
import { demo, post, request, clearTokens, type User } from '../lib/api';
const props = defineProps<{ user: User }>(),
  emit = defineEmits<{ changed: []; notify: [message: string]; logout: [] }>();
const password = ref({ current_password: '', new_password: '', repeat: '' }),
  extra = ref({ phone: props.user.phone || '', avatarUrl: props.user.avatar_url || '' }),
  error = ref(''),
  errorScope = ref(''),
  busy = ref(false),
  confirmClear = ref(false);
const strong = (v: string) =>
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/.test(v);
watch(
  () => props.user,
  (u) => {
    extra.value = { phone: u.phone || '', avatarUrl: u.avatar_url || '' };
  },
);
async function act(scope: string, fn: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  errorScope.value = scope;
  try {
    await fn();
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function saveExtra() {
  await act('contact', async () => {
    if (extra.value.phone && !/^1[3-9]\d{9}$/.test(extra.value.phone))
      throw new Error('请填写有效的中国大陆手机号');
    if (extra.value.avatarUrl && !/^(https?:\/\/|\/)/.test(extra.value.avatarUrl))
      throw new Error('头像请使用 http/https 地址或站内路径');
    if (!demo) await post('/users/me', extra.value, 'PATCH');
    emit('changed');
    emit('notify', '联系方式和头像已保存');
  });
}
async function changePassword() {
  await act('password', async () => {
    if (!strong(password.value.new_password))
      throw new Error(
        '新密码至少 8 位，包含大小写字母、数字及 @$!%*?& 中的特殊字符，仅支持这些字符',
      );
    if (password.value.new_password !== password.value.repeat)
      throw new Error('两次输入的新密码不一致');
    if (password.value.new_password === password.value.current_password)
      throw new Error('新密码不能与当前密码相同');
    if (!demo)
      await post('/users/me/password', {
        current_password: password.value.current_password,
        new_password: password.value.new_password,
      });
    password.value = { current_password: '', new_password: '', repeat: '' };
    emit('notify', '密码已修改，请重新登录');
    clearTokens();
    emit('logout');
  });
}
async function clearPersona() {
  await act('persona', async () => {
    if (!demo) await request('/users/me/ai-persona', { method: 'DELETE' });
    confirmClear.value = false;
    emit('notify', '画像清理任务已受理，后台完成后生效');
  });
}
</script>
<template>
  <section class="card account-section">
    <div class="section-heading">
      <h3><Image :size="17" /> 头像与联系方式</h3>
      <span class="pill">{{ user.vip_level || '个人账户' }}</span>
    </div>
    <form @submit.prevent="saveExtra">
      <label
        >手机号<input
          v-model="extra.phone"
          type="tel"
          autocomplete="tel"
          placeholder="用于账户联系" /></label
      ><label
        >头像地址<input v-model="extra.avatarUrl" placeholder="https://… 或 /uploads/…"
      /></label>
      <p class="field-note">支持已有图片地址；当前后端未提供头像上传接口。</p>
      <p v-if="error && errorScope === 'contact'" class="inline-error" role="alert">
        {{ redactIdentifiers(error) }}
      </p>
      <button class="primary" :disabled="busy">保存联系方式</button>
    </form>
  </section>
  <section class="card account-section">
    <div class="section-heading">
      <h3><LockKeyhole :size="17" /> 账户安全</h3>
    </div>
    <form @submit.prevent="changePassword">
      <label
        >当前密码<input
          v-model="password.current_password"
          type="password"
          autocomplete="current-password"
          required /></label
      ><label
        >新密码<input
          v-model="password.new_password"
          type="password"
          autocomplete="new-password"
          required
          minlength="8" /></label
      ><label
        >再次输入新密码<input
          v-model="password.repeat"
          type="password"
          autocomplete="new-password"
          required
      /></label>
      <p class="field-note">至少 8 位，含大小写字母、数字及 @$!%*?& 中的特殊字符。</p>
      <p v-if="error && errorScope === 'password'" class="inline-error" role="alert">
        {{ redactIdentifiers(error) }}
      </p>
      <button class="secondary" :disabled="busy">修改密码并重新登录</button>
    </form>
  </section>
  <section class="card account-section">
    <div class="section-heading">
      <h3><ShieldCheck :size="18" /> 个人 AI 画像</h3>
    </div>
    <p class="muted">
      你可以清除已生成的个人画像。清理通过后台任务执行，关闭分析授权可在上方资料设置中操作。
    </p>
    <button class="danger secondary" @click="confirmClear = true">清除个人画像</button>
  </section>
  <div v-if="confirmClear" class="modal-overlay" @click.self="confirmClear = false">
    <section class="modal card" role="dialog" aria-modal="true" aria-labelledby="privacy-title">
      <button class="icon-button close" aria-label="关闭弹窗" @click="confirmClear = false">
        <X :size="20" />
      </button>
      <h2 id="privacy-title">确认清除个人画像？</h2>
      <p>将向后台提交清理任务，已生成的画像数据会在任务完成后清除。</p>
      <div class="form-actions">
        <button class="secondary" :disabled="busy" @click="confirmClear = false">取消</button
        ><button class="primary" :disabled="busy" @click="clearPersona">确认清除</button>
      </div>
      <p v-if="error && errorScope === 'persona'" class="inline-error" role="alert">
        {{ redactIdentifiers(error) }}
      </p>
    </section>
  </div>
</template>
