<script setup lang="ts">
import { RouterView } from 'vue-router';
import { Sun, Moon } from 'lucide-vue-next';
import { useThemeStore } from '../stores/theme';
import WorkspaceDialogs from '../components/WorkspaceDialogs.vue';
import { redactIdentifiers } from '../lib/presentation';
import { IS_DEMO_MODE } from '../services/http';
import {
  Settings,
  ArrowUpRight,
  ChevronRight,
  Bell,
  X,
  LogOut,
  Check,
  Menu,
  Flower2,
} from 'lucide-vue-next';
import { toRefs } from 'vue';
import { useWorkspaceStore } from '../stores/workspace';
const theme = useThemeStore();
const {
  route,
  nav,
  user,
  isLoading,
  error,
  toast,
  modal,
  isSidebarOpen,
  realtime,
  unread,
  pageTitle,
  go,
  logout,
} = toRefs(useWorkspaceStore());
</script>
<template>
  <div class="app-shell" @keydown.esc="isSidebarOpen = false">
    <button
      v-if="isSidebarOpen"
      class="sidebar-backdrop"
      aria-label="收起侧边栏"
      @click="isSidebarOpen = false"
    />
    <aside id="main-sidebar" :class="['sidebar', { expanded: isSidebarOpen }]">
      <a class="brand" href="#/"
        ><img src="/favicon.svg" alt="共鸣标志" /><span>共鸣<small>AI SOCIAL HUB</small></span></a
      >
      <div class="workspace-label">你的社交空间 <span>PERSONAL</span></div>
      <nav aria-label="主要导航">
        <button
          v-for="n in nav"
          :key="n.path"
          :class="{ active: route.path === n.path }"
          @click="go(n.path)"
        >
          <component :is="n.icon" :size="20" /><span>{{ n.label }}</span
          ><b v-if="n.path === '/messages' && unread" class="count">{{ unread }}</b
          ><small v-if="n.path === '/ai'" class="new-label">NEW</small>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <button :class="{ active: route.path === '/settings' }" @click="go('/settings')">
          <Settings :size="19" />个人设置</button
        ><button @click="modal = 'help'">
          <Flower2 :size="19" />使用帮助<ArrowUpRight :size="15" />
        </button>
        <div class="sidebar-user">
          <span class="avatar self">{{ user.nickname.slice(0, 1) || '访' }}</span
          ><span
            ><strong>{{ user.nickname || '访客' }}</strong
            ><small>{{ IS_DEMO_MODE ? '演示体验中' : '个人账户' }}</small></span
          ><button aria-label="退出登录" @click="logout"><LogOut :size="17" /></button>
        </div>
      </div>
    </aside>
    <main>
      <header class="topbar">
        <div class="breadcrumb">
          <button
            class="mobile-toggle icon-button"
            aria-label="展开菜单"
            aria-controls="main-sidebar"
            :aria-expanded="isSidebarOpen"
            @click="isSidebarOpen = !isSidebarOpen"
          >
            <Menu :size="20" /></button
          ><span>我的工作台</span><ChevronRight :size="14" /><strong>{{ pageTitle }}</strong>
        </div>
        <div class="top-actions">
          <button
            class="icon-button theme-toggle"
            :aria-label="theme.toggleLabel"
            :title="theme.toggleLabel"
            :aria-pressed="theme.isDark"
            @click="theme.toggleTheme"
          >
            <Sun v-if="theme.isDark" :size="20" /><Moon v-else :size="20" />
          </button>
          <span class="connection">{{ IS_DEMO_MODE ? '演示模式' : realtime }}</span
          ><button class="icon-button" aria-label="好友申请" @click="modal = 'requests'">
            <Bell :size="20" /></button
          ><button class="avatar self small" aria-label="个人设置" @click="go('/settings')">
            {{ user.nickname.slice(0, 1) || '访' }}
          </button>
        </div>
      </header>
      <div v-if="error && !modal" class="error" role="alert">
        {{ redactIdentifiers(error)
        }}<button aria-label="关闭错误" @click="error = ''"><X :size="16" /></button>
      </div>
      <div v-if="isLoading" class="loading" role="status">正在加载你的空间…</div>
      <div :class="route.path === '/login' ? undefined : 'page-content'"><RouterView /></div>
    </main>
    <Transition name="toast"
      ><div v-if="toast" class="toast" role="status">
        <Check :size="17" />{{ toast }}
      </div></Transition
    >
    <WorkspaceDialogs />
  </div>
</template>
