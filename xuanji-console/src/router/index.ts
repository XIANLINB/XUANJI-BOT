import { createRouter, createWebHashHistory } from 'vue-router'
import api from '../api'

// 路由级 code-split：每个页面独立 chunk，首屏只加载当前页
const Dashboard = () => import('../views/Dashboard.vue')
const Bots = () => import('../views/Bots.vue')
const Groups = () => import('../views/Groups.vue')
const Friends = () => import('../views/Friends.vue')
const Messages = () => import('../views/Messages.vue')
const Events = () => import('../views/Events.vue')
const Database = () => import('../views/Database.vue')
const Logs = () => import('../views/Logs.vue')
const Health = () => import('../views/Health.vue')
const Settings = () => import('../views/Settings.vue')
const Setup = () => import('../views/Setup.vue')
const Plugins = () => import('../views/Plugins.vue')
const Permission = () => import('../views/Permission.vue')
const OneBot = () => import('../views/OneBot.vue')

export const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/setup', name: 'setup', component: Setup },
  { path: '/dashboard', name: 'dashboard', component: Dashboard, meta: { title: '仪表盘' } },
  { path: '/bots', name: 'bots', component: Bots, meta: { title: '机器人管理' } },
  { path: '/groups', name: 'groups', component: Groups, meta: { title: '群列表' } },
  { path: '/friends', name: 'friends', component: Friends, meta: { title: '好友' } },
  { path: '/messages', name: 'messages', component: Messages, meta: { title: '消息监控' } },
  { path: '/events', name: 'events', component: Events, meta: { title: '系统事件' } },
  { path: '/database', name: 'database', component: Database, meta: { title: '数据库' } },
  { path: '/plugins', name: 'plugins', component: Plugins, meta: { title: '插件管理' } },
  { path: '/permission', name: 'permission', component: Permission, meta: { title: '权限管理' } },
  { path: '/onebot', name: 'onebot', component: OneBot, meta: { title: 'OneBot 接入' } },
  { path: '/health', name: 'health', component: Health, meta: { title: '运行健康' } },
  { path: '/settings', name: 'settings', component: Settings, meta: { title: '运行设置' } },
  { path: '/logs', name: 'logs', component: Logs, meta: { title: '运行日志' } }
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 首启引导守卫：未完成初始化时，所有非 /setup 路由都重定向到引导页；
// 已完成则放行。权威来源是后端 /setup/status —— 每次导航都重新求证，
// 避免模块级缓存/浏览器还原标签页导致"已完成却再次进入向导"。
let completed = false

router.beforeEach(async (to) => {
  const localDone = !!(window as any).__xuanjiSetupDone
  // 每次导航都向后端求证（status 是唯一权威），失败时保持上次结论
  try {
    const r: any = await api.setupStatus()
    completed = !!r?.completed
  } catch {
    // 保持 last known
  }
  if (to.name === 'setup') {
    if (completed || localDone) return { name: 'dashboard' }
    return true
  }
  if (completed || localDone) return true
  return { name: 'setup' }
})
