import { createRouter, createWebHashHistory } from 'vue-router'
import api from '../api'

// 路由级 code-split：每个页面独立 chunk，首屏只加载当前页
const Dashboard = () => import('../views/Dashboard.vue')
const Bots = () => import('../views/Bots.vue')
const Groups = () => import('../views/Groups.vue')
const Friends = () => import('../views/Friends.vue')
const GroupMessages = () => import('../views/GroupMessages.vue')
const OpLog = () => import('../views/OpLog.vue')
const C2cMessages = () => import('../views/C2cMessages.vue')
const Events = () => import('../views/Events.vue')
const Monitor = () => import('../views/Monitor.vue')
const Database = () => import('../views/Database.vue')
const Logs = () => import('../views/Logs.vue')
const Health = () => import('../views/Health.vue')
const Settings = () => import('../views/Settings.vue')
const Tune = () => import('../views/Tune.vue')
const Setup = () => import('../views/Setup.vue')
const Login = () => import('../views/Login.vue')
const Plugins = () => import('../views/PluginPage.vue')
const PluginMarket = () => import('../views/PluginMarket.vue')
const PluginGuide = () => import('../views/PluginGuide.vue')
const PluginReview = () => import('../views/PluginReview.vue')
const Security = () => import('../views/Security.vue')
const Backup = () => import('../views/Backup.vue')
const Scheduler = () => import('../views/Scheduler.vue')
const Stats = () => import('../views/Stats.vue')
const Cache = () => import('../views/Cache.vue')
const Files = () => import('../views/Files.vue')
const Alert = () => import('../views/Alert.vue')
const Permission = () => import('../views/Permission.vue')
const AiConfig = () => import('../views/AiConfig.vue')
const AiChat = () => import('../views/AiChat.vue')
const AiPersonas = () => import('../views/AiPersonas.vue')
const AiUsage = () => import('../views/AiUsage.vue')
const AiTools = () => import('../views/AiTools.vue')
const AiMcp = () => import('../views/AiMcp.vue')
const AiKb = () => import('../views/AiKb.vue')
const AiAudit = () => import('../views/AiAudit.vue')
const AiSummary = () => import('../views/AiSummary.vue')
const AiMemory = () => import('../views/AiMemory.vue')
const AiProviders = () => import('../views/AiProviders.vue')
const Preferences = () => import('../views/Preferences.vue')

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
      { path: 'files', name: 'files', component: Files, meta: { title: '文件存储' } },
      { path: 'database', name: 'database', component: Database, meta: { title: '数据库' } }
    ]
  },
  {
    path: '/center',
    name: 'center-admin',
    redirect: '/center/qqbot',
    meta: { title: '管理中心' },
    children: [
      { path: 'qqbot', name: 'qqbot', component: Bots, meta: { title: 'QQBOT' } },
      { path: 'permission', name: 'permission', component: Permission, meta: { title: '权限管理' } },
      { path: 'events', name: 'events', component: Events, meta: { title: '系统事件' } },
      { path: 'op-log', name: 'group-op-log', component: OpLog, meta: { title: '操作日志' } }
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
      { path: 'market', name: 'plugin-market', component: PluginMarket, meta: { title: '插件市场' } },
      // 插件审核（独立页面：审核开发者提交的插件，通过/拒绝后上架）
      { path: 'review', name: 'plugin-review', component: PluginReview, meta: { title: '插件审核' } },
      // 插件开发指南
      { path: 'guide', name: 'plugin-guide', component: PluginGuide, meta: { title: '开发指南' } }
    ]
  },
  // 插件详情页（菜单动态生成，每个插件一个入口，标题 = 插件 name）
  { path: '/plugins/p/:pluginId', name: 'plugin-page', component: Plugins },
  {
    path: '/ai',
    name: 'ai-admin',
    redirect: '/ai/config',
    meta: { title: 'AIGC 能力' },
    children: [
      // 子页随阶段逐个加：P0 AI 设置；P1 AI 对话 / 人格管理；P1.5 用量统计
      { path: 'providers', name: 'ai-providers', component: AiProviders, meta: { title: '供应商管理' } },
      { path: 'config', name: 'ai-config', component: AiConfig, meta: { title: 'AI 设置' } },
      { path: 'chat', name: 'ai-chat', component: AiChat, meta: { title: 'AI 对话' } },
      { path: 'personas', name: 'ai-personas', component: AiPersonas, meta: { title: '人格管理' } },
      { path: 'usage', name: 'ai-usage', component: AiUsage, meta: { title: '用量统计' } },
      { path: 'tools', name: 'ai-tools', component: AiTools, meta: { title: 'AI 工具' } },
      { path: 'mcp', name: 'ai-mcp', component: AiMcp, meta: { title: 'MCP 服务' } },
      { path: 'kb', name: 'ai-kb', component: AiKb, meta: { title: '知识库' } },
      { path: 'audit', name: 'ai-audit', component: AiAudit, meta: { title: 'AI 审核' } },
      { path: 'summary', name: 'ai-summary', component: AiSummary, meta: { title: 'AI 日报' } },
      { path: 'memory', name: 'ai-memory', component: AiMemory, meta: { title: 'AI 记忆' } }
    ]
  },
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
      { path: 'tune', name: 'tune', component: Tune, meta: { title: '性能模板' } }
    ]
  },
  { path: '/logs', name: 'logs', component: Logs, meta: { title: '运行日志' } },
  { path: '/monitor', name: 'monitor', component: Monitor, meta: { title: '消息监控' } },
  { path: '/scheduler', name: 'scheduler', component: Scheduler, meta: { title: '定时任务' } },
  { path: '/settings', name: 'settings', component: Settings, meta: { title: '框架配置' } },
  { path: '/preferences', name: 'preferences', component: Preferences, meta: { title: '个性化设置' } },
  // 旧路径重定向（菜单重构前收藏的地址仍可用）
  { path: '/groups', redirect: '/chat-groups/list' },
  { path: '/friends', redirect: '/chat-c2c/friends' },
  { path: '/messages/group', redirect: '/chat-groups/messages' },
  { path: '/messages/c2c', redirect: '/chat-c2c/messages' },
  { path: '/bots', redirect: '/center/qqbot' },
  { path: '/permission', redirect: '/center/permission' },
  { path: '/database', redirect: '/data/database' },
  { path: '/tune', redirect: '/ops/tune' },
  { path: '/security', redirect: '/ops/security' },
  { path: '/health', redirect: '/ops/health' },
  { path: '/backup', redirect: '/ops/backup' },
  { path: '/logs-center', redirect: '/logs' },
  { path: '/logs-center/logs', redirect: '/logs' },
  { path: '/logs-center/events', redirect: '/center/events' },
  { path: '/events', redirect: '/center/events' },
  { path: '/ops/audit', redirect: '/ops/security' },
  { path: '/logs-center/audit', redirect: '/ops/security' },
  { path: '/ops/risk', redirect: '/ops/security' },
  { path: '/risk', redirect: '/ops/security' }
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
