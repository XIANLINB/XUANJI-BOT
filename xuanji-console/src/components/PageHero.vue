<script setup lang="ts">
import { NCard, NText, NIcon, type Component } from 'naive-ui'
import { brand } from '../theme'

const props = defineProps<{
  title: string
  subtitle?: string
  icon?: Component
}>()

// 品牌色浅色底，图标用品牌深色，保证亮/暗主题都协调
const tint = brand.primary + '14'
const accent = brand.primary
</script>

<template>
  <NCard class="hero" :bordered="true">
    <div class="hero-inner">
      <div class="hero-left">
        <div v-if="icon" class="hero-icon">
          <NIcon size="20"><component :is="icon" /></NIcon>
        </div>
        <div class="hero-text">
          <NText strong class="hero-title">{{ title }}</NText>
          <div v-if="subtitle" class="hero-sub">{{ subtitle }}</div>
        </div>
      </div>
      <div class="hero-actions">
        <slot />
      </div>
    </div>
  </NCard>
</template>

<style scoped>
.hero {
  margin-bottom: 16px;
  background: var(--n-color);
  border: 1px solid var(--n-border-color);
}
.hero-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.hero-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.hero-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: v-bind(tint);
  color: v-bind(accent);
  flex-shrink: 0;
}
.hero-text {
  min-width: 0;
}
.hero-title {
  font-size: 19px;
  color: var(--n-text-color-1);
  line-height: 1.2;
}
.hero-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--n-text-color-3);
}
.hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
