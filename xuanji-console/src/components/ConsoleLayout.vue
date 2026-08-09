<script setup lang="ts">
import { ref, computed, watch, onMounted, h, type Component } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  NLayout, NLayoutSider, NLayoutHeader, NLayoutContent,
  NMenu, NButton, NText, NIcon, NBadge, NTooltip, NGradientText
} from 'naive-ui'
import type { MenuOption } from 'naive-ui'
import {
  GridOutline, RocketOutline, PeopleOutline, PersonOutline,
  ChatbubbleOutline, FlashOutline, ServerOutline, DocumentTextOutline,
  SunnyOutline, MoonOutline, ColorPaletteOutline, PulseOutline, SettingsOutline,
  ExtensionPuzzleOutline, ShieldCheckmarkOutline, GitNetworkOutline, LogOutOutline,
  StorefrontOutline, KeyOutline, ArchiveOutline, TimeOutline,
  StatsChartOutline, TrashOutline, FolderOpenOutline, WarningOutline,
  ChatbubblesOutline, SparklesOutline, LibraryOutline, NewspaperOutline
} from '@vicons/ionicons5'
import { routes } from '../router'
import api from '../api'
import { brand } from '../theme'

const brandColor = brand.primary

const props = defineProps<{ isDark: boolean }>()
const emit = defineEmits<{ (e: 'toggle-theme'): void }>()

const router = useRouter()
const route = useRoute()

const iconMap: Record<string, Component> = {
  dashboard: GridOutline,
  chat: ChatbubbleOutline,
  'data-center': ServerOutline,
  stats: StatsChartOutline ?? GridOutline,
  cache: TrashOutline,
  files: FolderOpenOutline,
  'center-admin': RocketOutline,
  qqbot: RocketOutline,
  groups: PeopleOutline,
  friends: PersonOutline,
  'group-messages': ChatbubbleOutline,
  'c2c-messages': ChatbubbleOutline,
  'plugin-admin': ExtensionPuzzleOutline,
  'plugin-market': StorefrontOutline,
  'ops-admin': PulseOutline,
  events: FlashOutline,
  audit: ShieldCheckmarkOutline,
  database: ServerOutline,
  permission: ShieldCheckmarkOutline,
  security: KeyOutline,
  backup: ArchiveOutline,
  alert: WarningOutline,
  scheduler: TimeOutline,
  onebot: GitNetworkOutline,
  health: PulseOutline,
  settings: SettingsOutline,
  logs: DocumentTextOutline,
  tune: StatsChartOutline,
  monitor: ChatbubblesOutline,
  'ai-admin': SparklesOutline,
  'ai-chat': ChatbubblesOutline,
  'ai-personas': PersonOutline,
  'ai-usage': StatsChartOutline,
  'ai-tools': ExtensionPuzzleOutline,
  'ai-mcp': GitNetworkOutline,
  'ai-kb': LibraryOutline,
  'ai-audit': ShieldCheckmarkOutline,
  'ai-summary': NewspaperOutline,
  'ai-memory': ArchiveOutline,
  'ai-providers': ServerOutline
}

function renderIcon(icon: Component) {
  return () => h(NIcon, { size: 18 }, { default: () => h(icon) })
}

// ══════ 动态插件菜单：每个插件一个子菜单（页面名 = 插件 name）══════
const pluginMenuItems = ref<MenuOption[]>([])
const PLUGIN_PREFIX = 'plugin-page:'

async function loadPluginsMenu() {
  try {
    const list: any[] = await api.getPlugins()
    pluginMenuItems.value = (list || []).map((p) => ({
      label: (p.name || p.id) as string,
      key: PLUGIN_PREFIX + p.id
    }))
  } catch {
    pluginMenuItems.value = []
  }
}

const marketItem: MenuOption = {
  label: '插件市场',
  key: 'plugin-market',
  icon: renderIcon(StorefrontOutline)
}

// 注意：必须 computed——pluginMenuItems 更新后菜单要能重新构建（否则扫描出新插件菜单不刷新）
const menuOptions = computed<MenuOption[]>(() =>
  routes
    .filter((r) => r.name && r.meta?.title)
    .map((r) => {
      const item: MenuOption = {
        label: r.meta!.title as string,
        key: r.name as string,
        icon: renderIcon(iconMap[r.name as string] ?? GridOutline)
      }
      // 插件管理：子菜单 = 插件市场 + 每个插件一个页面（扫描入口已移到市场页）
      if (r.name === 'plugin-admin') {
        item.children = [marketItem, ...pluginMenuItems.value]
        return item
      }
      const children = r.children?.filter((c) => c.name && c.meta?.title)
      if (children?.length) {
        item.children = children.map((c) => ({
          label: c.meta!.title as string,
          key: c.name as string,
          icon: renderIcon(iconMap[c.name as string] ?? GridOutline)
        }))
      }
      return item
    })
)

// 分组父菜单 key 集合（点击父级 → 跳转该父级第一个子菜单）
const groupKeys = new Set(['chat-groups', 'chat-c2c', 'plugin-admin', 'center-admin', 'ops-admin', 'data-center', 'ai-admin'])

// 菜单默认全部收起（点击父级时才展开对应分组）
const expandedKeys = ref<string[]>([])

const currentTitle = computed(() => {
  if (route.name === 'plugin-page') {
    const p = pluginMenuItems.value.find((i) => i.key === PLUGIN_PREFIX + route.params.pluginId)
    return (p?.label as string) || '插件详情'
  }
  return (route.meta?.title as string) || '璇玑控制台'
})
const active = computed(() => {
  if (route.name === 'plugin-page') return PLUGIN_PREFIX + route.params.pluginId
  return (route.name as string) || 'dashboard'
})

function onSelect(key: string) {
  if (key.startsWith(PLUGIN_PREFIX)) {
    router.push('/plugins/p/' + key.slice(PLUGIN_PREFIX.length))
    return
  }
  if (groupKeys.has(key)) {
    // 父级菜单：跳转其第一个子菜单（NMenu 同时会自动展开该分组）
    const r = routes.find((x) => x.name === key)
    const first = r?.children?.find((c) => c.name && c.meta?.title)
    if (first?.name) {
      router.push({ name: first.name })
      return
    }
  }
  router.push({ name: key })
}

async function logout() {
  try {
    await api.authLogout()
  } catch {
    // 即使后端失败也强制回登录页，会话侧已尽力销毁
  }
  router.replace('/login')
}

const collapsed = ref(false)

onMounted(loadPluginsMenu)
// 每次导航刷新插件菜单（插件市场扫描/卸载后，菜单能同步新增/移除插件页面）
watch(() => route.fullPath, loadPluginsMenu)
// 进入子页面时自动展开其父级分组（初始默认全收起，仅导航后联动展开）
watch(
  () => route.name,
  (name) => {
    if (!name) return
    const parentKey = name === 'plugin-page' ? 'plugin-admin' : undefined
    const parent = parentKey
      ? routes.find((x) => x.name === parentKey)
      : routes.find((x) => x.children?.some((c) => c.name === name))
    if (parent?.name && !expandedKeys.value.includes(parent.name)) {
      expandedKeys.value = [...expandedKeys.value, parent.name]
    }
  },
  { immediate: true }
)
</script>

<template>
  <NLayout has-sider style="height: 100vh; overflow: hidden">
    <NLayoutSider
      bordered
      :collapsed-width="64"
      :width="232"
      :collapsed="collapsed"
      show-trigger
      @collapse="collapsed = true"
      @expand="collapsed = false"
    >
      <div class="sider">
        <div class="brand">
          <NIcon size="22" :color="brandColor">
            <ColorPaletteOutline />
          </NIcon>
          <NGradientText v-if="!collapsed" class="brand-text" :gradient="{ deg: 90, from: '#5b8def', to: '#722ed1' }" :size="17">
            璇玑控制台
          </NGradientText>
        </div>

        <div class="menu">
          <NMenu
            :value="active"
            :options="menuOptions"
            :collapsed="collapsed"
            :collapsed-width="64"
            :collapsed-icon-size="20"
            :expanded-keys="expandedKeys"
            @update:expanded-keys="(keys: string[]) => (expandedKeys = keys)"
            @update:value="onSelect"
          />
        </div>

        <div class="footer">
          <NButton
            tertiary
            block
            size="small"
            @click="logout"
          >
            <template #icon>
              <NIcon><LogOutOutline /></NIcon>
            </template>
            <span v-if="!collapsed">退出登录</span>
          </NButton>
          <div class="footer-status">
            <NTooltip trigger="hover">
              <template #trigger>
                <NBadge dot :type="'success'" :offset="[2, 2]" />
              </template>
              框架运行中
            </NTooltip>
            <span v-if="!collapsed" class="ver">v1.0</span>
          </div>
        </div>
      </div>
    </NLayoutSider>

    <NLayout style="height: 100%; display: flex; flex-direction: column; min-height: 0; overflow: hidden">
      <NLayoutHeader bordered class="topbar">
        <div class="topbar-inner">
          <div class="title-wrap">
            <NText strong class="title">{{ currentTitle }}</NText>
            <NText depth="3" class="subtitle">Xuanji Bot Framework</NText>
          </div>
          <NButton
            tertiary
            round
            @click="emit('toggle-theme')"
          >
            <template #icon>
              <NIcon>
                <component :is="props.isDark ? SunnyOutline : MoonOutline" />
              </NIcon>
            </template>
            {{ props.isDark ? '亮色' : '暗色' }}
          </NButton>
        </div>
      </NLayoutHeader>

      <NLayoutContent class="content" :native-scrollbar="true" style="flex: 1; min-height: 0; overflow: hidden; display: flex; flex-direction: column;">
        <router-view />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>

<style scoped>
.sider {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  font-weight: 800;
  font-size: 17px;
  letter-spacing: 1px;
  border-bottom: 1px solid var(--n-border-color);
}
.brand-text {
  background: linear-gradient(90deg, #5b5bd6, #2090e0);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
  padding: 10px 8px;
}
.menu::-webkit-scrollbar {
  display: none;
}
.footer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  border-top: 1px solid var(--n-border-color);
  font-size: 12px;
  color: var(--n-text-color-3);
}
.footer-status {
  display: flex;
  align-items: center;
  gap: 8px;
}
.topbar {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}
.topbar-inner {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px;
}
.title-wrap {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}
.title {
  font-size: 17px;
}
.subtitle {
  font-size: 11px;
  letter-spacing: 0.5px;
}
.content {
  flex: 1;
  min-height: 0;
  padding: 22px;
  box-sizing: border-box;
  background: var(--n-color);
}
</style>
