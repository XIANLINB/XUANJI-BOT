<script setup lang="ts">
import { ref, computed, h, type Component } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  NLayout, NLayoutSider, NLayoutHeader, NLayoutContent,
  NMenu, NButton, NText, NIcon, NBadge, NTooltip
} from 'naive-ui'
import type { MenuOption } from 'naive-ui'
import {
  GridOutline, RocketOutline, PeopleOutline, PersonOutline,
  ChatbubbleOutline, FlashOutline, ServerOutline, DocumentTextOutline,
  SunnyOutline, MoonOutline, ColorPaletteOutline, PulseOutline, SettingsOutline,
  ExtensionPuzzleOutline, ShieldCheckmarkOutline, GitNetworkOutline
} from '@vicons/ionicons5'
import { routes } from '../router'
import { brand } from '../theme'

const brandColor = brand.primary

const props = defineProps<{ isDark: boolean }>()
const emit = defineEmits<{ (e: 'toggle-theme'): void }>()

const router = useRouter()
const route = useRoute()

const iconMap: Record<string, Component> = {
  dashboard: GridOutline,
  bots: RocketOutline,
  groups: PeopleOutline,
  friends: PersonOutline,
  messages: ChatbubbleOutline,
  events: FlashOutline,
  database: ServerOutline,
  plugins: ExtensionPuzzleOutline,
  permission: ShieldCheckmarkOutline,
  onebot: GitNetworkOutline,
  health: PulseOutline,
  settings: SettingsOutline,
  logs: DocumentTextOutline
}

function renderIcon(icon: Component) {
  return () => h(NIcon, { size: 18 }, { default: () => h(icon) })
}

const menuOptions: MenuOption[] = routes
  .filter((r) => r.name && r.meta?.title)
  .map((r) => ({
    label: r.meta!.title as string,
    key: r.name as string,
    icon: renderIcon(iconMap[r.name as string] ?? GridOutline)
  }))

const currentTitle = computed(() => (route.meta?.title as string) || '璇玑控制台')
const active = computed(() => (route.name as string) || 'dashboard')

function onSelect(key: string) {
  router.push({ name: key })
}

const collapsed = ref(false)
</script>

<template>
  <NLayout has-sider style="height: 100vh">
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
          <span v-if="!collapsed" class="brand-text">璇玑控制台</span>
        </div>

        <div class="menu">
          <NMenu
            :value="active"
            :options="menuOptions"
            :collapsed="collapsed"
            :collapsed-width="64"
            :collapsed-icon-size="20"
            @update:value="onSelect"
          />
        </div>

        <div class="footer">
          <NTooltip trigger="hover">
            <template #trigger>
              <NBadge dot :type="'success'" :offset="[2, 2]" />
            </template>
            框架运行中
          </NTooltip>
          <span v-if="!collapsed" class="ver">璇玑 v3.0</span>
        </div>
      </div>
    </NLayoutSider>

    <NLayout>
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

      <NLayoutContent class="content" :native-scrollbar="false">
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
  overflow: auto;
  padding: 10px 8px;
}
.footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  border-top: 1px solid var(--n-border-color);
  font-size: 12px;
  color: var(--n-text-color-3);
}
.topbar {
  height: 60px;
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
  padding: 22px;
  box-sizing: border-box;
  background: var(--n-color);
}
</style>
