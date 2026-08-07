<script setup lang="ts">
// CommonChart — ECharts 公共封装（批次8）
// 统一管理 init / setOption / resize / dispose / option 深监听 / 窗口 resize，
// 各页面只需提供 option 数据；高频刷新（如 QPS 曲线 5s 轮询）直接改 option 即可。
import { ref, onMounted, onUnmounted, watch, shallowRef } from 'vue'
import * as echarts from 'echarts'

const props = withDefaults(
  defineProps<{
    /** ECharts option（深监听，变化即重绘） */
    option: Record<string, any>
    /** 容器高度，默认 260px */
    height?: string
    /** setOption 的 notMerge（默认 true：整体替换） */
    notMerge?: boolean
    /** 是否开启动画（高频刷新建议 false） */
    animation?: boolean
  }>(),
  { height: '260px', notMerge: true, animation: true }
)

const el = ref<HTMLDivElement | null>(null)
const chart = shallowRef<echarts.ECharts | null>(null)

function resize() {
  chart.value?.resize()
}

onMounted(() => {
  if (!el.value) return
  chart.value = echarts.init(el.value)
  chart.value.setOption({ animation: props.animation, ...props.option }, props.notMerge)
  window.addEventListener('resize', resize)
})

watch(
  () => props.option,
  (opt) => {
    chart.value?.setOption({ animation: props.animation, ...opt }, props.notMerge)
  },
  { deep: true }
)

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  chart.value?.dispose()
  chart.value = null
})

defineExpose({ chart, resize })
</script>

<template>
  <div ref="el" :style="{ height, width: '100%' }"></div>
</template>
