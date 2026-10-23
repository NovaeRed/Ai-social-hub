import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import './style.css'
const router = createRouter({
  history: createWebHashHistory(),
  routes: ['/', '/messages', '/friends', '/groups', '/schedules', '/ai', '/insights', '/settings', '/login'].map(
    (path) => ({ path, component: { template: '<span />' } }),
  ),
})
createApp(App).use(router).mount('#app')
