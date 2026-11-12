import { createRouter, createWebHashHistory } from 'vue-router'
import { auth } from './api'
import Login from './views/Login.vue'
import Layout from './views/Layout.vue'
import Calendar from './views/Calendar.vue'
import Dishes from './views/Dishes.vue'
import Members from './views/Members.vue'
import Rules from './views/Rules.vue'
import Inventory from './views/Inventory.vue'
import Templates from './views/Templates.vue'
import Stats from './views/Stats.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: Login },
    {
      path: '/',
      component: Layout,
      redirect: '/calendar',
      children: [
        { path: 'calendar', component: Calendar, meta: { title: '日历总览' } },
        { path: 'dishes', component: Dishes, meta: { title: '菜谱管理' } },
        { path: 'members', component: Members, meta: { title: '成员管理' } },
        { path: 'rules', component: Rules, meta: { title: '家庭规则' } },
        { path: 'inventory', component: Inventory, meta: { title: '库存与剩菜' } },
        { path: 'templates', component: Templates, meta: { title: '周菜单模板' } },
        { path: 'stats', component: Stats, meta: { title: '统计' } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (to.path !== '/login' && !auth.token) return '/login'
})

export default router
