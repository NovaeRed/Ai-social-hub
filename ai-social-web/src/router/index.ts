import { createRouter, createWebHashHistory } from 'vue-router';
import { IS_DEMO_MODE } from '../services/http';
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: () => import('../views/OverviewView.vue') },
    { path: '/groups', component: () => import('../views/GroupsView.vue') },
    { path: '/insights', component: () => import('../views/InsightsView.vue') },
    { path: '/messages', component: () => import('../views/MessagesView.vue') },
    { path: '/friends', component: () => import('../views/FriendsView.vue') },
    { path: '/schedules', component: () => import('../views/SchedulesView.vue') },
    { path: '/ai', component: () => import('../views/AiAssistantView.vue') },
    { path: '/settings', component: () => import('../views/SettingsView.vue') },
    { path: '/login', component: () => import('../views/LoginView.vue') },
  ],
});
router.beforeEach((to) =>
  !IS_DEMO_MODE && !sessionStorage.getItem('access_token') && to.path !== '/login'
    ? '/login'
    : true,
);
