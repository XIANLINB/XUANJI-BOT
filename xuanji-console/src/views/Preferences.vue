<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  NCard, NSpace, NText, NTag, NIcon, NTabs, NTabPane, NAlert,
  NRadioGroup, NRadioButton, NSelect, NSwitch, NButton, NInput,
  NPopconfirm, useMessage
} from 'naive-ui'
import { ColorPaletteOutline, GridOutline, OptionsOutline, PulseOutline, RocketOutline, SearchOutline, RefreshOutline, DownloadOutline, CloudUploadOutline } from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import { usePreferencesStore, type Preferences, type BrandPreset, type ThemeMode, type Density, type SidebarMode, type TimeFormat, type DateFormat, type Motion } from '../stores/preferences'
import { BRAND_PRESETS } from '../theme'

const message = useMessage()
const prefs = usePreferencesStore()
const filterText = ref('')
const activeTab = ref('appearance')

// ─── 品牌色预设（带色块展示）───
const brandOptions: { label: string; value: BrandPreset; color: string }[] = [
  { label: '靛紫', value: 'indigo', color: BRAND_PRESETS.indigo.primary },
  { label: '碧蓝', value: 'blue', color: BRAND_PRESETS.blue.primary },
  { label: '翠绿', value: 'green', color: BRAND_PRESETS.green.primary },
  { label: '玫粉', value: 'pink', color: BRAND_PRESETS.pink.primary }
]

const themeModeOptions: { label: string; value: ThemeMode; desc: string }[] = [
  { label: '浅色', value: 'light', desc: '默认亮色，明亮清爽' },
  { label: '深色', value: 'dark', desc: '暗色护眼，夜间友好' },
  { label: '跟随系统', value: 'system', desc: '随操作系统主题自动切换' }
]

const fontSizeOptions = [
  { label: '小（13px）', value: 13 },
  { label: '中（14px）', value: 14 },
  { label: '大（16px）', value: 16 }
]

const densityOptions: { label: string; value: Density; desc: string }[] = [
  { label: '宽松', value: 'comfortable', desc: '卡片内边距大，呼吸感强' },
  { label: '紧凑', value: 'compact', desc: '信息密度高，一屏看更多' }
]

const sidebarOptions: { label: string; value: SidebarMode; desc: string }[] = [
  { label: '展开', value: 'expanded', desc: '完整显示菜单文字' },
  { label: '折叠', value: 'collapsed', desc: '仅图标，点击展开' },
  { label: '自动隐藏', value: 'auto', desc: '悬停弹出，常态收起' }
]

const homeRouteOptions = [
  { label: '仪表盘', value: 'dashboard' },
  { label: '消息监控', value: 'monitor' },
  { label: '运行监控', value: 'health' },
  { label: '定时任务', value: 'scheduler' },
  { label: '框架配置', value: 'settings' }
]

const timeFormatOptions: { label: string; value: TimeFormat }[] = [
  { label: '24 小时制（14:30:00）', value: '24h' },
  { label: '12 小时制（02:30:00 PM）', value: '12h' }
]

const dateFormatOptions: { label: string; value: DateFormat }[] = [
  { label: '简洁（08-15）', value: 'short' },
  { label: '完整（2026-08-15）', value: 'full' }
]

const motionOptions: { label: string; value: Motion; desc: string }[] = [
  { label: '平滑', value: 'smooth', desc: '全部过渡动画' },
  { label: '适度', value: 'moderate', desc: '仅必要的过渡' },
  { label: '关闭', value: 'off', desc: '减少动效（性能优先）' }
]

// ─── 搜索过滤：跨 Tab 按关键字匹配 ───
function matches(label: string): boolean {
  if (!filterText.value.trim()) return true
  return label.toLowerCase().includes(filterText.value.trim().toLowerCase())
}

// ─── 即时预览：改即生效（prefs.update 自动持久化）───
async function update<K extends keyof Preferences>(key: K, value: Preferences[K]) {
  await prefs.update({ [key]: value } as Partial<Preferences>)
  message.success('已应用并保存')
}

// ─── 高级：重置 / 导入 / 导出 ───
const importing = ref(false)
const importText = ref('')
const showImport = ref(false)

async function resetAll() {
  await prefs.reset()
  message.success('已恢复出厂默认设置')
}

function exportConfig() {
  const json = prefs.exportJson()
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'xuanji-preferences.json'
  a.click()
  URL.revokeObjectURL(url)
  message.success('配置已导出')
}

async function importConfig() {
  if (!importText.value.trim()) return message.warning('请粘贴配置 JSON')
  try {
    await prefs.importJson(importText.value)
    showImport.value = false
    importText.value = ''
    message.success('配置已导入并应用')
  } catch {
    message.error('配置 JSON 格式错误')
  }
}

// 当前生效标记
function isActive(key: keyof Preferences, value: any): boolean {
  return (prefs.prefs as any)[key] === value
}
</script>

<template>
  <div>
    <PageHero title="个性化设置" subtitle="主题 · 布局 · 偏好 · 交互 · 高级 —— 改即生效，跨设备同步" :icon="ColorPaletteOutline">
      <NInput v-model:value="filterText" clearable placeholder="搜索设置项" style="width: 220px">
        <template #prefix><NIcon><SearchOutline /></NIcon></template>
      </NInput>
    </PageHero>

    <NCard :bordered="false">
      <NTabs v-model:value="activeTab" type="line" animated size="large" placement="left" style="gap: 0">

        <!-- ════════ 外观 ════════ -->
        <NTabPane name="appearance">
          <template #tab>
            <div class="tab-label"><NIcon size="18" color="#5b5bd6"><ColorPaletteOutline /></NIcon><span>外观</span></div>
          </template>

          <div v-if="matches('主题模式')" class="setting-row">
            <div class="setting-label">
              <NText class="setting-label-text">主题模式</NText>
              <NText depth="3" class="setting-desc">浅色 / 深色 / 跟随系统偏好</NText>
            </div>
            <div class="setting-field">
              <NRadioGroup :value="prefs.prefs.themeMode" @update:value="(v: ThemeMode) => update('themeMode', v)">
                <NRadioButton v-for="o in themeModeOptions" :key="o.value" :value="o.value">
                  {{ o.label }}
                </NRadioButton>
              </NRadioGroup>
              <NText depth="3" class="setting-hint">{{ themeModeOptions.find(o => o.value === prefs.prefs.themeMode)?.desc }}</NText>
            </div>
          </div>

          <div v-if="matches('品牌色')" class="setting-row">
            <div class="setting-label">
              <NText class="setting-label-text">品牌色</NText>
              <NText depth="3" class="setting-desc">按钮 / 链接 / 选中态主色</NText>
            </div>
            <div class="setting-field">
              <NRadioGroup :value="prefs.prefs.brand" @update:value="(v: BrandPreset) => update('brand', v)">
                <NRadioButton v-for="o in brandOptions" :key="o.value" :value="o.value">
                  <span class="color-chip" :style="{ background: o.color }"></span>
                  {{ o.label }}
                </NRadioButton>
              </NRadioGroup>
            </div>
          </div>

          <div v-if="matches('字号')" class="setting-row">
            <div class="setting-label">
              <NText class="setting-label-text">正文字号</NText>
              <NText depth="3" class="setting-desc">全局组件默认字号</NText>
            </div>
            <div class="setting-field">
              <NRadioGroup :value="prefs.prefs.fontSize" @update:value="(v: number) => update('fontSize', v)">
                <NRadioButton v-for="o in fontSizeOptions" :key="o.value" :value="o.value">{{ o.label }}</NRadioButton>
              </NRadioGroup>
            </div>
          </div>

          <div v-if="matches('密度')" class="setting-row">
            <div class="setting-label">
              <NText class="setting-label-text">页面密度</NText>
              <NText depth="3" class="setting-desc">卡片内边距与信息紧凑度</NText>
            </div>
            <div class="setting-field">
              <NRadioGroup :value="prefs.prefs.density" @update:value="(v: Density) => update('density', v)">
                <NRadioButton v-for="o in densityOptions" :key="o.value" :value="o.value">{{ o.label }}</NRadioButton>
              </NRadioGroup>
              <NText depth="3" class="setting-hint">{{ densityOptions.find(o => o.value === prefs.prefs.density)?.desc }}</NText>
            </div>
          </div>
        </NTabPane>

        <!-- ════════ 布局 ════════ -->
        <NTabPane name="layout">
          <template #tab>
            <div class="tab-label"><NIcon size="18" color="#2090e0"><GridOutline /></NIcon><span>布局</span></div>
          </template>

          <div v-if="matches('侧边栏')" class="setting-row">
            <div class="setting-label">
              <NText class="setting-label-text">侧边栏模式</NText>
              <NText depth="3" class="setting-desc">导航栏展开/折叠/自动隐藏</NText>
            </div>
            <div class="setting-field">
              <NRadioGroup :value="prefs.prefs.sidebarMode" @update:value="(v: SidebarMode) => update('sidebarMode', v)">
                <NRadioButton v-for="o in sidebarOptions" :key="o.value" :value="o.value">{{ o.label }}</NRadioButton>
              </NRadioGroup>
              <NText depth="3" class="setting-hint">{{ sidebarOptions.find(o => o.value === prefs.prefs.sidebarMode)?.desc }}</NText>
            </div>
          </div>

          <div v-if="matches('默认首页')" class="setting-row">
            <div class="setting-label">
              <NText class="setting-label-text">默认首页</NText>
              <NText depth="3" class="setting-desc">登录后默认进入的页面</NText>
            </div>
            <div class="setting-field">
              <NSelect :value="prefs.prefs.homeRoute" :options="homeRouteOptions" style="width: 260px"
                @update:value="(v: string) => update('homeRoute', v)" />
            </div>
          </div>
        </NTabPane>

        <!-- ════════ 偏好 ════════ -->
        <NTabPane name="preference">
          <template #tab>
            <div class="tab-label"><NIcon size="18" color="#18a058"><OptionsOutline /></NIcon><span>偏好</span></div>
          </template>

          <div v-if="matches('时间格式')" class="setting-row">
            <div class="setting-label">
              <NText class="setting-label-text">时间格式</NText>
              <NText depth="3" class="setting-desc">全站时间显示的 12/24 小时制</NText>
            </div>
            <div class="setting-field">
              <NRadioGroup :value="prefs.prefs.timeFormat" @update:value="(v: TimeFormat) => update('timeFormat', v)">
                <NRadioButton v-for="o in timeFormatOptions" :key="o.value" :value="o.value">{{ o.label }}</NRadioButton>
              </NRadioGroup>
            </div>
          </div>

          <div v-if="matches('日期格式')" class="setting-row">
            <div class="setting-label">
              <NText class="setting-label-text">日期格式</NText>
              <NText depth="3" class="setting-desc">简洁（MM-DD）或完整（YYYY-MM-DD）</NText>
            </div>
            <div class="setting-field">
              <NRadioGroup :value="prefs.prefs.dateFormat" @update:value="(v: DateFormat) => update('dateFormat', v)">
                <NRadioButton v-for="o in dateFormatOptions" :key="o.value" :value="o.value">{{ o.label }}</NRadioButton>
              </NRadioGroup>
            </div>
          </div>
        </NTabPane>

        <!-- ════════ 交互 ════════ -->
        <NTabPane name="interaction">
          <template #tab>
            <div class="tab-label"><NIcon size="18" color="#f0a020"><PulseOutline /></NIcon><span>交互</span></div>
          </template>

          <div v-if="matches('动效')" class="setting-row">
            <div class="setting-label">
              <NText class="setting-label-text">动画与动效</NText>
              <NText depth="3" class="setting-desc">过渡动画强度</NText>
            </div>
            <div class="setting-field">
              <NRadioGroup :value="prefs.prefs.motion" @update:value="(v: Motion) => update('motion', v)">
                <NRadioButton v-for="o in motionOptions" :key="o.value" :value="o.value">{{ o.label }}</NRadioButton>
              </NRadioGroup>
              <NText depth="3" class="setting-hint">{{ motionOptions.find(o => o.value === prefs.prefs.motion)?.desc }}</NText>
            </div>
          </div>

          <div v-if="matches('操作确认')" class="setting-row">
            <div class="setting-label">
              <NText class="setting-label-text">危险操作二次确认</NText>
              <NText depth="3" class="setting-desc">删除 / 重置等操作前弹窗确认</NText>
            </div>
            <div class="setting-field">
              <NSwitch :value="prefs.prefs.confirmDestructive"
                @update:value="(v: boolean) => update('confirmDestructive', v)" />
            </div>
          </div>
        </NTabPane>

        <!-- ════════ 高级 ════════ -->
        <NTabPane name="advanced">
          <template #tab>
            <div class="tab-label"><NIcon size="18" color="#d03050"><RocketOutline /></NIcon><span>高级</span></div>
          </template>

          <NAlert type="info" :show-icon="true" style="margin-bottom: 16px">
            配置存储在后端 <b>xuanji_config</b> 表，跨设备同步、重启不丢失。换浏览器无需重新设置。
          </NAlert>

          <div class="setting-row">
            <div class="setting-label">
              <NText class="setting-label-text">导出配置</NText>
              <NText depth="3" class="setting-desc">下载当前设置为 JSON 文件</NText>
            </div>
            <div class="setting-field">
              <NButton @click="exportConfig">
                <template #icon><NIcon><DownloadOutline /></NIcon></template>
                导出
              </NButton>
            </div>
          </div>

          <div class="setting-row">
            <div class="setting-label">
              <NText class="setting-label-text">导入配置</NText>
              <NText depth="3" class="setting-desc">粘贴 JSON 覆盖当前设置</NText>
            </div>
            <div class="setting-field">
              <NButton @click="showImport = !showImport">
                <template #icon><NIcon><CloudUploadOutline /></NIcon></template>
                {{ showImport ? '收起' : '导入' }}
              </NButton>
            </div>
          </div>

          <div v-if="showImport" class="setting-row" style="display:block">
            <NInput v-model:value="importText" type="textarea" :rows="6" placeholder='粘贴导出的 JSON，例如 {"themeMode":"dark","brand":"blue",...}' />
            <NSpace style="margin-top: 8px">
              <NButton type="primary" :loading="importing" @click="importConfig">应用导入</NButton>
              <NButton @click="showImport = false; importText = ''">取消</NButton>
            </NSpace>
          </div>

          <div class="setting-row">
            <div class="setting-label">
              <NText class="setting-label-text" style="color: #e5484d">恢复出厂默认</NText>
              <NText depth="3" class="setting-desc">清空所有个性化设置，恢复默认</NText>
            </div>
            <div class="setting-field">
              <NPopconfirm @positive-click="resetAll">
                <template #trigger>
                  <NButton type="error" ghost>
                    <template #icon><NIcon><RefreshOutline /></NIcon></template>
                    重置
                  </NButton>
                </template>
                确认恢复出厂默认？所有个性化设置将被清除。
              </NPopconfirm>
            </div>
          </div>
        </NTabPane>

      </NTabs>
    </NCard>
  </div>
</template>

<style scoped>
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.setting-row {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.15));
}
.setting-row:last-child {
  border-bottom: none;
}
.setting-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.setting-label-text {
  font-weight: 600;
  font-size: 14px;
}
.setting-desc {
  font-size: 12px;
}
.setting-field {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.setting-hint {
  font-size: 12px;
}
.color-chip {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 3px;
  margin-right: 4px;
  vertical-align: -1px;
  border: 1px solid rgba(0, 0, 0, 0.15);
}
</style>
