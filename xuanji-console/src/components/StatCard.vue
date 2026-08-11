<script setup lang="ts">
import type { Component } from 'vue'
import { NCard, NIcon, NText, NNumberAnimation } from 'naive-ui'

interface Props {
  /** 图标组件（如 CubeOutline）。 */
  icon: Component
  /** 主色（hex），同时作为图标底色 + 数值色。 */
  color: string
  /** 数字。可传字符串（如 '100%'）或数字（自动 NNumberAnimation 动画）。 */
  value: number | string
  /** 底部标签。 */
  label: string
  /** 副标题（位于 value 与 label 之间的小字，如 "正常 12 · 已退出 3"）。 */
  sub?: string
  /** 是否动画数字（false直接显示）。 */
  animate?: boolean
  /** 数字动画时长（ms）。 */
  duration?: number
}

const props = withDefaults(defineProps<Props>(), { animate: true, duration: 900, sub: '' })

const displayValue = String(props.value)
</script>

<template>
  <NCard hoverable class="stat-card" :content-style="{ padding: '12px 14px' }">
    <div class="stat-top">
      <div class="stat-icon" :style="{ background: props.color + '1a', color: props.color }">
        <NIcon size="18"><component :is="props.icon" /></NIcon>
      </div>
      <div class="stat-value" :style="{ color: props.color }">
        <NNumberAnimation
          v-if="props.animate && typeof props.value === 'number'"
          :from="0"
          :to="Number(props.value) || 0"
          :duration="props.duration"
        />
        <template v-else>{{ displayValue }}</template>
      </div>
    </div>
    <NText v-if="props.sub" depth="3" class="stat-sub">{{ props.sub }}</NText>
    <NText depth="3" class="stat-label">{{ props.label }}</NText>
  </NCard>
</template>

<style scoped>
.stat-card { height: 100%; }
.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.stat-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.stat-sub {
  display: block;
  margin-top: 6px;
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  color: var(--n-text-color-3);
}
.stat-label {
  font-size: 12px;
}
</style>