<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NButton, NIcon, NText, NTag, NEmpty, NScrollbar, NSpin, NAvatar,
  NModal, NDivider, NInput, NTooltip, NAlert, NUpload, NRadioGroup,
  NRadioButton, useThemeVars
} from 'naive-ui'
import {
  ChatbubblesOutline, SendOutline, RefreshOutline, SearchOutline,
  ImageOutline, VideocamOutline, MicOutline, AttachOutline
} from '@vicons/ionicons5'
import api from '../api'
import { groupName, userName } from '../utils/names'
import dayjs from 'dayjs'

const themeVars = useThemeVars()

const message = useMessage()

// ════════════════════════════════════════════════════════════════
//  数据状态
// ════════════════════════════════════════════════════════════════

interface BotInfo { botKey: string; appId: string; platform: string }
interface SessionRow { id: string; bot: string; type: 'group' | 'c2c'; name: string; sub: string; preview?: string; previewTime?: number }
interface MsgRow { [k: string]: any }

const bots = ref<BotInfo[]>([])
const groups = ref<any[]>([])
const friends = ref<any[]>([])
const sessions = ref<SessionRow[]>([])     // 合并后的会话列表
const sessionMap = ref<Record<string, SessionRow>>({}) // id → SessionRow

// 左侧会话状态
const sessionTab = ref<'group' | 'c2c'>('group')
const botFilter = ref('')
const searchKw = ref('')
const selectedSessionId = ref('')

// 中间聊天状态
const chatMessages = ref<MsgRow[]>([])
const chatLoading = ref(false)
const chatHasMore = ref(false)
const chatBot = ref('')
const chatTargetType = ref<'group' | 'c2c'>('group')
const chatTargetId = ref('')
const chatTitle = ref('')
const chatGroupId = ref('')      // 群ID（用于群成员/群信息）
const inputType = ref<'text' | 'markdown' | 'media'>('text')
const inputText = ref('')
const sending = ref(false)
const groupMembers = ref<any[]>([])
const membersLoading = ref(false)
let pollTimer: number | null = null

// 富媒体
const showMediaModal = ref(false)
const mediaType = ref<'image' | 'video' | 'voice' | 'file'>('image')
const mediaBase64 = ref('')
const mediaFilename = ref('')
const mediaSize = ref(0)
const mediaSending = ref(false)

// ════════════════════════════════════════════════════════════════
//  计算属性 / 工具
// ════════════════════════════════════════════════════════════════

function idOf(row: any): string {
  return row?.GROUP_ID || row?.groupId || row?.groupOpenid || row?.PLATFORM_USER_ID || row?.platform_user_id || row?.userId || row?.USER_ID || ''
}

function sessionKey(type: 'group' | 'c2c', id: string, bot: string): string {
  return `${type}:${bot}:${id}`
}

function fmtTime(sec: any): string {
  const n = Number(sec)
  if (!isFinite(n) || n <= 0) return '—'
  const d = dayjs(n * 1000)
  if (d.isSame(dayjs(), 'day')) return d.format('HH:mm')
  if (d.isSame(dayjs().subtract(1, 'day'), 'day')) return '昨天'
  if (d.isSame(dayjs(), 'year')) return d.format('MM-DD')
  return d.format('YYYY-MM-DD')
}

function fmtTimeFull(sec: any): string {
  const n = Number(sec)
  if (!isFinite(n) || n <= 0) return ''
  const d = dayjs(n * 1000)
  if (d.isSame(dayjs(), 'day')) return d.format('HH:mm:ss')
  return d.format('MM-DD HH:mm:ss')
}

function fmtChatTime(sec: any): string {
  const n = Number(sec)
  if (!isFinite(n) || n <= 0) return ''
  return dayjs(n * 1000).format('YYYY-MM-DD HH:mm:ss')
}

function fmtTodayDivider(sec: any): string {
  const n = Number(sec)
  if (!isFinite(n) || n <= 0) return ''
  return dayjs(n * 1000).format('MM-DD HH:mm')
}

function isOut(m: any): boolean {
  return String(m.DIRECTION ?? m.direction ?? '').toUpperCase() === 'OUT'
}
function msgContent(m: any): string {
  return String(m.CONTENT ?? m.content ?? '')
}
function msgType(m: any): string {
  return String(m.MSG_TYPE ?? m.msg_type ?? '')
}
function isImage(m: any): boolean {
  const t = msgType(m).toLowerCase()
  return t.includes('image') || t.includes('img') || t === 'picture'
}
function isVideo(m: any): boolean {
  return msgType(m).toLowerCase().includes('video')
}
function isVoice(m: any): boolean {
  const t = msgType(m).toLowerCase()
  return t.includes('voice') || t.includes('audio') || t.includes('record')
}
function isMarkdown(m: any): boolean {
  const t = msgType(m).toLowerCase()
  return t.includes('markdown') || t.includes('md')
}
function isMedia(m: any): boolean {
  return isImage(m) || isVideo(m) || isVoice(m)
}

function senderName(m: any): string {
  const nick = m.NICKNAME || m.nickname
  if (nick) return String(nick)
  const uid = String(m.USER_ID ?? m.user_id ?? '')
  if (!uid) return '用户'
  if (chatTargetType.value === 'group') {
    const mb = groupMembers.value.find(x => String(idOf(x)) === uid)
    if (mb) return String(mb.NICKNAME || mb.nickname || uid.slice(0, 8))
  } else {
    const f = friends.value.find(x => String(idOf(x)) === uid)
    if (f) return userName(f)
  }
  return uid.slice(0, 8)
}

/** 收消息里 sender 的群角色（owner/admin/member/—）。仅群聊可用，单聊返回 '—'。 */
function senderRole(m: any): string {
  if (chatTargetType.value !== 'group') return '—'
  const uid = String(m.USER_ID ?? m.user_id ?? '')
  if (!uid) return '—'
  const mb = groupMembers.value.find(x => String(idOf(x)) === uid)
  return String(mb?.ROLE ?? mb?.role ?? '—')
}

/** Bot 消息上的机器人标签（显示机器人名称，有名称显示名称，否则显示 appId 缩写） */
const botNameLabel = computed(() => {
  const b: any = bots.value.find(x => x.appId === chatBot.value || x.botKey === chatBot.value)
  if (b?.name) return b.name
  return String(chatBot.value || 'Bot').slice(0, 12)
})

function avatarColor(name: string): string {
  // 根据首字符哈希给一个稳定颜色（避免每次随机）
  const colors = ['#5b8def', '#07c160', '#f0a020', '#9b59b6', '#e5484d', '#2090e0', '#722ed1', '#18a058']
  let h = 0
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0
  return colors[h % colors.length]
}

// 当前选中会话
const currentSession = computed<SessionRow | null>(() => sessionMap.value[selectedSessionId.value] || null)

/** 群人数（统一兜底，避免 NaN） */
const groupMemberCount = computed(() => {
  const g: any = currentSession.value
  const n = Number(g?.MEMBER_COUNT ?? g?.member_count ?? 0)
  return isFinite(n) && n > 0 ? n : '—'
})

/** 群主昵称 */
const ownerNickname = computed(() => {
  const o = groupMembers.value.find((m: any) => (m.ROLE ?? m.role) === 'owner')
  return o ? senderName({ USER_ID: o.MEMBER_ID ?? o.member_id }) : ''
})
/** 管理员数 */
const adminCount = computed(() =>
  groupMembers.value.filter((m: any) => (m.ROLE ?? m.role) === 'admin').length
)

// 按关键字 + Tab 过滤的会话列表
const filteredSessions = computed(() => {
  const kw = searchKw.value.trim().toLowerCase()
  return sessions.value.filter(s => {
    if (s.type !== sessionTab.value) return false
    if (!kw) return true
    return (s.name || '').toLowerCase().includes(kw)
      || String((s as any)._targetId || '').toLowerCase().includes(kw)
      || (s.preview || '').toLowerCase().includes(kw)
  })
})
const currentGroup = computed(() => {
  if (chatTargetType.value !== 'group' || !chatTargetId.value) return null
  return groups.value.find(g => idOf(g) === chatTargetId.value) || null
})
const currentFriend = computed(() => {
  if (chatTargetType.value !== 'c2c' || !chatTargetId.value) return null
  return friends.value.find(f => idOf(f) === chatTargetId.value) || null
})

// ════════════════════════════════════════════════════════════════
//  会话列表（合并 + 最新预览）
// ════════════════════════════════════════════════════════════════

async function loadSessions() {
  // 加载群 / 好友
  const [g, f] = await Promise.all([
    api.getGroups().catch(() => []),
    api.getFriends().catch(() => [])
  ])
  groups.value = g || []
  friends.value = f || []

  // 合并为 SessionRow
  const build = (rows: any[], type: 'group' | 'c2c') => rows
    .filter(r => !botFilter.value || String(r.BOT_APPID) === String(botFilter.value))
    .map<SessionRow>(r => {
      const id = idOf(r)
      const bot = String(r.BOT_APPID || '')
      const isGrp = type === 'group'
      const name = isGrp ? groupName(r) : userName(r)
      const memberCount = isGrp ? Number(r.MEMBER_COUNT ?? r.member_count ?? 0) : 0
      const sub = isGrp
        ? `${memberCount || '—'} 人 · ${id.slice(0, 8)}…`
        : `单聊 · ${id.slice(0, 8)}…`
      return {
        id: sessionKey(type, id, bot),
        type,
        bot,
        name,
        sub,
        _targetId: id
      } as any
    })

  const grpRows = build(groups.value, 'group')
  const c2cRows = build(friends.value, 'c2c')
  sessions.value = [...grpRows, ...c2cRows] as any
  // 建索引
  const map: Record<string, SessionRow> = {}
  for (const s of sessions.value) map[s.id] = s
  sessionMap.value = map
  // 拉每会话最新一条作为预览（异步）
  loadPreviews()
}

async function loadPreviews() {
  const list = sessions.value.slice(0)
  // 并发拉最新预览（限并发避免过载）
  const chunk = 8
  for (let i = 0; i < list.length; i += chunk) {
    const slice = list.slice(i, i + chunk)
    await Promise.all(slice.map(async (s) => {
      try {
        const res = await api.getContactMessages({
          type: s.type,
          targetId: (s as any)._targetId,
          limit: 1
        })
        const rows = (res?.rows || []) as MsgRow[]
        if (rows.length) {
          const m = rows[0]
          s.preview = msgContent(m).slice(0, 40) || `[${msgType(m) || '消息'}]`
          s.previewTime = Number(m.CREATE_TIME ?? m.create_time ?? 0)
        } else {
          s.preview = '暂无消息'
        }
      } catch { s.preview = '' }
    }))
  }
  // 触发响应式（数组已修改但 Vue 可能检测不到引用层）
  sessions.value = [...sessions.value]
}

// 监听筛选变化 → 重排
watch(botFilter, () => {
  loadSessions()
})

// ════════════════════════════════════════════════════════════════
//  聊天面板
// ════════════════════════════════════════════════════════════════

async function openSession(s: SessionRow) {
  selectedSessionId.value = s.id
  chatTargetType.value = s.type
  chatTargetId.value = (s as any)._targetId
  chatBot.value = s.bot
  chatTitle.value = s.name
  chatMessages.value = []
  chatHasMore.value = false
  if (s.type === 'group') chatGroupId.value = (s as any)._targetId
  await loadMessages(true)
  if (s.type === 'group') await loadMembers(chatTargetId.value)
  else groupMembers.value = []
  startPoll()
}

async function loadMessages(today = false, before?: number) {
  if (!chatTargetId.value) return
  chatLoading.value = true
  try {
    const params: any = { type: chatTargetType.value, targetId: chatTargetId.value, limit: 100 }
    if (today) params.startTime = dayjs().startOf('day').unix()
    else if (before) params.beforeTime = before
    const res = await api.getContactMessages(params)
    const rows = (res?.rows || []) as MsgRow[]
    if (today) chatMessages.value = rows
    else chatMessages.value = [...rows, ...chatMessages.value]
    chatHasMore.value = !!res?.hasMore
    await nextTick()
    if (today) scrollToBottom()
  } catch (e: any) {
    message.error('加载消息失败：' + (e?.message ?? e))
  } finally {
    chatLoading.value = false
  }
}

async function loadMembers(groupId: string) {
  membersLoading.value = true
  try { groupMembers.value = (await api.getGroupMembers(groupId)) || [] }
  catch { groupMembers.value = [] }
  finally { membersLoading.value = false }
}

// ════════════════════════════════════════════════════════════════
//  滚动 / 轮询
// ════════════════════════════════════════════════════════════════

const scrollEl = ref<HTMLElement | null>(null)
function scrollToBottom() {
  if (scrollEl.value) scrollEl.value.scrollTop = scrollEl.value.scrollHeight
}
async function onScrollUp() {
  if (!chatHasMore.value || chatLoading.value) return
  const el = scrollEl.value
  if (!el || el.scrollTop > 40) return
  const earliest = chatMessages.value[0]?.CREATE_TIME ?? chatMessages.value[0]?.create_time
  if (!earliest) return
  await loadMessages(false, Number(earliest))
}

function startPoll() {
  stopPoll()
  pollTimer = window.setInterval(() => {
    if (document.visibilityState === 'hidden') return
    refreshLatest()
  }, 4000)
}
function stopPoll() {
  if (pollTimer !== null) { clearInterval(pollTimer); pollTimer = null }
}
async function refreshLatest() {
  if (!chatTargetId.value) return
  try {
    const res = await api.getContactMessages({
      type: chatTargetType.value, targetId: chatTargetId.value, limit: 50
    })
    const rows = (res?.rows || []) as MsgRow[]
    if (!rows.length) return
    const lastLocal = chatMessages.value[chatMessages.value.length - 1]
    const lastLocalTime = lastLocal ? Number(lastLocal.CREATE_TIME ?? lastLocal.create_time ?? 0) : 0
    const newOnes = rows.filter(r => Number(r.CREATE_TIME ?? r.create_time ?? 0) > lastLocalTime)
    if (newOnes.length) {
      chatMessages.value = [...chatMessages.value, ...newOnes]
      // 同步更新左侧预览
      updatePreviewInList(newOnes)
      await nextTick(); scrollToBottom()
    }
  } catch {}
}
function updatePreviewInList(newOnes: MsgRow[]) {
  const sid = selectedSessionId.value
  if (!sid) return
  const last = newOnes[newOnes.length - 1]
  const s = sessionMap.value[sid]
  if (s) {
    s.preview = msgContent(last).slice(0, 40)
    s.previewTime = Number(last.CREATE_TIME ?? last.create_time ?? 0)
    sessions.value = [...sessions.value]
  }
}

// ════════════════════════════════════════════════════════════════
//  发送
// ════════════════════════════════════════════════════════════════

async function sendText() {
  const content = inputText.value.trim()
  if (!content || !chatTargetId.value || !chatBot.value) return
  if (inputType.value === 'media') {
    // 富媒体模式：先把当前选中的媒体连同文本一起发（QQ 不支持纯文本 + 媒体混发，所以这里要求用户用单独文本模式或单独富媒体模式）
    message.warning('富媒体模式请用下方「选择文件」按钮发送图片/视频/语音')
    return
  }
  sending.value = true
  try {
    const msgType = inputType.value === 'markdown' ? 'markdown' : 'text'
    const res = await api.send(chatBot.value, chatTargetType.value, chatTargetId.value, msgType, content)
    if (res?.status === 'ok') {
      inputText.value = ''
      chatMessages.value.push({
        DIRECTION: 'OUT', MSG_TYPE: msgType, CONTENT: content,
        CREATE_TIME: Math.floor(Date.now() / 1000),
        _optimistic: true
      })
      await nextTick(); scrollToBottom()
      setTimeout(refreshLatest, 800)
    } else {
      message.error('发送失败：' + (res?.msg || '未知错误'))
    }
  } catch (e: any) { message.error('发送失败：' + (e?.message ?? e)) }
  finally { sending.value = false }
}

function setMediaType(t: 'image' | 'video' | 'voice' | 'file') {
  mediaType.value = t
  mediaBase64.value = ''
  mediaFilename.value = ''
  mediaSize.value = 0
  showMediaModal.value = true
}

/** 富媒体类型中文标签 */
function mediaLabel(t: string): string {
  return t === 'image' ? '图片' : t === 'video' ? '视频' : t === 'voice' ? '语音' : '文件'
}

function onUploadChange(options: { file: any }) {
  const file = options.file?.file as File | undefined
  if (!file) return
  // 文件大小校验（QQ 官方软/硬限制）
  const check = validateMediaSize(mediaType.value, file.size)
  if (check === 'hard') {
    const lim = MEDIA_LIMITS[mediaType.value]
    message.error(`文件超硬限制（${fmtMB(lim.hard)}），请压缩后再上传`)
    return
  }
  if (check === 'soft') {
    const lim = MEDIA_LIMITS[mediaType.value]
    message.warning(`文件超过软限制（${fmtMB(lim.soft)}），将自动降级为「文件类型」上传`)
  }
  mediaFilename.value = file.name
  mediaSize.value = file.size
  const reader = new FileReader()
  reader.onload = () => { mediaBase64.value = String(reader.result || '') }
  reader.readAsDataURL(file)
}
function fileTypeOf(): number {
  if (mediaType.value === 'image') return 1
  if (mediaType.value === 'video') return 2
  if (mediaType.value === 'voice') return 3
  return 4   // file
}

/** QQ 官方媒体/文件大小限制（https://bot.q.qq.com/wiki/develop/api-v2/server-inter/message/send-receive/send.html）
 *  软限制：超过会降级为「文件类型」上传；硬限制：超过直接报错。 */
const MEDIA_LIMITS: Record<string, { soft: number; hard: number; ext: string }> = {
  image: { soft: 20 * 1024 * 1024, hard: 200 * 1024 * 1024, ext: 'png / jpg' },
  video: { soft: 30 * 1024 * 1024, hard: 200 * 1024 * 1024, ext: 'mp4' },
  voice: { soft: 20 * 1024 * 1024, hard: 200 * 1024 * 1024, ext: 'silk' },
  file:  { soft: 200 * 1024 * 1024, hard: 200 * 1024 * 1024, ext: '任意' }
}
function fmtMB(b: number): string { return (b / 1024 / 1024).toFixed(0) + ' MB' }

/** 校验选中文件大小：'ok' 正常；'soft' 超软限制（提示降级）；'hard' 超硬限制（阻止）。 */
function validateMediaSize(t: string, bytes: number): 'ok' | 'soft' | 'hard' {
  const lim = MEDIA_LIMITS[t]
  if (!lim) return 'ok'
  if (bytes > lim.hard) return 'hard'
  if (bytes > lim.soft) return 'soft'
  return 'ok'
}
async function sendMedia() {
  if (!mediaBase64.value || !chatTargetId.value || !chatBot.value) return
  // 发送前再校验一次（防止用户在弹窗里停留期间其他地方改了）
  const check = validateMediaSize(mediaType.value, mediaSize.value)
  if (check === 'hard') {
    const lim = MEDIA_LIMITS[mediaType.value]
    message.error(`文件超硬限制（${fmtMB(lim.hard)}），无法发送`)
    return
  }
  mediaSending.value = true
  try {
    const res = await api.sendMedia(
      chatBot.value, chatTargetType.value, chatTargetId.value,
      fileTypeOf(), mediaBase64.value, mediaFilename.value || 'file'
    )
    if (res?.status === 'ok') {
      message.success('媒体发送成功')
      showMediaModal.value = false
      chatMessages.value.push({
        DIRECTION: 'OUT', MSG_TYPE: mediaType.value,
        CONTENT: `[${mediaType.value}] ${mediaFilename.value}`,
        CREATE_TIME: Math.floor(Date.now() / 1000), _optimistic: true
      })
      await nextTick(); scrollToBottom()
      setTimeout(refreshLatest, 1000)
    } else {
      message.error('发送失败：' + (res?.msg || '未知错误'))
    }
  } catch (e: any) { message.error('发送失败：' + (e?.message ?? e)) }
  finally { mediaSending.value = false }
}

// ════════════════════════════════════════════════════════════════
//  生命周期
// ════════════════════════════════════════════════════════════════

onMounted(async () => {
  try {
    const list: any[] = await api.getBots()
    bots.value = (list || []).map((b: any) => ({
      botKey: b.botKey, appId: b.appId, platform: b.platform, name: b.name || ''
    }))
  } catch {}
  await loadSessions()
  // 默认打开第一个群
  const first = sessions.value.find(s => s.type === sessionTab.value)
  if (first) await openSession(first)
})

onUnmounted(() => stopPoll())

function dayChanged(a: any, b: any): boolean {
  const ta = Number(a), tb = Number(b)
  if (!isFinite(ta) || !isFinite(tb)) return false
  return new Date(ta * 1000).toDateString() !== new Date(tb * 1000).toDateString()
}
function roleClass(role: string | undefined): string {
  if (role === 'owner') return 'role-owner'
  if (role === 'admin') return 'role-admin'
  return 'role-member'
}

/** 角色中文标签 */
function roleLabel(role: string | undefined): string {
  if (role === 'owner') return '群主'
  if (role === 'admin') return '管理员'
  return '成员'
}

/** 成员排序：owner → admin → member（按昵称） */
const sortedGroupMembers = computed(() => {
  const rank = (r: string | undefined) =>
    r === 'owner' ? 0 : r === 'admin' ? 1 : 2
  return [...groupMembers.value].sort((a: any, b: any) => {
    const ra = rank(a.ROLE ?? a.role)
    const rb = rank(b.ROLE ?? b.role)
    if (ra !== rb) return ra - rb
    return String(a.NICKNAME || a.nickname || '').localeCompare(
      String(b.NICKNAME || b.nickname || ''))
  })
})

watch(sessionTab, () => {
  const first = sessions.value.find(s => s.type === sessionTab.value)
  if (first) openSession(first)
})
</script>

<template>
  <div class="monitor-page">
    <div class="chat-wrap">
      <!-- ═══════ 左侧 ═══════ -->
      <aside class="sidebar">
        <div class="sb-tabs">
          <button :class="{ active: sessionTab === 'group' }" @click="sessionTab = 'group'">群聊</button>
          <button :class="{ active: sessionTab === 'c2c' }" @click="sessionTab = 'c2c'">单聊</button>
        </div>
        <div class="sb-filter">
          <NInput
            v-model:value="searchKw"
            size="small"
            placeholder="搜索群名 / 群号 / 昵称…"
            clearable
            style="margin-bottom: 8px"
          >
            <template #prefix><NIcon size="13"><SearchOutline /></NIcon></template>
          </NInput>
          <NSelect
            v-model:value="botFilter"
            :options="[{ label: '全部机器人', value: '' }, ...bots.map(b => ({ label: b.appId, value: b.appId }))]"
            size="small"
            placeholder="按机器人筛选"
          />
        </div>
        <NScrollbar class="chat-list">
          <div v-if="!filteredSessions.length" class="empty-tip">
            <NEmpty :description="sessionTab === 'group' ? '暂无群聊' : '暂无单聊'" size="small" />
          </div>
          <div
            v-for="s in filteredSessions"
            :key="s.id"
            class="chat-item"
            :class="{ active: selectedSessionId === s.id }"
            @click="openSession(s)"
          >
            <div class="avatar" :style="{ background: avatarColor(s.name) }">{{ s.name.charAt(0).toUpperCase() }}</div>
            <div class="ci-main">
              <div class="ci-top">
                <span class="ci-name">{{ s.name }}</span>
                <span class="ci-time">{{ fmtTime(s.previewTime) }}</span>
              </div>
              <div class="ci-bottom">
                <span class="ci-preview">{{ s.preview || '加载中…' }}</span>
              </div>
              <div class="ci-sub">{{ s.sub }}</div>
            </div>
          </div>
        </NScrollbar>
      </aside>

      <!-- ═══════ 中间 ═══════ -->
      <main class="chat-main">
        <header class="cm-head">
          <div>
            <div class="cm-title">
              <span>{{ chatTitle || '未选择会话' }}</span>
              <span v-if="chatBot" class="bot-tag">机器人 · {{ chatBot }}</span>
            </div>
            <div class="cm-sub" v-if="chatTargetId">
              {{ chatTargetType === 'group' ? '群聊' : '单聊' }} · {{ chatTargetId }}
              <span v-if="chatTargetType === 'group' && currentGroup">
                · {{ groupMemberCount }} 人
              </span>
              · 消息 {{ chatMessages.length }}
            </div>
            <div class="cm-sub" v-else>请在左侧选择会话</div>
          </div>
          <div class="cm-head-right">
            <NTooltip trigger="hover">
              <template #trigger>
                <button class="icon-btn" @click="loadMessages(true)">↻</button>
              </template>
              刷新今日
            </NTooltip>
          </div>
        </header>

        <div class="msg-area" ref="scrollEl" @scroll="onScrollUp">
          <div v-if="chatHasMore" class="load-more-hint" @click="onScrollUp">
            <NButton size="tiny" quaternary>{{ chatLoading ? '加载中…' : '↑ 上滑加载更早' }}</NButton>
          </div>
          <NEmpty v-if="!chatMessages.length && !chatLoading" description="今天暂无消息" size="small" style="padding: 40px 0" />

          <template v-for="(m, i) in chatMessages" :key="i">
            <!-- 时间分隔线 -->
            <div v-if="i === 0 || dayChanged(m.CREATE_TIME ?? m.create_time, chatMessages[i - 1].CREATE_TIME ?? chatMessages[i - 1].create_time)"
              class="time-divider">{{ fmtTodayDivider(m.CREATE_TIME ?? m.create_time) }}</div>

            <div class="msg-row" :class="isOut(m) ? 'bot' : 'user'">
              <div class="msg-avatar" :style="{ background: avatarColor(isOut(m) ? chatTitle : senderName(m)) }">
                {{ (isOut(m) ? chatTitle : senderName(m)).charAt(0).toUpperCase() }}
              </div>
              <div class="msg-body">
                <div class="msg-meta">
                  <span class="msg-name">{{ isOut(m) ? chatTitle : senderName(m) }}</span>
                  <!-- 群聊 · 收到的消息：在发送者名后显示群角色（owner=群主/admin=管理员/member=成员） -->
                  <span
                    v-if="!isOut(m) && chatTargetType === 'group' && senderRole(m) !== '—'"
                    :class="['role-tag', roleClass(senderRole(m))]"
                  >{{ roleLabel(senderRole(m)) }}</span>
                  <!-- Bot 发出的消息：显示机器人 appId/昵称（替代原来的"机器人"标签） -->
                  <span v-if="isOut(m)" class="role-tag bot-tag">🤖 {{ botNameLabel }}</span>
                  <span class="msg-time">{{ fmtTimeFull(m.CREATE_TIME ?? m.create_time) }}</span>
                </div>
                <div class="bubble" :class="{ md: isMarkdown(m) }">
                  <!-- 富媒体消息 -->
                  <template v-if="isImage(m)">
                    <img class="msg-img" :src="mediaBase64 || msgContent(m)" :alt="msgContent(m)" @error="$event.target.style.display='none'" v-if="msgContent(m).startsWith('data:') || msgContent(m).match(/\.(jpg|jpeg|png|gif|webp)/i)" />
                    <NText v-else class="msg-media-hint">[图片] {{ msgContent(m) }}</NText>
                  </template>
                  <template v-else-if="isVideo(m)">
                    <video class="msg-video" controls :src="msgContent(m)" v-if="msgContent(m).startsWith('data:') || msgContent(m).match(/\.(mp4|mov)/i)"></video>
                    <NText v-else class="msg-media-hint">[视频] {{ msgContent(m) }}</NText>
                  </template>
                  <template v-else-if="isVoice(m)">
                    <div class="voice-chip">▶ 语音消息 {{ msgContent(m) }}</div>
                  </template>
                  <NText v-else>{{ msgContent(m) }}</NText>
                </div>
              </div>
            </div>
          </template>
        </div>

        <!-- 输入区 -->
        <div class="input-area">
          <div class="ia-tools">
            <NRadioGroup v-model:value="inputType" size="small">
              <NRadioButton value="text">文本</NRadioButton>
              <NRadioButton value="markdown">Markdown</NRadioButton>
            </NRadioGroup>
            <div class="spacer"></div>
            <span class="ia-count">
              类型：{{ inputType === 'text' ? '文本' : 'Markdown' }} · 机器人：{{ chatBot || '—' }}
            </span>
          </div>

          <div class="file-preview" v-if="mediaBase64">
            <img v-if="mediaType === 'image' && mediaBase64.startsWith('data:image')" :src="mediaBase64" />
            <div v-else class="fp-icon"><NIcon size="22"><AttachOutline /></NIcon></div>
            <div class="fp-info">
              <div class="fp-name">{{ mediaFilename }}</div>
              <div class="fp-detail">{{ mediaLabel(mediaType) }} · {{ (mediaSize / 1024).toFixed(1) }} KB · base64 已就绪</div>
            </div>
            <button class="fp-del" @click="mediaBase64 = ''">移除</button>
          </div>

          <NInput
            v-model:value="inputText"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            :placeholder="chatTargetId ? '输入消息，Enter 发送，Shift+Enter 换行' : '请先在左侧选择会话'"
            :disabled="!chatTargetId"
            @keydown.enter.exact.prevent="sendText"
            style="background: transparent"
          />

          <div class="ia-bottom">
            <!-- 4 个富媒体/文件按钮移到左侧（发送按钮旁边） -->
            <div class="media-tools">
              <NTooltip trigger="hover"><template #trigger><button class="icon-btn" @click="setMediaType('image')"><NIcon size="18"><ImageOutline /></NIcon></button></template>发送图片</NTooltip>
              <NTooltip trigger="hover"><template #trigger><button class="icon-btn" @click="setMediaType('video')"><NIcon size="18"><VideocamOutline /></NIcon></button></template>发送视频</NTooltip>
              <NTooltip trigger="hover"><template #trigger><button class="icon-btn" @click="setMediaType('voice')"><NIcon size="18"><MicOutline /></NIcon></button></template>发送语音</NTooltip>
              <NTooltip trigger="hover"><template #trigger><button class="icon-btn" @click="setMediaType('file')"><NIcon size="18"><AttachOutline /></NIcon></button></template>发送文件</NTooltip>
            </div>
            <NButton
              type="primary"
              :loading="sending"
              :disabled="!chatTargetId || (!inputText.trim() && !mediaBase64)"
              @click="sendText"
              class="send-btn"
            >
              <template #icon><NIcon><SendOutline /></NIcon></template>
              发 送
            </NButton>
          </div>
        </div>
      </main>

      <!-- ═══════ 右侧 ═══════ -->
      <aside class="info-panel">
        <template v-if="chatTargetType === 'group' && currentGroup">
          <section class="ip-section">
            <h3>群聊信息</h3>
            <div class="ip-card">
              <div class="ip-avatar" :style="{ background: avatarColor(groupName(currentGroup)) }">{{ groupName(currentGroup).charAt(0).toUpperCase() }}</div>
              <div>
                <div class="ip-name">{{ groupName(currentGroup) }}</div>
                <div class="ip-id mono">{{ idOf(currentGroup) }}</div>
              </div>
            </div>
            <div class="kv-list">
              <div class="kv-row"><span class="kv-k">群主</span><span class="kv-v">{{ ownerNickname || '—' }}</span></div>
              <div class="kv-row"><span class="kv-k">管理员</span><span class="kv-v">{{ adminCount }} 人</span></div>
              <div class="kv-row"><span class="kv-k">成员数</span><span class="kv-v">{{ groupMemberCount }}</span></div>
              <div class="kv-row"><span class="kv-k">机器人</span><span class="kv-v">{{ currentGroup.BOT_APPID || '—' }}</span></div>
              <div class="kv-row"><span class="kv-k">入群时间</span><span class="kv-v">{{ fmtChatTime(currentGroup.JOIN_TIME ?? currentGroup.join_time) }}</span></div>
            </div>
          </section>

          <section class="ip-section">
            <h3>成员列表 <span class="member-count">· {{ sortedGroupMembers.length }} 人</span></h3>
            <NSpin :show="membersLoading" size="small">
              <NEmpty v-if="!membersLoading && !sortedGroupMembers.length" description="暂无成员数据" size="small" style="padding: 12px 0" />
              <div v-for="mb in sortedGroupMembers" :key="idOf(mb)" class="member-item">
                <div class="m-avatar" :style="{ background: avatarColor(String(mb.NICKNAME || mb.nickname || idOf(mb))) }">
                  {{ (mb.NICKNAME || mb.nickname || idOf(mb)).charAt(0).toUpperCase() }}
                </div>
                <div class="m-info">
                  <div class="m-name">{{ mb.NICKNAME || mb.nickname || '未知' }}</div>
                  <div class="m-role mono">{{ idOf(mb) }}</div>
                </div>
                <span :class="['role-tag', roleClass(mb.ROLE ?? mb.role)]">{{ roleLabel(mb.ROLE ?? mb.role) }}</span>
              </div>
              <NText v-if="groupMembers.length > 50" depth="3" style="font-size: 11px; display: block; padding: 6px 0">仅展示前 50 人</NText>
            </NSpin>
          </section>
        </template>

        <template v-else-if="chatTargetType === 'c2c' && currentFriend">
          <section class="ip-section">
            <h3>单聊信息</h3>
            <div class="ip-card">
              <div class="ip-avatar" :style="{ background: avatarColor(userName(currentFriend)) }">{{ userName(currentFriend).charAt(0).toUpperCase() }}</div>
              <div>
                <div class="ip-name">{{ userName(currentFriend) }}</div>
                <div class="ip-id mono">{{ idOf(currentFriend) }}</div>
              </div>
            </div>
            <div class="kv-list">
              <div class="kv-row"><span class="kv-k">用户ID</span><span class="kv-v mono">{{ idOf(currentFriend) }}</span></div>
              <div class="kv-row"><span class="kv-k">所属机器人</span><span class="kv-v">{{ currentFriend.BOT_APPID || '—' }}</span></div>
              <div class="kv-row"><span class="kv-k">加入时间</span><span class="kv-v">{{ fmtChatTime(currentFriend.JOIN_TIME ?? currentFriend.join_time) }}</span></div>
            </div>
          </section>
        </template>

        <NEmpty v-else description="请在左侧选择会话" style="padding: 40px 0" />
      </aside>
    </div>

    <!-- 富媒体弹窗 -->
    <NModal v-model:show="showMediaModal" preset="card" :title="`发送${mediaLabel(mediaType)}（${MEDIA_LIMITS[mediaType]?.ext}）`" style="width: 480px">
      <NSpace vertical :size="12">
        <NText depth="3" style="font-size: 12px">
          发送到：{{ chatTitle }}（{{ chatTargetType === 'group' ? '群聊' : '单聊' }}） · {{ chatTargetId }}
        </NText>
        <NUpload
          accept="*"
          :max="1"
          :show-file-list="false"
          @change="onUploadChange"
        >
          <NButton :disabled="mediaBase64">
            <template #icon><NIcon><AttachOutline /></NIcon></template>
            {{ mediaFilename ? '已选择文件（点「发送」确认）' : '选择文件（点击或拖拽）' }}
          </NButton>
        </NUpload>
        <div v-if="mediaBase64" class="fp-upload-preview">
          <img v-if="mediaType === 'image' && mediaBase64.startsWith('data:image')" :src="mediaBase64" />
          <div v-else class="fp-icon"><NIcon size="22"><AttachOutline /></NIcon></div>
          <div class="fp-info">
            <div class="fp-name">{{ mediaFilename }}</div>
            <div class="fp-detail">{{ (mediaSize / 1024).toFixed(1) }} KB · base64 已就绪</div>
          </div>
          <button class="fp-del" @click="mediaBase64 = ''; mediaFilename = ''">移除</button>
        </div>
        <NAlert type="info" :show-icon="true" style="font-size: 12px">
          QQ 官方文件限制：图片 {{ MEDIA_LIMITS.image.soft / 1048576 }}MB（软）/ {{ MEDIA_LIMITS.image.hard / 1048576 }}MB（硬）；视频 {{ MEDIA_LIMITS.video.soft / 1048576 }}MB / {{ MEDIA_LIMITS.video.hard / 1048576 }}MB；语音 {{ MEDIA_LIMITS.voice.soft / 1048576 }}MB / {{ MEDIA_LIMITS.voice.hard / 1048576 }}MB；文件 {{ MEDIA_LIMITS.file.soft / 1048576 }}MB / {{ MEDIA_LIMITS.file.hard / 1048576 }}MB。
          超过软限制会降级为「文件」类型上传，超过硬限制会报错。
        </NAlert>
      </NSpace>
      <template #footer>
        <NSpace justify="end" :size="10">
          <NButton @click="showMediaModal = false">取消</NButton>
          <NButton type="primary" :loading="mediaSending" :disabled="!mediaBase64" @click="sendMedia">发送</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
/* 模板里需要 dayChanged/roleClass；它们在 setup 内被引用，自动可用。
   空 script 块占位避免 SFC 解析报错，编译后会被 tree-shake 掉。 */
</style>

<style scoped>
.monitor-page { display: flex; flex-direction: column; height: 100%; padding-top: 4px; }
.chat-wrap {
  display: flex;
  height: calc(100vh - 120px);
  min-height: 520px;
  background: v-bind('themeVars.cardColor');
  border-radius: 14px;
  border: 1px solid v-bind('themeVars.borderColor');
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

/* ═══════════ 左侧 ═══════════ */
.sidebar {
  width: 300px;
  min-width: 300px;
  background: v-bind('themeVars.cardColor');
  border-right: 1px solid v-bind('themeVars.borderColor');
  display: flex;
  flex-direction: column;
}
.sb-tabs {
  display: flex;
  border-bottom: 1px solid v-bind('themeVars.borderColor');
}
.sb-tabs button {
  flex: 1;
  padding: 12px 0;
  border: none;
  background: none;
  font-size: 14px;
  color: v-bind('themeVars.textColor3');
  cursor: pointer;
  position: relative;
  transition: color 0.15s;
}
.sb-tabs button.active {
  color: v-bind('themeVars.primaryColor');
  font-weight: 600;
}
.sb-tabs button.active::after {
  content: "";
  position: absolute;
  left: 30%; right: 30%; bottom: 0;
  height: 2px;
  background: v-bind('themeVars.primaryColor');
  border-radius: 2px;
}
.sb-filter {
  padding: 10px 12px;
  border-bottom: 1px solid v-bind('themeVars.borderColor');
}
.chat-list {
  flex: 1;
  overflow-y: auto;
  background: transparent;
}
.empty-tip { padding: 40px 0; }
.chat-item {
  display: flex;
  padding: 10px 14px;
  cursor: pointer;
  gap: 10px;
  transition: background 0.15s;
}
.chat-item:hover {
  background: v-bind('themeVars.hoverColor');
}
.chat-item.active {
  background: v-bind('themeVars.primaryColorOpacity2');
  border-left: 3px solid v-bind('themeVars.primaryColor');
}
.avatar {
  width: 42px; height: 42px;
  border-radius: 10px;
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  font-size: 17px; font-weight: 600;
}
.ci-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.ci-top { display: flex; justify-content: space-between; align-items: baseline; }
.ci-name {
  font-size: 14px; font-weight: 500;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.ci-time {
  font-size: 11px; color: #9ca3af; flex-shrink: 0; margin-left: 6px;
}
.ci-bottom { display: flex; align-items: center; gap: 4px; }
.ci-preview {
  font-size: 12px; color: #6b7280;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  flex: 1;
}
.ci-sub {
  font-size: 11px; color: #9ca3af;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* ═══════════ 中间 ═══════════ */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: v-bind('themeVars.bodyColor');   /* 恢复主题背景色 */
  min-width: 0;
}
.cm-head {
  height: 56px;
  background: v-bind('themeVars.cardColor');
  border-bottom: 1px solid v-bind('themeVars.borderColor');
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  flex-shrink: 0;
}
.cm-title {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 10px;
}
.bot-tag {
  font-size: 11px;
  color: v-bind('themeVars.primaryColor');
  background: v-bind('themeVars.primaryColorOpacity2');
  padding: 1px 7px;
  border-radius: 8px;
  font-weight: 500;
}
.cm-sub { font-size: 12px; color: v-bind('themeVars.textColor3'); margin-top: 2px; }
.cm-head-right { display: flex; align-items: center; gap: 8px; }
.icon-btn {
  border: none;
  background: none;
  font-size: 16px;
  cursor: pointer;
  color: v-bind('themeVars.textColor3');
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.15s;
}
.icon-btn:hover { background: v-bind('themeVars.hoverColor'); }

.msg-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  background: v-bind('themeVars.bodyColor');
}
.load-more-hint { text-align: center; padding: 6px 0; }
.time-divider {
  text-align: center;
  font-size: 12px;
  color: v-bind('themeVars.textColor3');
  margin: 14px 0 10px;
  position: relative;
}
.time-divider::before,
.time-divider::after {
  content: "";
  position: absolute;
  top: 50%;
  width: 80px;
  height: 1px;
  background: v-bind('themeVars.borderColor');
}
.time-divider::before { right: calc(50% + 50px); }
.time-divider::after { left: calc(50% + 50px); }
.msg-row {
  display: flex;
  margin-bottom: 14px;
  gap: 10px;
}
.msg-row.bot { flex-direction: row-reverse; }
.msg-avatar {
  width: 36px; height: 36px;
  border-radius: 8px;
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  font-size: 15px; font-weight: 600;
}
.msg-body {
  max-width: 62%;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.msg-row.bot .msg-body { align-items: flex-end; }
.msg-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  font-size: 12px;
}
.msg-row.bot .msg-meta { flex-direction: row-reverse; }
.msg-name { color: v-bind('themeVars.textColor2'); font-weight: 500; }
.msg-time { font-size: 11px; color: v-bind('themeVars.textColor3'); }
.bubble {
  position: relative;
  padding: 10px 13px;
  border-radius: 10px;
  line-height: 1.55;
  word-break: break-word;
  white-space: pre-wrap;
  font-size: 14px;
  max-width: 100%;
  box-shadow: 0 1px 3px rgba(15, 30, 60, 0.08);
}
.msg-row.user .bubble {
  background: #ffffff;
  border: 1px solid rgba(15, 30, 60, 0.10);
  border-top-left-radius: 3px;
}
.msg-row.bot .bubble {
  background: #dbeafe;   /* 浅蓝（弃用微信绿 #95ec69），与底色 #e5e7eb 形成层次 */
  border: 1px solid rgba(29, 78, 216, 0.18);
  border-top-right-radius: 3px;
}
.bubble.md {
  font-size: 13px;
  background: #f8f9fa;
  border: 1px solid #cbd5e1;
  padding: 10px 12px;
  border-top-left-radius: 3px;
}
.msg-row.bot .bubble.md {
  background: #eef2ff;   /* 浅紫（区别于普通 bot 气泡的浅蓝） */
  border-color: rgba(29, 78, 216, 0.22);
}
.bubble img.msg-img { max-width: 220px; max-height: 220px; border-radius: 6px; display: block; margin: 4px 0; }
.bubble video.msg-video { max-width: 320px; border-radius: 6px; display: block; }
.bubble .voice-chip {
  display: flex; align-items: center; gap: 8px;
  background: rgba(0,0,0,0.04);
  padding: 6px 10px; border-radius: 6px;
  font-size: 12px; color: v-bind('themeVars.textColor2');
}
.msg-media-hint { color: #722ed1; font-style: italic; }

.role-tag {
  position: absolute;
  top: 7px; right: 0;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
  font-weight: 500;
  letter-spacing: 0.3px;
}
.role-owner { background: #fee7e7; color: #c0392b; border: 1px solid #f5b6b6; }
.role-admin { background: #e0edfc; color: #1c5fad; border: 1px solid #b6cce8; }
.role-member { background: #f0f1f3; color: #6b7280; border: 1px solid #dde0e5; opacity: 0.85; }
.bot-tag {
  background: #eef2ff;
  color: #1c5fad;
  border: 1px solid #c7d6f0;
  font-weight: 500;
}

/* 输入区 */
.input-area {
  background: v-bind('themeVars.cardColor');
  border-top: 1px solid v-bind('themeVars.borderColor');
  padding: 8px 16px 10px;
  flex-shrink: 0;
}
.ia-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.spacer { flex: 1; }

.file-preview {
  display: flex; align-items: center; gap: 10px;
  background: v-bind('themeVars.bodyColor'); border-radius: 8px;
  padding: 8px 12px; margin-bottom: 8px;
  border: 1px solid v-bind('themeVars.borderColor');
}
.file-preview img {
  width: 44px; height: 44px;
  border-radius: 6px; object-fit: cover;
}
.fp-icon { width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; font-size: 24px; background: v-bind('themeVars.cardColor'); border-radius: 6px; color: v-bind('themeVars.textColor3'); }
.fp-info { flex: 1; min-width: 0; }
.fp-name { font-size: 13px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fp-detail { font-size: 11px; color: v-bind('themeVars.textColor3'); }
.fp-del { border: none; background: none; color: #e5484d; cursor: pointer; font-size: 13px; }

.ia-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
}
.media-tools {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-right: auto;  /* 紧贴左侧，让发送按钮靠右对齐 */
}
.send-btn { min-width: 96px; }
.ia-count { font-size: 12px; color: v-bind('themeVars.textColor3'); }
.fp-upload-preview {
  display: flex; align-items: center; gap: 10px;
  background: v-bind('themeVars.bodyColor'); border-radius: 8px;
  padding: 8px 12px;
  border: 1px solid v-bind('themeVars.borderColor');
}
.fp-upload-preview img {
  width: 44px; height: 44px;
  border-radius: 6px; object-fit: cover;
}

/* ═══════════ 右侧 ═══════════ */
.info-panel {
  width: 280px;
  min-width: 280px;
  background: v-bind('themeVars.cardColor');
  border-left: 1px solid v-bind('themeVars.borderColor');
  overflow-y: auto;
}
.ip-section { padding: 14px 16px; border-bottom: 1px solid v-bind('themeVars.borderColor'); }
.ip-section h3 {
  font-size: 13px; color: v-bind('themeVars.textColor2');
  font-weight: 500; margin-bottom: 10px;
  display: flex; align-items: center;
}
.ip-card {
  background: v-bind('themeVars.bodyColor'); border-radius: 10px;
  padding: 12px; display: flex; align-items: center; gap: 12px;
  border: 1px solid v-bind('themeVars.borderColor');
}
.ip-avatar {
  width: 52px; height: 52px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 22px; font-weight: 600;
}
.ip-name { font-size: 16px; font-weight: 600; }
.ip-id { font-size: 11px; color: v-bind('themeVars.textColor3'); margin-top: 2px; word-break: break-all; }
.kv-list { margin-top: 10px; }
.kv-row {
  display: flex; justify-content: space-between; padding: 7px 0;
  font-size: 13px; border-bottom: 0.5px dashed v-bind('themeVars.borderColor');
}
.kv-row:last-child { border-bottom: none; }
.kv-k { color: v-bind('themeVars.textColor3'); }
.kv-v { color: v-bind('themeVars.textColor1'); word-break: break-all; text-align: right; max-width: 65%; }
.member-count { font-size: 12px; color: v-bind('themeVars.textColor3'); margin-left: 4px; font-weight: 400; }
.member-item {
  display: flex; align-items: center; gap: 10px; padding: 7px 0;
  position: relative;
}
.m-avatar {
  width: 32px; height: 32px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 13px; font-weight: 600;
  flex-shrink: 0;
}
.m-info { flex: 1; min-width: 0; overflow: hidden; }
.m-name {
  font-size: 13px; font-weight: 500;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.m-role {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10.5px; color: v-bind('themeVars.textColor3');
  margin-top: 1px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  padding-right: 56px;   /* 留出右上角 role-tag 绝对定位的位置，避免 ID 被盖 */
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
}
</style>