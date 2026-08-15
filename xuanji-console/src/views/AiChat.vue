<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import {
  NCard, NSelect, NButton, NInput, NText, NIcon, NScrollbar, NSpace, NTooltip
} from 'naive-ui'
import {
  ChatbubblesOutline, SendOutline, TrashOutline, StopCircleOutline,
  SparklesOutline
} from '@vicons/ionicons5'
import api from '../api'
import { chatStream } from '../api/llm'
import PageHero from '../components/PageHero.vue'
import EmptyState from '../components/EmptyState.vue'
import { renderMarkdown } from '../utils/markdown'
import { useBotsStore } from '../stores/bots'

const message = useMessage()
const dialog = useDialog()
const botsStore = useBotsStore()

interface ChatMsg {
  role: 'user' | 'bot'
  content: string
  streaming?: boolean
  feed?: 1 | -1
  stopped?: boolean
}

const MAX_MSGS = 100
const botKey = ref<string>('')
const messages = ref<ChatMsg[]>([])
const input = ref('')
const sending = ref(false)
const abortCtrl = ref<AbortController | null>(null)
const scrollRef = ref<any>(null)

const botOptions = computed(() =>
  botsStore.bots.map(b => ({
    label: `${b.name || b.botKey || b.appId || ''}${b.botKey ? ` (${b.botKey})` : ''}`,
    value: b.botKey || b.appId || ''
  }))
)

const canSend = computed(() => input.value.trim().length > 0 && !sending.value)

async function loadBots() {
  try {
    await botsStore.loadBots()
    if (botsStore.bots.length > 0) botKey.value = botsStore.bots[0].botKey || botsStore.bots[0].appId || ''
  } catch (e: any) {
    message.error('加载机器人列表失败: ' + (e.message || e))
  }
}

async function scrollBottom() {
  await nextTick()
  scrollRef.value?.scrollTo({ top: 999999, behavior: 'smooth' })
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  const botMsg: ChatMsg = { role: 'bot', content: '', streaming: true }
  messages.value.push(botMsg)
  // 消息条数上限，避免长对话无限累积
  if (messages.value.length > MAX_MSGS) messages.value = messages.value.slice(-MAX_MSGS)
  sending.value = true
  abortCtrl.value = new AbortController()
  scrollBottom()
  // chatStream 内部已捕获全部错误（AbortError 静默、其余走 onError），此处无需再包 try/catch
  await chatStream({ botKey: botKey.value || undefined, message: text }, {
    signal: abortCtrl.value.signal,
    onDelta: (piece) => {
      botMsg.content += piece
      scrollBottom()
    },
    onDone: () => {
      botMsg.streaming = false
      sending.value = false
    },
    onError: (err) => {
      botMsg.content = botMsg.content || '（调用失败）'
      botMsg.streaming = false
      sending.value = false
      message.error('AI 调用失败: ' + err)
    }
  })
  scrollBottom()
}

function stop() {
  abortCtrl.value?.abort()
  const last = messages.value[messages.value.length - 1]
  if (last?.streaming) { last.streaming = false; last.stopped = true }
  sending.value = false
}

function clear() {
  dialog.warning({
    title: '清空对话',
    content: '确认清空当前对话记录？',
    positiveText: '清空',
    negativeText: '取消',
    onPositiveClick: () => { messages.value = [] }
  })
}

// P2-F 用户反馈：👍=1 / 👎=-1，同一条回复只能反馈一次
async function feedback(m: ChatMsg, score: 1 | -1) {
  if (m.streaming || m.feed) return
  try {
    await api.llmApi.feedback({ botKey: botKey.value || undefined, replyText: m.content, score })
    m.feed = score
    message.success(score === 1 ? '已记录 👍' : '已记录 👎')
  } catch (e: any) {
    message.error('反馈失败: ' + (e.message || e))
  }
}

onMounted(loadBots)
</script>

<template>
  <div class="page">
    <PageHero title="AI 对话" subtitle="与配置的 LLM 供应商流式对话（机器人级人格生效）" :icon="SparklesOutline">
      <NSpace>
        <NSelect
          v-model:value="botKey"
          :options="botOptions"
          placeholder="选择机器人视角"
          clearable
          style="width: 220px"
        />
        <NButton secondary @click="clear">
          <template #icon><NIcon><TrashOutline /></NIcon></template>
          清空
        </NButton>
      </NSpace>
    </PageHero>

    <NCard :bordered="true" class="chat-card">
      <template v-if="messages.length === 0">
        <EmptyState description="发一条消息开始与 AI 对话（群聊 AI 需要在 AI 设置开启聊天总开关）" />
      </template>
      <NScrollbar ref="scrollRef" class="msg-scroll">
        <div class="msg-list">
          <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
            <div class="bubble" :class="{ streaming: m.streaming }">
              <span v-if="m.streaming" class="cursor" />
              <!-- bot 回复：流式期纯文本（避免半截 markdown 渲染闪烁），完成后渲染 markdown -->
              <span v-if="m.role === 'bot' && m.streaming">{{ m.content }}</span>
              <span v-else-if="m.role === 'bot'" class="bot-md" v-html="renderMarkdown(m.content)"></span>
              <template v-else>{{ m.content }}</template>
              <span v-if="m.stopped" class="stopped-tag">已停止</span>
              <div v-if="m.role === 'bot' && !m.streaming" class="feed-actions">
                <NTooltip trigger="hover"><template #trigger>
                  <span class="feed-btn" :class="{ active: m.feed === 1 }" @click="feedback(m, 1)">👍</span>
                </template>喜欢这条回复</NTooltip>
                <NTooltip trigger="hover"><template #trigger>
                  <span class="feed-btn" :class="{ active: m.feed === -1 }" @click="feedback(m, -1)">👎</span>
                </template>不喜欢这条回复</NTooltip>
              </div>
            </div>
          </div>
        </div>
      </NScrollbar>
      <div class="input-bar">
        <NInput
          v-model:value="input"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 6 }"
          placeholder="输入消息，Enter 发送 / Shift+Enter 换行"
          :disabled="sending"
          @keydown.enter.exact.prevent="send"
        />
        <div class="input-actions">
          <NButton v-if="sending" type="warning" secondary @click="stop">
            <template #icon><NIcon><StopCircleOutline /></NIcon></template>
            停止
          </NButton>
          <NButton type="primary" :disabled="!canSend" @click="send">
            <template #icon><NIcon><SendOutline /></NIcon></template>
            发送
          </NButton>
        </div>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.chat-card {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 230px);
  min-height: 420px;
}
.empty {
  padding: 80px 0;
}
.msg-scroll {
  flex: 1;
}
.msg-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 2px;
}
.msg-row {
  display: flex;
}
.msg-row.user {
  justify-content: flex-end;
}
.msg-row.bot {
  justify-content: flex-start;
}
.bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-row.user .bubble {
  background: #5b8def;
  color: #fff;
  border-bottom-right-radius: 4px;
}
.msg-row.bot .bubble {
  background: #dbeafe;
  color: #1f2d3d;
  border-bottom-left-radius: 4px;
}
.feed-actions {
  display: flex;
  gap: 4px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed rgba(31, 45, 61, 0.12);
}
.feed-btn {
  cursor: pointer;
  font-size: 13px;
  opacity: 0.55;
  transition: opacity 0.15s;
  user-select: none;
}
.feed-btn:hover { opacity: 1; }
.feed-btn.active { opacity: 1; filter: drop-shadow(0 0 2px rgba(91, 141, 239, 0.6)); }
.cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  background: currentColor;
  vertical-align: text-bottom;
  animation: blink 1s step-end infinite;
}
.bot-md { word-break: break-word; }
.bot-md :deep(p) { margin: 0 0 6px; }
.bot-md :deep(pre) { background: rgba(0, 0, 0, 0.06); padding: 8px; border-radius: 6px; overflow: auto; margin: 6px 0; }
.bot-md :deep(code) { background: rgba(0, 0, 0, 0.06); padding: 1px 4px; border-radius: 4px; font-size: 13px; }
.bot-md :deep(pre code) { background: transparent; padding: 0; }
.bot-md :deep(ul), .bot-md :deep(ol) { padding-left: 18px; margin: 4px 0; }
.bot-md :deep(blockquote) { border-left: 3px solid rgba(31, 45, 61, 0.2); padding-left: 10px; margin: 6px 0; color: #555; }
.stopped-tag { display: inline-block; margin-left: 8px; font-size: 11px; color: #9aa0a6; }
@keyframes blink {
  50% { opacity: 0; }
}
.input-bar {
  border-top: 1px solid var(--n-border-color);
  padding-top: 12px;
}
.input-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
</style>
