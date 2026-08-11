<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NCard, NButton, NSpace, NInput, NInputNumber, NSwitch, NSelect, NText,
  NTag, NDivider, NForm, NFormItem, NGrid, NGi, NAlert, NTooltip, NSpin,
  NEmpty, NIcon, NTabs, NTabPane
} from 'naive-ui'
import {
  SparklesOutline, SaveOutline, FlashOutline, KeyOutline,
  ServerOutline, ChatbubbleEllipsesOutline, ShieldCheckmarkOutline,
  SettingsOutline, InformationCircleOutline, LinkOutline
} from '@vicons/ionicons5'
import api from '../api'
import type { LlmConfig, LlmProviderInfo, ProviderRow, ModelRow } from '../api/llm'
import PageHero from '../components/PageHero.vue'

const message = useMessage()

const loading = ref(true)
const saving = ref(false)
const testing = ref(false)

const providers = ref<LlmProviderInfo[]>([])
const config = ref<LlmConfig | null>(null)
const initialConfig = ref<LlmConfig | null>(null) // 用于脏检查
const testResult = ref<{ reply?: string; error?: string } | null>(null)

// ──────────── 供应商 / 模型（能力绑定选择） ────────────
const providersConfig = ref<ProviderRow[]>([])
const modelsMap = ref<Record<number, ModelRow[]>>({})

async function loadProvidersConfig() {
  try {
    providersConfig.value = (await api.llmApi.providerList()) || []
  } catch {
    providersConfig.value = []
  }
}

async function loadProviderModels(providerId: number | null | undefined) {
  if (!providerId) return
  if (modelsMap.value[providerId]) return
  try {
    modelsMap.value[providerId] = (await api.llmApi.modelList(providerId)) || []
  } catch {
    modelsMap.value[providerId] = []
  }
}

function providerOptions() {
  return providersConfig.value.map(p => ({ label: `${p.name} (${p.providerType})`, value: p.id }))
}

function modelOptions(providerId: number | null | undefined, cap: string) {
  const models = providerId ? (modelsMap.value[providerId] || []) : []
  return models
    .filter(m => !m.capabilities || String(m.capabilities).includes(cap))
    .map(m => ({ label: m.modelName, value: m.modelName }))
}

/** 全部供应商+模型的选项（多选能力绑定用）：label="供应商名 / 模型名"，value="providerId:modelName" */
function bindingOptions(cap: string) {
  const out: { label: string; value: string }[] = []
  for (const p of providersConfig.value) {
    const models = modelsMap.value[p.id] || []
    for (const m of models) {
      if (!m.capabilities || String(m.capabilities).includes(cap)) {
        out.push({ label: `${p.name} / ${m.modelName}`, value: `${p.id}:${m.modelName}` })
      }
    }
  }
  return out
}

async function loadAllModels() {
  for (const p of providersConfig.value) {
    await loadProviderModels(p.id)
  }
}

// ──────────── 群白名单数据源（复用控制台群列表） ────────────
interface GroupRow { GROUP_ID?: string; groupId?: string; groupOpenid?: string; GROUP_NAME?: string; groupName?: string }
const allGroups = ref<GroupRow[]>([])
const groupOptions = computed(() =>
  allGroups.value.map(g => {
    const id = g.GROUP_ID || g.groupId || g.groupOpenid || ''
    const name = g.GROUP_NAME || g.groupName || id
    return { label: name || id, value: id }
  })
)

const currentProvider = computed(() =>
  providers.value.find(p => p.id === config.value?.providerId)
)

// ──────────── 能力矩阵：开关 + 绑定自包含 ────────────
// 每个能力一行：能力名 + 说明，右侧 [开关] [绑定模型多选]；关掉开关后绑定置灰
const capabilities = [
  { key: 'chat',        label: '对话',     cap: 'CHAT',          bindKey: 'chatBindings',          enabledKey: '' as string,                 hasSwitch: false, hint: '基础对话所用模型，第一个可用即用，失败 / 限流自动切下一个。' },
  { key: 'vision',      label: '图片理解', cap: 'VISION',        bindKey: 'visionBindings',        enabledKey: 'visionEnabled',              hasSwitch: true,  hint: '关闭后用户发图 / 表情包直接忽略，不再调用视觉模型。' },
  { key: 'image',       label: '图像生成', cap: 'IMAGE_GEN',     bindKey: 'imageBindings',         enabledKey: 'imageGenEnabled',            hasSwitch: true,  hint: '关闭后不再调用文生图模型（cogview / SD 等）。' },
  { key: 'tts',         label: '语音合成', cap: 'TTS',           bindKey: 'ttsBindings',           enabledKey: 'ttsEnabled',                 hasSwitch: true,  hint: '关闭后不再发语音；音色参数在「语音合成」页配置。' },
  { key: 'videoU',      label: '视频理解', cap: 'VIDEO_UNDERSTAND', bindKey: 'videoBindings',      enabledKey: 'videoUnderstandEnabled',    hasSwitch: true,  hint: '关闭后用户发视频不被识别；模型差异大，默认关。' },
  { key: 'videoG',      label: '视频生成', cap: 'VIDEO_GEN',     bindKey: 'videoGenBindings',      enabledKey: 'videoGenEnabled',            hasSwitch: true,  hint: '关闭后不再文生视频；慢且贵，默认关。' },
  { key: 'voiceClone',  label: '语音克隆', cap: 'VOICE_CLONE',   bindKey: 'voiceCloneBindings',    enabledKey: 'voiceCloneEnabled',          hasSwitch: true,  hint: '合规风险高，默认关；开启前请确保已告知用户并合规授权。' }
]

// ──────────── 脏检查：哪些 Tab 有改动 ────────────
const tabFields: Record<string, string[]> = {
  basic: ['enabled', 'mentionRequired', 'cooldownSeconds', 'dailyTokenLimit', 'temperature', 'maxTokens', 'groupIds', 'c2cEnabled', 'c2cUserIds', 'c2cDailyTokenLimit', 'c2cCooldownSeconds'],
  capabilities: ['chatBindings', 'visionBindings', 'imageBindings', 'ttsBindings', 'videoBindings', 'videoGenBindings', 'voiceCloneBindings', 'visionEnabled', 'imageGenEnabled', 'videoUnderstandEnabled', 'videoGenEnabled', 'ttsEnabled', 'voiceCloneEnabled', 'toolCallingEnabled', 'toolConfirmRequired', 'mcpEnabled'],
  voice: ['ttsVoice', 'ttsAudioFormat', 'ttsStylePrompt', 'fishVoice', 'fishStylePrompt', 'fishSpeed'],
  insight: ['profileEnabled', 'profileExtractHours', 'profileExtractMsgThreshold', 'proactiveEnabled', 'proactiveDailyLimit', 'proactiveCooldownMinutes', 'proactiveIdleMinutes', 'proactiveTimeStart', 'proactiveTimeEnd'],
  advanced: ['intentRouting', 'aiAudit', 'dailyReportEnabled', 'renderEnabled']
}

const dirty = computed(() =>
  initialConfig.value ? JSON.stringify(config.value) !== JSON.stringify(initialConfig.value) : false
)

function tabDirty(name: string): boolean {
  const keys = tabFields[name] || []
  if (!initialConfig.value || !config.value) return false
  const a = keys.map(k => (config.value as any)[k])
  const b = keys.map(k => (initialConfig.value as any)[k])
  return JSON.stringify(a) !== JSON.stringify(b)
}

async function load() {
  try {
    const [providersRes, configRes, groups] = await Promise.all([
      api.llmApi.providers(),
      api.llmApi.getConfig(),
      api.getGroups()
    ])
    providers.value = providersRes || []
    config.value = configRes || null
    allGroups.value = (groups as GroupRow[]) || []
    await loadProvidersConfig()
    await loadAllModels()
    if (config.value) {
      if (!config.value.groupIds) config.value.groupIds = []
      // 多选能力绑定兜底
      if (!config.value.chatBindings) config.value.chatBindings = []
      if (!config.value.visionBindings) config.value.visionBindings = []
      if (!config.value.imageBindings) config.value.imageBindings = []
      if (!config.value.ttsBindings) config.value.ttsBindings = []
      // V3 新增能力绑定兜底
      if (!config.value.videoBindings) config.value.videoBindings = []
      if (!config.value.videoGenBindings) config.value.videoGenBindings = []
      if (!config.value.voiceCloneBindings) config.value.voiceCloneBindings = []
      // 单聊维度兜底
      if (!config.value.c2cUserIds) config.value.c2cUserIds = []
      if (config.value.c2cEnabled == null) config.value.c2cEnabled = false
      if (config.value.c2cDailyTokenLimit == null) config.value.c2cDailyTokenLimit = 500_000
      if (config.value.c2cCooldownSeconds == null) config.value.c2cCooldownSeconds = 30
      // 新字段兜底默认（旧配置 JSON 无这些字段）
      if (config.value.profileExtractHours == null) config.value.profileExtractHours = 12
      if (config.value.profileExtractMsgThreshold == null) config.value.profileExtractMsgThreshold = 30
      if (config.value.proactiveDailyLimit == null) config.value.proactiveDailyLimit = 3
      if (config.value.proactiveCooldownMinutes == null) config.value.proactiveCooldownMinutes = 120
      if (config.value.proactiveIdleMinutes == null) config.value.proactiveIdleMinutes = 30
      if (!config.value.proactiveTimeStart) config.value.proactiveTimeStart = '09:00'
      if (!config.value.proactiveTimeEnd) config.value.proactiveTimeEnd = '22:00'
      if (!config.value.ttsVoice) config.value.ttsVoice = '冰糖'
      if (!config.value.ttsAudioFormat) config.value.ttsAudioFormat = 'wav'
      if (!config.value.fishStylePrompt) config.value.fishStylePrompt = '清冷低沉、疏离慵懒的少女御姐音，语速偏慢，声线平稳克制，尾音气声收束，藏着一丝温柔倦意'
      if (!config.value.fishSpeed) config.value.fishSpeed = 1.0
      if (config.value.renderEnabled === undefined || config.value.renderEnabled === null) config.value.renderEnabled = true
      // AI 能力总开关兜底（旧配置 JSON 无这些字段 → 默认 true；工具确认默认 true）
      if (config.value.toolCallingEnabled == null) config.value.toolCallingEnabled = true
      if (config.value.toolConfirmRequired == null) config.value.toolConfirmRequired = true
      if (config.value.mcpEnabled == null) config.value.mcpEnabled = true
      if (config.value.visionEnabled == null) config.value.visionEnabled = true
      if (config.value.imageGenEnabled == null) config.value.imageGenEnabled = true
      if (config.value.videoUnderstandEnabled == null) config.value.videoUnderstandEnabled = false
      if (config.value.videoGenEnabled == null) config.value.videoGenEnabled = false
      if (config.value.ttsEnabled == null) config.value.ttsEnabled = false
      if (config.value.voiceCloneEnabled == null) config.value.voiceCloneEnabled = false
      if (config.value.dailyReportEnabled == null) config.value.dailyReportEnabled = true
      // 快照用于脏检查
      initialConfig.value = JSON.parse(JSON.stringify(config.value))
    }
  } catch (e: any) {
    message.error('加载 AI 配置失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!config.value) return
  saving.value = true
  try {
    await api.llmApi.saveConfig(config.value)
    initialConfig.value = JSON.parse(JSON.stringify(config.value))
    message.success('AI 配置已保存')
  } catch (e: any) {
    message.error('保存失败: ' + (e.message || e))
  } finally {
    saving.value = false
  }
}

function discard() {
  if (!initialConfig.value) return
  config.value = JSON.parse(JSON.stringify(initialConfig.value))
  message.info('已撤销未保存的修改')
}

async function test() {
  testing.value = true
  testResult.value = null
  try {
    const r = await api.llmApi.test()
    if (r.ok) {
      testResult.value = { reply: r.reply }
      message.success('连接成功')
    } else {
      testResult.value = { error: r.error || '未知错误' }
      message.error('连接失败')
    }
  } catch (e: any) {
    testResult.value = { error: e.message || String(e) }
    message.error('连接失败: ' + (e.message || e))
  } finally {
    testing.value = false
  }
}

function pickModelDefault() {
  if (currentProvider.value && config.value) {
    config.value.model = currentProvider.value.defaultModel || config.value.model
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHero
      title="AI 设置"
      subtitle="先到「供应商管理」添加供应商并勾选模型能力，再在「模型与能力」里按能力绑定模型；其余为对话行为、洞察与语音参数"
      :icon="SparklesOutline"
    >
      <NSpace align="center">
        <NTag v-if="dirty" type="warning" round size="small" :bordered="false">● 有未保存修改</NTag>
        <NButton v-if="dirty" tertiary @click="discard">撤销</NButton>
        <NButton type="primary" :loading="saving" @click="save">
          <template #icon><NIcon><SaveOutline /></NIcon></template>
          保存配置
        </NButton>
        <NButton type="primary" secondary :loading="testing" @click="test">
          <template #icon><NIcon><FlashOutline /></NIcon></template>
          测试连接
        </NButton>
      </NSpace>
    </PageHero>

    <!-- 测试结果紧跟保存/测试按钮，缩短操作路径 -->
    <NAlert v-if="testResult?.reply" type="success" :bordered="false" class="test-alert">
      <template #icon><NIcon><LinkOutline /></NIcon></template>
      连接成功，模型回复：{{ testResult.reply }}
    </NAlert>
    <NAlert v-if="testResult?.error" type="error" :bordered="false" class="test-alert">
      <template #icon><NIcon><ShieldCheckmarkOutline /></NIcon></template>
      连接失败：{{ testResult.error }}
    </NAlert>

    <NSpin :show="loading">
      <template v-if="config">
        <NTabs type="line" animated>
          <!-- ════════ 对话行为 ════════ -->
          <NTabPane name="basic">
            <template #tab><span class="tab-label">对话行为<i v-if="tabDirty('basic')" class="dot" /></span></template>

            <NCard title="核心开关" :bordered="true" class="card card--core">
              <NForm label-placement="left" label-width="150" label-align="right">
                <NGrid :cols="2" :x-gap="24" responsive="screen">
                  <NGi>
                    <NFormItem label="聊天总开关">
                      <div class="field">
                        <NSwitch v-model:value="config.enabled" />
                        <div class="hint">关闭后所有群聊 / 单聊都不再响应 AI，整页其余设置全部失效。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="必须 @ 才回复">
                      <div class="field">
                        <NSwitch v-model:value="config.mentionRequired" />
                        <div class="hint">开启后群内只有被 @ 才回复；私聊恒视为已被 @，此开关对私聊无影响。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="单聊总开关">
                      <div class="field">
                        <NSwitch v-model:value="config.c2cEnabled" />
                        <div class="hint">私聊是否启用 AI。信任成本更高，默认关闭，启用请配白名单。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                </NGrid>
              </NForm>
            </NCard>

            <NCard title="频率与额度" :bordered="true" class="card card--detail">
              <NForm label-placement="left" label-width="150" label-align="right">
                <NGrid :cols="2" :x-gap="24" responsive="screen">
                  <NGi>
                    <NFormItem label="冷却间隔（秒）">
                      <div class="field">
                        <NInputNumber v-model:value="config.cooldownSeconds" :min="0" :max="600" style="width: 100%" />
                        <div class="hint">同一会话两次回复的最小间隔，防止刷屏。0 = 不限制。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="每日 token 上限">
                      <div class="field">
                        <NInputNumber v-model:value="config.dailyTokenLimit" :min="0" :step="100000" style="width: 100%" />
                        <div class="hint">全站每日 token 消耗上限，达上限后停止调用。0 = 不限制。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="单聊日 token 上限">
                      <NInputNumber v-model:value="config.c2cDailyTokenLimit" :min="0" :step="50000" style="width: 100%" />
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="单聊冷却（秒）">
                      <NInputNumber v-model:value="config.c2cCooldownSeconds" :min="0" :max="600" style="width: 100%" />
                    </NFormItem>
                  </NGi>
                </NGrid>
              </NForm>
            </NCard>

            <NCard title="对话模型参数" :bordered="true" class="card card--detail">
              <NForm label-placement="left" label-width="150" label-align="right">
                <NGrid :cols="2" :x-gap="24" responsive="screen">
                  <NGi>
                    <NFormItem label="温度">
                      <div class="field">
                        <NInputNumber v-model:value="config.temperature" :min="0" :max="2" :step="0.1" style="width: 100%" />
                        <div class="hint">0 = 严谨确定性，2 = 发散有创意。日常对话建议 0.7~1.0。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="回复上限 token">
                      <div class="field">
                        <NInputNumber v-model:value="config.maxTokens" :min="1" :max="32768" style="width: 100%" />
                        <div class="hint">单次回复最大长度，越大越能写长文，也越费 token。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                </NGrid>
              </NForm>
            </NCard>

            <NCard title="启用范围（群 / 单聊）" :bordered="true" class="card card--detail">
              <NForm label-placement="left" label-width="150" label-align="right">
                <NGrid :cols="2" :x-gap="24" responsive="screen">
                  <NGi :span="2">
                    <NFormItem label="群白名单">
                      <div class="field">
                        <NSelect v-model:value="config.groupIds" multiple tag filterable :options="groupOptions" placeholder="留空 = 全部群启用；填写则仅这些群启用" clearable />
                        <div class="hint">仅对名单内群启用了 AI；空白表示所有群都启用。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi :span="2">
                    <NFormItem label="单聊白名单">
                      <div class="field">
                        <NSelect v-model:value="config.c2cUserIds" multiple tag filterable placeholder="留空 = 全部用户启用；填写则仅这些用户" clearable />
                        <div class="hint">仅名单内用户可私聊调起 AI；空白表示所有用户。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                </NGrid>
              </NForm>
            </NCard>
          </NTabPane>

          <!-- ════════ 模型与能力 ════════ -->
          <NTabPane name="capabilities">
            <template #tab><span class="tab-label">模型与能力<i v-if="tabDirty('capabilities')" class="dot" /></span></template>

            <NAlert type="info" :bordered="false" class="card cap-alert">
              <template #icon><NIcon><InformationCircleOutline /></NIcon></template>
              先到「供应商管理」添加供应商并为其模型勾选能力，再在这里按能力<b>多选</b>「供应商 / 模型」：第一个可用即用，失败 / 限流自动切换到下一个（主选 + 备选容灾）。多个能力可共用同一模型；全部留空则回退单供应商旧配置。
            </NAlert>

            <NCard title="能力矩阵（开关 + 绑定自包含）" :bordered="true" class="card">
              <div class="cap-list">
                <div v-for="(c, idx) in capabilities" :key="c.key" class="cap-row" :class="{ 'is-off': c.hasSwitch && !config[c.enabledKey] }">
                  <div class="cap-meta">
                    <div class="cap-name">
                      {{ c.label }}
                      <NTag v-if="!c.hasSwitch" size="tiny" :bordered="false" type="default">随总开关</NTag>
                    </div>
                    <div class="hint">{{ c.hint }}</div>
                  </div>
                  <div class="cap-ctrl">
                    <NSwitch v-if="c.hasSwitch" v-model:value="config[c.enabledKey]" />
                    <NSelect
                      v-model:value="config[c.bindKey]"
                      multiple filterable tag
                      :disabled="c.hasSwitch && !config[c.enabledKey]"
                      :options="bindingOptions(c.cap)"
                      placeholder="多选「供应商 / 模型」，按序尝试"
                      clearable
                      class="cap-select"
                    />
                  </div>
                  <NDivider v-if="idx < capabilities.length - 1" class="cap-div" />
                </div>
              </div>
            </NCard>

            <NCard title="工具与扩展" :bordered="true" class="card card--detail">
              <NForm label-placement="left" label-width="150" label-align="right">
                <NGrid :cols="2" :x-gap="24" responsive="screen">
                  <NGi>
                    <NFormItem label="工具调用">
                      <div class="field">
                        <NSwitch v-model:value="config.toolCallingEnabled" />
                        <div class="hint">关掉后模型只作纯对话，不再能调用建任务 / 发语音 / 识图等工具。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="工具需确认">
                      <div class="field">
                        <NSwitch v-model:value="config.toolConfirmRequired" />
                        <div class="hint">开 = 危险工具（建任务 / 发消息等）先征求你的同意；关 = 直接执行（更自由但有风险）。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="MCP 桥接">
                      <div class="field">
                        <NSwitch v-model:value="config.mcpEnabled" />
                        <div class="hint">关掉后不连接外部 MCP server（内置工具仍可用）。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                </NGrid>
              </NForm>
            </NCard>
          </NTabPane>

          <!-- ════════ 语音合成 ════════ -->
          <NTabPane name="voice">
            <template #tab><span class="tab-label">语音合成<i v-if="tabDirty('voice')" class="dot" /></span></template>

            <NAlert v-if="!config.ttsEnabled" type="warning" :bordered="false" class="card">
              语音合成能力已关闭（在「模型与能力」中开启），以下参数暂不生效。
            </NAlert>

            <NCard title="基础音色" :bordered="true" class="card" :class="config.ttsEnabled ? '' : 'card--disabled'">
              <NForm label-placement="left" label-width="150" label-align="right">
                <NGrid :cols="2" :x-gap="24" responsive="screen">
                  <NGi>
                    <NFormItem label="TTS 默认音色">
                      <NInput v-model:value="config.ttsVoice" placeholder="冰糖" :disabled="!config.ttsEnabled" />
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="音频格式">
                      <NSelect v-model:value="config.ttsAudioFormat"
                        :disabled="!config.ttsEnabled"
                        :options="[
                          { label: 'wav', value: 'wav' },
                          { label: 'mp3', value: 'mp3' },
                          { label: 'pcm16', value: 'pcm16' }
                        ]" />
                    </NFormItem>
                  </NGi>
                  <NGi :span="2">
                    <NFormItem label="TTS 默认风格">
                      <NInput v-model:value="config.ttsStylePrompt" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="温柔少女音，语速偏慢，带着些许娇羞" :disabled="!config.ttsEnabled" />
                    </NFormItem>
                  </NGi>
                </NGrid>
              </NForm>
            </NCard>

            <NCard title="Fish Audio 备选（TTS 凭据请在「供应商管理」添加 Fish 供应商）" :bordered="true" class="card card--detail" :class="config.ttsEnabled ? '' : 'card--disabled'">
              <NForm label-placement="left" label-width="150" label-align="right">
                <NGrid :cols="2" :x-gap="24" responsive="screen">
                  <NGi :span="2">
                    <NFormItem label="Fish 音色 ID">
                      <div class="field">
                        <NInput v-model:value="config.fishVoice" placeholder="留空用 Fish 默认音色" :disabled="!config.ttsEnabled" />
                        <div class="hint">fish.audio 音色库或克隆音色的 32 位 ID；也可在人格编辑里给每个人格单独配。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi :span="2">
                    <NFormItem label="Fish 默认风格">
                      <NInput v-model:value="config.fishStylePrompt" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="清冷低沉、疏离慵懒的少女御姐音，语速偏慢……" :disabled="!config.ttsEnabled" />
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="Fish 语速">
                      <NInputNumber v-model:value="config.fishSpeed" :min="0.5" :max="2" :step="0.1" style="width:100%" :disabled="!config.ttsEnabled" />
                    </NFormItem>
                  </NGi>
                </NGrid>
              </NForm>
            </NCard>
          </NTabPane>

          <!-- ════════ 用户洞察 ════════ -->
          <NTabPane name="insight">
            <template #tab><span class="tab-label">用户洞察<i v-if="tabDirty('insight')" class="dot" /></span></template>

            <NCard title="用户画像（全量消息认知）" :bordered="true" class="card">
              <NForm label-placement="left" label-width="150" label-align="right">
                <NGrid :cols="2" :x-gap="24" responsive="screen">
                  <NGi>
                    <NFormItem label="用户画像开关">
                      <div class="field">
                        <NSwitch v-model:value="config.profileEnabled" />
                        <div class="hint">开启后统计群成员消息，AI 能认出"他是谁、什么说话风格"。本地统计零 token，画像提炼按间隔耗少量 token。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="画像提炼间隔(小时)">
                      <NInputNumber v-model:value="config.profileExtractHours" :min="1" :max="72" style="width: 100%" />
                    </NFormItem>
                  </NGi>
                  <NGi :span="2">
                    <NFormItem label="提炼消息阈值">
                      <div class="field">
                        <NInputNumber v-model:value="config.profileExtractMsgThreshold" :min="1" :max="500" style="width: 100%" />
                        <div class="hint">单个用户累计多少条消息才做一次画像提炼，避免低频用户空转消耗。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                </NGrid>
              </NForm>
            </NCard>

            <NCard title="主动搭话（冷场活跃气氛）" :bordered="true" class="card card--detail">
              <NForm label-placement="left" label-width="150" label-align="right">
                <NGrid :cols="2" :x-gap="24" responsive="screen">
                  <NGi>
                    <NFormItem label="主动搭话开关">
                      <div class="field">
                        <NSwitch v-model:value="config.proactiveEnabled" />
                        <div class="hint">群冷场时机器人主动 @ 最近活跃成员问话或发话题卡片。默认关，防骚扰。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="每群每日次数上限">
                      <NInputNumber v-model:value="config.proactiveDailyLimit" :min="0" :max="20" style="width: 100%" />
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="冷却间隔(分钟)">
                      <NInputNumber v-model:value="config.proactiveCooldownMinutes" :min="1" :max="1440" style="width: 100%" />
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="冷场判定(分钟)">
                      <div class="field">
                        <NInputNumber v-model:value="config.proactiveIdleMinutes" :min="1" :max="1440" style="width: 100%" />
                        <div class="hint">群内距最后一条消息超过该分钟数，视为冷场可触发搭话。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi :span="2">
                    <NFormItem label="主动时间段">
                      <NInput v-model:value="config.proactiveTimeStart" placeholder="09:00" style="width: 45%" :disabled="!config.proactiveEnabled" />
                      <NText style="margin: 0 10px" depth="3">至</NText>
                      <NInput v-model:value="config.proactiveTimeEnd" placeholder="22:00" style="width: 45%" :disabled="!config.proactiveEnabled" />
                      <div class="hint" style="width:100%">仅在该时间段内允许主动搭话。</div>
                    </NFormItem>
                  </NGi>
                </NGrid>
              </NForm>
            </NCard>
          </NTabPane>

          <!-- ════════ 扩展与生成（原高级安全，去安全误导） ════════ -->
          <NTabPane name="advanced">
            <template #tab><span class="tab-label">扩展与生成<i v-if="tabDirty('advanced')" class="dot" /></span></template>

            <NCard title="智能路由与审核" :bordered="true" class="card">
              <NForm label-placement="left" label-width="150" label-align="right">
                <NGrid :cols="2" :x-gap="24" responsive="screen">
                  <NGi>
                    <NFormItem label="意图路由">
                      <div class="field">
                        <NSwitch v-model:value="config.intentRouting" />
                        <div class="hint">人话 → 命令：开启后 AI 可把"帮我签到"转成命令执行（需确认）。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="AI 内容审核">
                      <div class="field">
                        <NSwitch v-model:value="config.aiAudit" />
                        <div class="hint">消息过 LLM 审核，违规拦截不回复。默认关。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                </NGrid>
              </NForm>
            </NCard>

            <NCard title="内容生成" :bordered="true" class="card card--detail">
              <NForm label-placement="left" label-width="150" label-align="right">
                <NGrid :cols="2" :x-gap="24" responsive="screen">
                  <NGi>
                    <NFormItem label="AI 日报">
                      <div class="field">
                        <NSwitch v-model:value="config.dailyReportEnabled" />
                        <div class="hint">群 / 单聊定时自动总结并推送。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                  <NGi>
                    <NFormItem label="图文卡片渲染">
                      <div class="field">
                        <NSwitch v-model:value="config.renderEnabled" />
                        <div class="hint">图文日报 / render_card 卡片渲染（Playwright Chromium）。关闭后不启动浏览器，相关功能提示未启用。</div>
                      </div>
                    </NFormItem>
                  </NGi>
                </NGrid>
              </NForm>
            </NCard>
          </NTabPane>
        </NTabs>
      </template>
      <NEmpty v-else description="暂无配置数据" />
    </NSpin>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.card {
  border-radius: 10px;
}
/* 视觉层级：核心卡强调，细节卡降级 */
.card--core {
  border: 1px solid var(--n-color-target, #2080f0);
  box-shadow: 0 0 0 3px rgba(32, 128, 240, 0.08);
}
.card--detail {
  border-color: var(--n-border-color);
}
.card--disabled {
  opacity: 0.55;
}
.cap-alert {
  margin-bottom: 16px;
}
.test-alert {
  margin-bottom: 0;
}
/* 每个开关 / 输入下方统一说明行 */
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}
.hint {
  font-size: 12px;
  line-height: 1.45;
  color: var(--n-text-color-3);
}
/* 能力矩阵行 */
.cap-list {
  display: flex;
  flex-direction: column;
}
.cap-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 14px 4px;
}
.cap-row.is-off .cap-meta {
  opacity: 0.6;
}
.cap-meta {
  flex: 1 1 auto;
  min-width: 0;
}
.cap-name {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.cap-ctrl {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 0 0 auto;
}
.cap-select {
  width: 320px;
  max-width: 42vw;
}
.cap-div {
  margin: 0;
}
/* Tab 未保存红点 */
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tab-label .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--n-color-target, #f0a020);
  display: inline-block;
}
</style>
