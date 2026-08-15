<script setup lang="ts">
import { NEmpty } from 'naive-ui'

// 全局统一空态：列表/表格无数据时复用，保证风格一致。
withDefaults(defineProps<{ description?: string }>(), { description: '暂无数据' })
</script>

<template>
  <div class="empty-state">
    <NEmpty :description="description" size="small">
      <template #icon>
        <slot name="icon" />
      </template>
    </NEmpty>
  </div>
</template>

<style scoped>
.empty-state {
  padding: 48px 0;
  display: flex;
  justify-content: center;
}
</style>
