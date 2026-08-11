<script setup lang="ts">
import {
  ref, computed, onMounted, h
} from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NInput, NGrid, NGi, NEmpty,
  NPopconfirm, NModal, NAlert, useMessage, NSelect, NSpin
} from 'naive-ui'
import {
  StorefrontOutline, RefreshOutline, SearchOutline, SettingsOutline, TrashOutline, CubeOutline,
  PlayOutline, StopOutline
} from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import StatCard from '../components/StatCard.vue'
import api from '../api'

const router = useRouter()
const message = useMessage()
const rows = ref<any[]>([])
const keyword = ref('')
const category = ref('all')
const loading = ref(false)
const scanning = ref(false)

// 插件分类（五大类）
const CATEGORIES = [
  { value: 'all', label: '全部' },
  { value: 'entertainment', label: '娱乐' },
  { value: 'tool', label: '工具' },
  { value: 'group-admin', label: '群管' },
  { value: 'service', label: '服务' },
  { value: 'other', label: '其他' }
]
const CATEGORY_LABEL: Record<string, string> = {
  entertainment: '娱乐', tool: '工具', 'group-admin': '群管', service: '服务', other: '其他'
}

// 命令执行统计（风控数据源）
const cmdStat = ref<Record<string, any>>({ execCount: 0, failCount: 0, successRate: 100, rateLimitHits: 0 })
async function loadCmdStat() {
  try {
    const o = await api.riskOverview()
    cmdStat.value = { ...o.command, rateLimitHits: o.rateLimit?.commandHits ?? 0 }
  } catch { /* 统计失败不影响列表 */ }
}

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return rows.value.filter((p) => {
    if (category.value !== 'all' && p.category !== category.value) return false
    if (!kw) return true
    return (p.name || '').toLowerCase().includes(kw) ||
      (p.id || '').toLowerCase().includes(kw) ||
      (p.description || '').toLowerCase().includes(kw)
  })
})

async function load() {
  loading.value = true
  try {
    rows.value = await api.getPlugins()
  } catch (e: any) {
    message.error('加载插件失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

async function scan() {
  scanning.value = true
  try {
    const r = await api.scanPlugins()
    const n = (r?.loaded || []).length
    message.success(n > 0 ? `扫描完成，新加载 ${n} 个插件` : '扫描完成，无新插件')
    await load()
  } catch (e: any) {
    message.error('扫描失败：' + (e?.message ?? e))
  } finally {
    scanning.value = false
  }
}

async function toggle(p: any) {
  try {
    if (p.running) {
      await api.stopPlugin(p.id)
      message.success('已停用：' + (p.name || p.id))
    } else {
      await api.startPlugin(p.id)
      message.success('已启用：' + (p.name || p.id))
    }
    await load()
  } catch (e: any) {
    message.error('操作失败：' + (e?.message ?? e))
  }
}

async function reload(p: any) {
  try {
    const r = await api.reloadPlugin(p.id)
    if (r.status === 'error') message.error('热重载失败（看后端日志）')
    else message.success('已热重载：' + (p.name || p.id))
    await load()
  } catch (e: any) {
    message.error('热重载失败：' + (e?.message ?? e))
  }
}

async function unload(p: any) {
  try {
    const r = await api.unloadPlugin(p.id)
    if (r.status === 'ok') message.success('已卸载：' + (p.name || p.id))
    else message.error('卸载失败（看后端日志）')
    await load()
  } catch (e: any) {
    message.error('卸载失败：' + (e?.message ?? e))
  }
}

// ===== 卸载确认弹窗 =====
const showUnloadModal = ref(false)
const confirmPlugin = ref<any>(null)
function askUnload(p: any) {
  confirmPlugin.value = p
  showUnloadModal.value = true
}
function doUnload() {
  if (confirmPlugin.value) unload(confirmPlugin.value)
  showUnloadModal.value = false
}

function openPage(p: any) {
  router.push('/plugins/p/' + p.id)
}

onMounted(() => { load(); loadCmdStat() })
</script>

<template>
  <div>
    <PageHero title="插件市场" subtitle="卡片式管理全部插件 · 扫描 / 启停 / 热重载 / 卸载" :icon="StorefrontOutline">
      <NSpace align="center">
        <NButton type="primary" :loading="scanning" @click="scan">
          <template #icon><NIcon><RefreshOutline /></NIcon></template>
          扫描新插件
        </NButton>
        <NInput v-model:value="keyword" placeholder="搜索插件名 / ID / 描述" clearable style="width: 220px">
          <template #prefix><NIcon :component="SearchOutline" /></template>
        </NInput>
      </NSpace>
    </PageHero>

    <!-- 分类筛选 -->
    <NSpace align="center" style="margin-bottom: 12px">
      <NSelect v-model:value="category" :options="CATEGORIES" size="small" style="width: 140px" />
      <NText depth="3" style="font-size: 12px">分类：娱乐 / 工具 / 群管 / 服务 / 其他 · 来源：官方 / 社区</NText>
    </NSpace>

    <NEmpty v-if="!loading && !filtered.length" description="暂无插件，点「扫描新插件」或把 jar 放入 plugins/ 目录" style="padding: 60px 0" />

    <!-- 插件执行统计（来自框架命令注册表） -->
    <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 16px">
      <NGi span="24 s:12 m:8 l:6">
        <StatCard :icon="PlayOutline" color="#5b5bd6" :value="Number(cmdStat.execCount ?? 0)" label="命令执行次数（进程累计）" />
      </NGi>
      <NGi span="24 s:12 m:8 l:6">
        <StatCard :icon="StopOutline" color="#e5484d" :value="Number(cmdStat.failCount ?? 0)" label="命令执行异常" />
      </NGi>
      <NGi span="24 s:12 m:8 l:6">
        <StatCard :icon="PlayOutline" color="#18a058" :value="(cmdStat.successRate ?? 100) + '%'" label="命令成功率" :animate="false" />
      </NGi>
      <NGi span="24 s:12 m:8 l:6">
        <StatCard :icon="SettingsOutline" color="#f0a020" :value="Number(cmdStat.rateLimitHits ?? 0)" label="命令限速命中（@Command rateLimit）" />
      </NGi>
    </NGrid>

    <NGrid :cols="24" :x-gap="16" :y-gap="16" responsive="screen" item-responsive class="grid">
      <NGi v-for="p in filtered" :key="p.id" span="24 m:12 l:8 xl:6">
        <NCard hoverable class="p-card" @click="openPage(p)">
          <div class="p-head">
            <div class="p-icon"><NIcon size="20"><CubeOutline /></NIcon></div>
            <div class="p-title">
              <div class="p-name">
                <NText strong>{{ p.name || p.id }}</NText>
                <NTag size="small" :bordered="false" :type="p.running ? 'success' : 'default'" round>{{ p.running ? '运行中' : '已停用' }}</NTag>
                <NTag size="small" :bordered="false" :type="p.origin === 'official' ? 'info' : 'warning'" round>{{ p.origin === 'official' ? '官方' : '社区' }}</NTag>
                <NTag size="small" :bordered="false" type="primary" round>{{ CATEGORY_LABEL[p.category] || '其他' }}</NTag>
              </div>
              <NText depth="3" style="font-size: 12px">{{ p.id }} · v{{ p.version }}</NText>
            </div>
          </div>
          <NText depth="3" class="p-desc">{{ p.description || '—' }}</NText>
          <div class="p-author">作者：{{ p.provider || '—' }}</div>
          <div class="p-ops" @click.stop>
            <NButton
              size="small"
              block
              :type="p.running ? 'warning' : 'primary'"
              :disabled="loading"
              class="op-main"
              @click="toggle(p)"
            >
              <template #icon><NIcon size="14"><component :is="p.running ? StopOutline : PlayOutline" /></NIcon></template>
              {{ p.running ? '停用' : '启用' }}
            </NButton>
            <NGrid :cols="3" :x-gap="6" class="op-sub">
              <NGi><NButton size="small" secondary block @click="reload(p)">
                <template #icon><NIcon size="14"><RefreshOutline /></NIcon></template>重载
              </NButton></NGi>
              <NGi><NButton size="small" secondary block @click="openPage(p)">
                <template #icon><NIcon size="14"><SettingsOutline /></NIcon></template>配置
              </NButton></NGi>
              <NGi><NButton size="small" type="error" ghost block @click="askUnload(p)">
                <template #icon><NIcon size="14"><TrashOutline /></NIcon></template>卸载
              </NButton></NGi>
            </NGrid>
          </div>
        </NCard>
      </NGi>
    </NGrid>

    <!-- 卸载确认弹窗 -->
    <NModal
      v-model:show="showUnloadModal"
      preset="card"
      :title="`确认卸载插件 · ${confirmPlugin?.name || confirmPlugin?.id || ''}`"
      style="width: 500px; max-width: 92vw"
      :bordered="false"
    >
      <NAlert v-if="confirmPlugin" type="warning" :show-icon="true" title="此操作不可恢复">
        即将卸载插件「{{ confirmPlugin.name || confirmPlugin.id }}」，将执行以下操作：
      </NAlert>
      <NText tag="div" depth="3" style="display: block; margin: 12px 0 4px; font-size: 13px">影响范围：</NText>
      <ul class="unload-list">
        <li>关闭插件的 Spring 子容器</li>
        <li>反注册全部命令与事件处理器</li>
        <li>删除插件 jar 文件（plugins/&lt;id&gt;.jar）</li>
        <li>清除该插件的持久化数据（xuanji_plugin_kv 全部记录）</li>
      </ul>
      <template #footer>
        <NSpace justify="end" :size="8">
          <NButton size="small" @click="showUnloadModal = false">取消</NButton>
          <NButton size="small" type="error" @click="doUnload">确认卸载</NButton>
        </NSpace>
      </template>
    </NModal>

  </div>
</template>

<style scoped>
.grid { margin-top: 4px; }
.p-card { height: 100%; cursor: pointer; }
.p-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.p-icon {
  width: 36px; height: 36px; border-radius: 10px; flex-shrink: 0;
  background: #e6f1fb; color: #185fa5;
  display: flex; align-items: center; justify-content: center;
}
.p-title { min-width: 0; flex: 1; }
.p-name { display: flex; align-items: center; gap: 8px; }
.p-desc {
  display: block; font-size: 12px; min-height: 36px; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.p-author { font-size: 12px; color: var(--n-text-color-3); margin: 6px 0 12px; }
.p-ops { display: flex; flex-direction: column; gap: 6px; }
.op-main { border-radius: 7px; }
.op-sub :deep(.n-button) { border-radius: 6px; padding-left: 6px; padding-right: 6px; }
.unload-list { margin: 0; padding-left: 20px; line-height: 1.9; font-size: 13px; color: var(--n-text-color-2); }
</style>
