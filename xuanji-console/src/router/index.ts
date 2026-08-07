import { createRouter, createWebHashHistory } from 'vue-router'
import api from '../api'

// 路由级 code-split：每个页面独立 chunk，首屏只加载当前页
const Dashboard = () => import('../views/Dashboard.vue')
const Bots = () => import('../views/Bots.vue')
const Groups = () => import('../views/Groups.vue')
const Friends = () => import('../views/Friends.vue')
const GroupMessages = () => import('../views/GroupMessages.vue')
const C2cMessages = () => import('../views/C2cMessages.vue')
const Events = () => import('../views/Events.vue')
const Database = () => import('../views/Database.vue')
const Logs = () => import('../views/Logs.vue')
const Health = () => import('../views/Health.vue')
const Settings = () => import('../views/Settings.vue')
const Setup = () => import('../views/Setup.vue')
const Login = () => import('../views/Login.vue')
const Plugins = () => import('../views/PluginPage.vue')
const PluginMarket = () => import('../views/PluginMarket.vue')
const Security = () => import('../views/Security.vue')
const Backup = () => import('../views/Backup.vue')
const Scheduler = () => import('../views/Scheduler.vue')
const Stats = () => import('../views/Stats.vue')
const Cache = () => import('../views/Cache.vue')
const Files = () => import('../views/Files.vue')
const Alert = () => import('../views/Alert.vue')
const Risk = () => import('../views/Risk.vue')
const Permission = () => import('../views/Permission.vue')
const OneBot = () => import('../views/OneBot.vue')

export const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/setup', name: 'setup', component: Setup },
  { path: '/login', name: 'login', component: Login },
  { path: '/dashboard', name: 'dashboard', component: Dashboard, meta: { title: '仪表盘' } },
  {
    path: '/data',
    name: 'data-center',
    redirect: '/data/stats',
    meta: { title: '数据中心' },
    children: [
      { path: 'stats', name: 'stats', component: Stats, meta: { title: '聚合统计' } },
      { path: 'cache', name: 'cache', component: Cache, meta: { title: '缓存清理' } },
      { path: 'files', name: 'files', component: Files, meta: { title: '文件存储' } }
    ]
  },
  {
    path: '/center',
    name: 'center-admin',
    redirect: '/center/qqbot',
    meta: { title: '管理中心' },
    children: [
      { path: 'qqbot', name: 'qqbot', component: Bots, meta: { title: 'QQBOT' } },
      { path: 'onebot', name: 'onebot', component: OneBot, meta: { title: 'OneBot' } },
      { path: 'permission', name: 'permission', component: Permission, meta: { title: '权限管理' } },
      { path: 'database', name: 'database', component: Database, meta: { title: '数据库' } }
    ]
  },
  {
    path: '/chat-groups',
    name: 'chat-groups',
    redirect: '/chat-groups/list',
    meta: { title: '群聊管理' },
    children: [
      { path: 'list', name: 'groups', component: Groups, meta: { title: '群聊列表' } },
      { path: 'messages', name: 'group-messages', component: GroupMessages, meta: { title: '群聊消息' } }
    ]
  },
  {
    path: '/chat-c2c',
    name: 'chat-c2c',
    redirect: '/chat-c2c/friends',
    meta: { title: '单聊管理' },
    children: [
      { path: 'friends', name: 'friends', component: Friends, meta: { title: '单聊列表' } },
      { path: 'messages', name: 'c2c-messages', component: C2cMessages, meta: { title: '单聊消息' } }
    ]
  },
  {
    path: '/plugins',
    name: 'plugin-admin',
    redirect: '/plugins/market',
    meta: { title: '插件管理' },
    children: [
      // 插件市场（卡片式管理 + 扫描入口）
      { path: 'market', name: 'plugin-market', component: PluginMarket, meta: { title: '插件市场' } }
    ]
  },
  // 插件详情页（菜单动态生成，每个插件一个入口，标题 = 插件 name）
  { path: '/plugins/p/:pluginId', name: 'plugin-page', component: Plugins },
  {
    path: '/ops',
    name: 'ops-admin',
    redirect: '/ops/security',
    meta: { title: '运维中心' },
    children: [
      { path: 'security', name: 'security', component: Security, meta: { title: '安全管理' } },
      { path: 'health', name: 'health', component: Health, meta: { title: '运行健康' } },
      { path: 'backup', name: 'backup', component: Backup, meta: { title: '备份恢复' } },
      { path: 'alert', name: 'alert', component: Alert, meta: { title: '预警中心' } },
      { path: 'risk', name: 'risk', component: Risk, meta: { title: '风控中心' } }
    ]
  },
  {
    path: '/logs-center',
    name: 'logs-center',
    redirect: '/logs-center/logs',
    meta: { title: '日志中心' },
    children: [
      { path: 'logs', name: 'logs', component: Logs, meta: { title: '运行日志' } },
      { path: 'events', name: 'events', component: Events, meta: { title: '事件日志' } }
    ]
  },
  { path: '/scheduler', name: 'scheduler', component: Scheduler, meta: { title: '定时任务' } },
  { path: '/settings', name: 'settings', component: Settings, meta: { title: '运行设置' } },
  // 旧路径重定向（菜单重构前收藏的地址仍可用）
  { path: '/groups', redirect: '/chat-groups/list' },
  { path: '/friends', redirect: '/chat-c2c/friends' },
  { path: '/messages/group', redirect: '/chat-groups/messages' },
  { path: '/messages/c2c', redirect: '/chat-c2c/messages' },
  { path: '/bots', redirect: '/center/qqbot' },
  { path: '/onebot', redirect: '/center/onebot' },
  { path: '/permission', redirect: '/center/permission' },
  { path: '/database', redirect: '/center/database' },
  { path: '/security', redirect: '/ops/security' },
  { path: '/health', redirect: '/ops/health' },
  { path: '/backup', redirect: '/ops/backup' },
  { path: '/logs', redirect: '/logs-center/logs' },
  { path: '/events', redirect: '/logs-center/events' }
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 首启引导守卫：未完成初始化时，所有非 /setup 路由都重定向到引导页；
// 已完成则要求已登录（会话 cookie 有效），未登录跳登录页。
// 权威来源是后端 /setup/status 与 /auth/me —— 每次导航都重新求证，
// 避免模块级缓存/浏览器还原标签页导致的状态错位。
let completed = false

router.beforeEach(async (to) => {
  const localDone = !!(window as any).__xuanjiSetupDone
  try {
    // 并行求证：安装状态 + 登录状态
    const [s, m]: any = await Promise.all([api.setupStatus(), api.authMe()])
    completed = !!s?.completed
    const authenticated = !!m?.authenticated

    if (to.name === 'setup') {
      return completed || localDone ? { name: 'dashboard' } : true
    }
    if (to.name === 'login') return true
    if (!completed && !localDone) return { name: 'setup' }
    if (!authenticated) return { name: 'login' }
    return true
  } catch {
    // 请求失败保守处理：安装向导/登录页始终可进；其余按上次结论兜底
    if (to.name === 'setup' || to.name === 'login') return true
    if (!completed && !localDone) return { name: 'setup' }
    return { name: 'login' }
  }
})
