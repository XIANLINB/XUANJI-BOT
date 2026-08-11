<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NCard, NButton, NSpace, NSelect, NInput, NDataTable, NModal, NForm, NFormItem,
  NIcon, NPopconfirm, NEmpty, NSpin, NTag, NAlert, NText, NUpload
} from 'naive-ui'
import { LibraryOutline, AddOutline, TrashOutline, SearchOutline, DocumentTextOutline } from '@vicons/ionicons5'
import api from '../api'
import type { KbDocRow } from '../api/llm'
import PageHero from '../components/PageHero.vue'

const message = useMessage()
const bots = ref<any[]>([])
const botKey = ref<string>('')
const docs = ref<KbDocRow[]>([])
const loading = ref(false)

const botOpts = computed(() => bots.value.map(b => ({ label: b.name || b.botKey || b.appId || '', value: b.botKey || b.appId || '' })))

const showModal = ref(false)
const docName = ref('')
const docContent = ref('')
const saving = ref(false)

const q = ref('')
const searchResult = ref('')
const searching = ref(false)

async function load() {
  if (!botKey.value) return
  loading.value = true
  try {
    docs.value = (await api.llmApi.kbList(botKey.value)) || []
  } catch (e: any) {
    message.error('加载知识库失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

async function loadBots() {
  try {
    bots.value = (await api.getBots()) || []
    if (bots.value.length > 0) botKey.value = bots.value[0].botKey || bots.value[0].appId || ''
  } catch {
    bots.value = []
  }
}

async function upload() {
  if (!docContent.value.trim()) {
    message.warning('文档内容不能为空')
    return
  }
  saving.value = true
  try {
    await api.llmApi.kbUpload({ botKey: botKey.value, name: docName.value.trim() || '未命名文档', content: docContent.value })
    message.success('已上传到知识库')
    showModal.value = false
    docName.value = ''
    docContent.value = ''
    await load()
  } catch (e: any) {
    message.error('上传失败: ' + (e.message || e))
  } finally {
    saving.value = false
  }
}

async function handleFileUpload({ file, onFinish, onError }: any) {
  try {
    const raw = file.file as File
    if (!raw) return
    const res = await api.llmApi.kbUploadFile(botKey.value, raw)
    if (res && res.ok) {
      message.success(`已上传「${res.name || raw.name}」到知识库`)
      onFinish()
      await load()
    } else {
      message.error((res && res.error) || '上传失败')
      onError()
    }
  } catch (e: any) {
    message.error('上传失败: ' + (e.message || e))
    onError()
  }
}

async function remove(r: KbDocRow) {
  try {
    await api.llmApi.kbDelete(r.id)
    message.success('已删除')
    await load()
  } catch (e: any) {
    message.error('删除失败: ' + (e.message || e))
  }
}

async function search() {
  if (!q.value.trim()) return
  searching.value = true
  try {
    const res = await api.llmApi.kbSearch(botKey.value, q.value.trim())
    searchResult.value = res.result || ''
  } catch (e: any) {
    message.error('检索失败: ' + (e.message || e))
  } finally {
    searching.value = false
  }
}

const columns = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '名称', key: 'name', ellipsis: { tooltip: true }, render: (r: KbDocRow) => r.name || '—' },
  { title: '字符数', key: 'charCount', width: 90 },
  { title: '分段数', key: 'chunkCount', width: 90 },
  { title: '上传时间', key: 'createdAt', width: 160 },
  { title: '操作', key: 'actions', width: 80, render: (r: KbDocRow) =>
      h(NPopconfirm, { onPositiveClick: () => remove(r) }, {
        trigger: () => h(NButton, { size: 'tiny', type: 'error', secondary: true }, { default: () => '删除' }),
        default: () => '确认删除？'
      }) },
]

onMounted(async () => {
  await loadBots()
  await load()
})
</script>

<template>
  <div class="page">
    <PageHero title="知识库" subtitle="上传文档(txt/md) → 分段入库 → 关键词检索。群里 @机器人 问知识库相关问题，AI 会自动检索回答" :icon="LibraryOutline">
      <NSpace>
        <NSelect v-model:value="botKey" :options="botOpts" placeholder="选择机器人" clearable style="width: 180px" @update:value="load" />
        <NButton type="primary" @click="showModal = true">
          <template #icon><NIcon><AddOutline /></NIcon></template>
          上传文档
        </NButton>
      </NSpace>
    </PageHero>

    <NCard :bordered="true">
      <NSpin :show="loading">
        <NEmpty v-if="!loading && docs.length === 0" description="暂无文档，点击「上传文档」添加（支持 txt/md 文本）" />
        <NDataTable v-else :columns="columns" :data="docs" :row-key="(r: KbDocRow) => r.id" :bordered="false" />
      </NSpin>
    </NCard>

    <NCard :bordered="true" title="测试问答">
      <NSpace style="width: 100%">
        <NInput v-model:value="q" placeholder="输入问题，如「怎么配置多机器人」" @keyup.enter="search" style="flex: 1" />
        <NButton type="primary" secondary :loading="searching" @click="search">
          <template #icon><NIcon><SearchOutline /></NIcon></template>
          检索
        </NButton>
      </NSpace>
      <NAlert v-if="searchResult" type="info" :bordered="false" class="result" :show-icon="false">
        <NText depth="2" style="white-space: pre-wrap">{{ searchResult }}</NText>
      </NAlert>
    </NCard>

    <NModal v-model:show="showModal" preset="card" title="上传文档到知识库" style="width: 640px">
      <NForm label-placement="left" label-width="80" label-align="right">
        <NFormItem label="文件上传">
          <NUpload accept=".txt,.md,.csv,.json,.log" :custom-request="handleFileUpload" :show-file-list="true" name="file">
            <NButton secondary>
              <template #icon><NIcon><DocumentTextOutline /></NIcon></template>
              选择文件（txt / md / csv / json）
            </NButton>
          </NUpload>
        </NFormItem>
        <NFormItem label="名称">
          <NInput v-model:value="docName" placeholder="如：璇玑配置指南（留空自动命名）" />
        </NFormItem>
        <NFormItem label="内容">
          <NInput v-model:value="docContent" type="textarea" :autosize="{ minRows: 8, maxRows: 16 }" placeholder="或直接粘贴文档文本（txt/md 格式）" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showModal = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="upload">上传文本</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.result {
  margin-top: 12px;
}
</style>
