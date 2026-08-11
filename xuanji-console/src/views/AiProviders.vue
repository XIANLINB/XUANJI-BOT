<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NCard, NButton, NSpace, NModal, NForm, NFormItem, NInput, NSelect, NDataTable,
  NIcon, NPopconfirm, NEmpty, NSpin, NTag, NText, NCheckboxGroup, NCheckbox, NAlert, NDivider
} from 'naive-ui'
import { ServerOutline, AddOutline, TrashOutline, CloudDownloadOutline, ChevronDownOutline, ChevronForwardOutline, KeyOutline, CreateOutline } from '@vicons/ionicons5'
import api from '../api'
import type { ProviderRow, ModelRow, ApiKeyRow } from '../api/llm'
import PageHero from '../components/PageHero.vue'

const message = useMessage()
const providers = ref<ProviderRow[]>([])
const loading = ref(false)
const expanded = ref<Record<number, boolean>>({})
const modelsMap = ref<Record<number, ModelRow[]>>({})
const keysMap = ref<Record<number, ApiKeyRow[]>>({})
const modelLoading = ref<Record<number, boolean>>({})

// 供应商弹窗（新增/编辑）
const showProvider = ref(false)
const editingProvider = ref<ProviderRow | null>(null)
const pForm = ref({ name: '', providerType: 'openai', baseUrl: '', apiKey: '' })

// 模型弹窗
const showModel = ref(false)
const curProvider = ref<ProviderRow | null>(null)
const mForm = ref({ modelName: '', capabilities: [] as string[] })

// Key 弹窗
const showKey = ref(false)
const keyProvider = ref<ProviderRow | null>(null)
const keyForm = ref({ apiKey: '', remark: '' })

const CAPS = [
  { label: '对话', value: 'CHAT' },
  { label: '图片理解', value: 'IMAGE_UNDERSTAND' },
  { label: '图像生成', value: 'IMAGE_GEN' },
  { label: '视频理解', value: 'VIDEO_UNDERSTAND' },
  { label: '视频生成', value: 'VIDEO_GEN' },
  { label: '语音生成', value: 'TTS' },
  { label: '语音克隆', value: 'VOICE_CLONE' },
  { label: '角色模型', value: 'ROLE' },
  { label: '向量模型', value: 'EMBEDDING' }
]

async function load() {
  loading.value = true
  try {
    providers.value = (await api.llmApi.providerList()) || []
  } catch (e: any) {
    message.error('加载供应商失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

async function toggleExpand(p: ProviderRow) {
  const next = !expanded.value[p.id]
  expanded.value[p.id] = next
  if (next) {
    if (!modelsMap.value[p.id]) await loadModels(p)
    if (!keysMap.value[p.id]) await loadKeys(p)
  }
}

async function loadModels(p: ProviderRow) {
  modelLoading.value[p.id] = true
  try {
    modelsMap.value[p.id] = (await api.llmApi.modelList(p.id)) || []
  } catch (e: any) {
    message.error('加载模型失败: ' + (e.message || e))
  } finally {
    modelLoading.value[p.id] = false
  }
}

async function loadKeys(p: ProviderRow) {
  try {
    keysMap.value[p.id] = (await api.llmApi.keyList(p.id)) || []
  } catch {
    keysMap.value[p.id] = []
  }
}

function openAddProvider() {
  editingProvider.value = null
  pForm.value = { name: '', providerType: 'openai', baseUrl: '', apiKey: '' }
  showProvider.value = true
}

function openEditProvider(p: ProviderRow) {
  editingProvider.value = p
  pForm.value = { name: p.name, providerType: p.providerType, baseUrl: p.baseUrl || '', apiKey: '' }
  showProvider.value = true
}

async function saveProvider() {
  if (!pForm.value.name.trim()) {
    message.warning('请填写供应商名称')
    return
  }
  try {
    await api.llmApi.providerSave({ ...pForm.value, id: editingProvider.value?.id })
    message.success(editingProvider.value ? '供应商已更新' : '供应商已添加')
    showProvider.value = false
    await load()
  } catch (e: any) {
    message.error('保存失败: ' + (e.message || e))
  }
}

async function delProvider(p: ProviderRow) {
  try {
    await api.llmApi.providerDelete(p.id)
    message.success('供应商已删除')
    delete expanded.value[p.id]
    delete modelsMap.value[p.id]
    delete keysMap.value[p.id]
    await load()
  } catch (e: any) {
    message.error('删除失败: ' + (e.message || e))
  }
}

function openAddKey(p: ProviderRow) {
  keyProvider.value = p
  keyForm.value = { apiKey: '', remark: '' }
  showKey.value = true
}

async function saveKey() {
  if (!keyProvider.value) return
  if (!keyForm.value.apiKey.trim()) {
    message.warning('请填写 API Key')
    return
  }
  try {
    await api.llmApi.keySave({ providerId: keyProvider.value.id, apiKey: keyForm.value.apiKey.trim(), remark: keyForm.value.remark.trim() })
    message.success('Key 已添加')
    showKey.value = false
    await loadKeys(keyProvider.value)
  } catch (e: any) {
    message.error('保存失败: ' + (e.message || e))
  }
}

async function delKey(k: ApiKeyRow) {
  try {
    await api.llmApi.keyDelete(k.id)
    message.success('Key 已删除')
    await loadKeys({ id: k.providerId } as ProviderRow)
  } catch (e: any) {
    message.error('删除失败: ' + (e.message || e))
  }
}

function openModel(p: ProviderRow) {
  curProvider.value = p
  mForm.value = { modelName: '', capabilities: [] }
  showModel.value = true
}

async function saveModel() {
  if (!curProvider.value) return
  if (!mForm.value.modelName.trim()) {
    message.warning('请填写模型名')
    return
  }
  try {
    await api.llmApi.modelSave({
      providerId: curProvider.value.id,
      modelName: mForm.value.modelName.trim(),
      capabilities: mForm.value.capabilities.join(',')
    })
    message.success('模型已添加')
    showModel.value = false
    await loadModels(curProvider.value)
  } catch (e: any) {
    message.error('保存失败: ' + (e.message || e))
  }
}

async function delModel(m: ModelRow) {
  try {
    await api.llmApi.modelDelete(m.id)
    message.success('模型已删除')
    await loadModels({ id: m.providerId } as ProviderRow)
  } catch (e: any) {
    message.error('删除失败: ' + (e.message || e))
  }
}

async function fetchModels(p: ProviderRow) {
  try {
    const res = await api.llmApi.fetchModels(p.id)
    if (res.ok && res.models && res.models.length > 0) {
      for (const m of res.models) {
        await api.llmApi.modelSave({ providerId: p.id, modelName: m, capabilities: '' })
      }
      message.success(`已从供应商拉取 ${res.models.length} 个模型，请为它们勾选能力`)
      await loadModels(p)
    } else {
      message.warning(res.error || '未获取到模型列表')
    }
  } catch (e: any) {
    message.error('拉取失败: ' + (e.message || e))
  }
}

const modelColumns = [
  { title: '模型名', key: 'modelName', ellipsis: { tooltip: true } },
  { title: '能力', key: 'capabilities', render: (m: ModelRow) => {
      const caps = (m.capabilities || '').split(',').filter(Boolean)
      return caps.length > 0
        ? h(NSpace, { size: 4 }, { default: () => caps.map(c =>
            h(NTag, { size: 'small', type: c === 'TTS' ? 'warning' : 'info' }, { default: () => c })) })
        : h(NTag, { size: 'small', type: 'default' }, { default: () => '未设能力' })
    } },
  { title: '操作', key: 'actions', width: 80, render: (m: ModelRow) =>
      h(NPopconfirm, { onPositiveClick: () => delModel(m) }, {
        trigger: () => h(NButton, { size: 'tiny', type: 'error', secondary: true }, { default: () => '删除' }),
        default: () => '确认删除该模型？'
      }) },
]

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHero title="供应商管理" subtitle="配置多个 AI 供应商（DeepSeek / 智谱 / 小米 / Fish…），每个供应商下管理其大模型与 API Key 并声明能力；AI 设置页按能力选择「供应商 + 模型」" :icon="ServerOutline">
      <NButton type="primary" @click="openAddProvider">
        <template #icon><NIcon><AddOutline /></NIcon></template>
        添加供应商
      </NButton>
    </PageHero>

    <NSpin :show="loading">
      <NEmpty v-if="!loading && providers.length === 0" description="暂无供应商，点「添加供应商」开始配置（如 DeepSeek / 智谱 / 小米 MiMo / Fish Audio）" />
      <NCard v-for="p in providers" :key="p.id" :bordered="true" class="provider-card">
        <div class="provider-head" @click="toggleExpand(p)">
          <NIcon :component="expanded[p.id] ? ChevronDownOutline : ChevronForwardOutline" />
          <NText strong style="font-size: 15px">{{ p.name }}</NText>
          <NTag size="small" type="info">{{ p.providerType }}</NTag>
          <NText depth="3" style="flex: 1; margin-left: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ p.baseUrl }}</NText>
          <NText depth="3">Key: {{ p.apiKey || '未配置' }} · {{ p.modelCount || 0 }} 个模型</NText>
        </div>

        <div v-if="expanded[p.id]" class="provider-body">
          <NSpace style="margin-bottom: 10px">
            <NButton size="small" secondary @click="openModel(p)">
              <template #icon><NIcon><AddOutline /></NIcon></template>
              添加模型
            </NButton>
            <NButton size="small" secondary @click="fetchModels(p)">
              <template #icon><NIcon><CloudDownloadOutline /></NIcon></template>
              自动拉取模型
            </NButton>
            <NButton size="small" secondary @click="openEditProvider(p)">
              <template #icon><NIcon><CreateOutline /></NIcon></template>
              编辑
            </NButton>
            <NPopconfirm :on-positive-click="() => delProvider(p)">
              <template #trigger>
                <NButton size="small" type="error" secondary>
                  <template #icon><NIcon><TrashOutline /></NIcon></template>
                  删除供应商
                </NButton>
              </template>
              删除供应商将同时删除其下所有模型和 Key，确认？
            </NPopconfirm>
          </NSpace>

          <NDivider style="margin: 8px 0">API Key（可配多个，按序轮询容灾）</NDivider>
          <NSpace style="margin-bottom: 8px">
            <NButton size="tiny" secondary @click="openAddKey(p)">
              <template #icon><NIcon><KeyOutline /></NIcon></template>
              添加 Key
            </NButton>
            <NText v-if="keysMap[p.id] && keysMap[p.id].length === 0" depth="3">暂无额外 Key（使用供应商基础 Key）</NText>
            <NTag v-for="k in keysMap[p.id] || []" :key="k.id" size="small" closable :type="k.enabled ? 'info' : 'default'" @close="delKey(k)">
              {{ k.remark || 'Key' }}: {{ k.apiKey }}
            </NTag>
          </NSpace>

          <NDivider style="margin: 8px 0">模型列表</NDivider>
          <NSpin :show="!!modelLoading[p.id]">
            <NDataTable :columns="modelColumns" :data="modelsMap[p.id] || []" :bordered="false" :row-key="(r: ModelRow) => r.id" />
          </NSpin>
        </div>
      </NCard>
    </NSpin>

    <NAlert type="info" :bordered="false" :show-icon="false">
      <NText depth="2">提示：在「AI 设置」里按能力（对话/图片理解/图像生成/语音生成）选择供应商和模型。OpenAI 兼容供应商可直接「从供应商拉取模型」。</NText>
    </NAlert>

    <!-- 供应商弹窗 -->
    <NModal v-model:show="showProvider" preset="card" :title="editingProvider ? '编辑供应商' : '添加供应商'" style="width: 480px">
      <NForm label-placement="left" label-width="100" label-align="right">
        <NFormItem label="名称">
          <NInput v-model:value="pForm.name" placeholder="如：DeepSeek / 智谱 GLM / 小米 MiMo / Fish Audio" />
        </NFormItem>
        <NFormItem label="类型">
          <NSelect v-model:value="pForm.providerType" :options="[
            { label: 'openai（通用 OpenAI 兼容）', value: 'openai' },
            { label: 'anthropic（Anthropic 兼容）', value: 'anthropic' },
            { label: 'deepseek（DeepSeek）', value: 'deepseek' },
            { label: 'glm（智谱 GLM）', value: 'glm' },
            { label: 'fish（Fish Audio TTS）', value: 'fish' },
            { label: 'mimo（小米 MiMo TTS）', value: 'mimo' }
          ]" />
        </NFormItem>
        <NFormItem label="Base URL">
          <NInput v-model:value="pForm.baseUrl" placeholder="如：https://api.deepseek.com / https://open.bigmodel.cn/api/paas/v4" />
        </NFormItem>
        <NFormItem label="API Key">
          <NInput v-model:value="pForm.apiKey" type="password" show-password-on="click" :placeholder="editingProvider ? '留空保持不变（可展开后在 Key 区添加多个）' : '供应商平台申请的 Key'" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showProvider = false">取消</NButton>
          <NButton type="primary" @click="saveProvider">{{ editingProvider ? '保存' : '添加' }}</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- Key 弹窗 -->
    <NModal v-model:show="showKey" preset="card" :title="'为「' + (keyProvider?.name || '') + '」添加 API Key'" style="width: 460px">
      <NForm label-placement="left" label-width="100" label-align="right">
        <NFormItem label="API Key">
          <NInput v-model:value="keyForm.apiKey" type="password" show-password-on="click" placeholder="供应商平台申请的另一个 Key" />
        </NFormItem>
        <NFormItem label="备注">
          <NInput v-model:value="keyForm.remark" placeholder="如：账号A / 备用 / 免费额度" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showKey = false">取消</NButton>
          <NButton type="primary" @click="saveKey">添加</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- 模型弹窗 -->
    <NModal v-model:show="showModel" preset="card" :title="'为「' + (curProvider?.name || '') + '」添加模型'" style="width: 480px">
      <NForm label-placement="left" label-width="100" label-align="right">
        <NFormItem label="模型名">
          <NInput v-model:value="mForm.modelName" placeholder="如：deepseek-v4-flash / glm-4.6v-flash / mimo-v2.5-tts" />
        </NFormItem>
        <NFormItem label="能力">
          <NCheckboxGroup v-model:value="mForm.capabilities">
            <NSpace>
              <NCheckbox v-for="c in CAPS" :key="c.value" :value="c.value" :label="c.label" />
            </NSpace>
          </NCheckboxGroup>
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showModel = false">取消</NButton>
          <NButton type="primary" @click="saveModel">添加</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 14px; }
.provider-card { padding: 4px; }
.provider-head { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.provider-body { margin-top: 12px; }
</style>
