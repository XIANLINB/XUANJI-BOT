<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { NSelect, NInputNumber, NCheckbox, NCheckboxGroup, NSpace, NTag } from 'naive-ui'

const props = defineProps<{ cron: string }>()
const emit = defineEmits<{ 'update:cron': [v: string] }>()

// ═══════════ 周期选项 ═══════════
type Frequency = 'every-min' | 'every-hour' | 'every-day' | 'every-week' | 'every-month' | 'every-n-min' | 'every-n-hour' | 'custom'
const FREQ_OPTIONS = [
  { label: '每分钟', value: 'every-min' },
  { label: '每小时', value: 'every-hour' },
  { label: '每天', value: 'every-day' },
  { label: '每周', value: 'every-week' },
  { label: '每月', value: 'every-month' },
  { label: '自定义 cron', value: 'custom' }
]

const HOUR_OPTIONS = Array.from({ length: 24 }, (_, i) => ({
  label: String(i).padStart(2, '0'),
  value: i
}))
const MINUTE_OPTIONS = Array.from({ length: 60 }, (_, i) => ({
  label: String(i).padStart(2, '0'),
  value: i
}))
const DAY_OPTIONS = Array.from({ length: 31 }, (_, i) => ({
  label: String(i + 1),
  value: i + 1
}))

// 周一~周日（国内习惯：周一在最前，周日在最后）
const WEEKDAY_OPTIONS = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 0 }
]

// ═══════════ 内部状态 ═══════════
const freq = ref<Frequency>('every-day')
const hour = ref(9)
const minute = ref(0)
const weekdays = ref<number[]>([1])
const monthDay = ref(1)
const nMin = ref(5)
const nHour = ref(2)
const customCron = ref(props.cron || '0 9 * * *')

/** 解析已有 cron（粗略推断 frequency），编辑既有任务时不丢上下文 */
function parseCron(c: string) {
  const parts = c.trim().split(/\s+/)
  if (parts.length !== 5) return
  const [mm, hh, dd, mo, ww] = parts
  customCron.value = c

  // 含 , - / 都视为自定义
  if ([mm, hh, dd, mo, ww].some(p => p.includes(',') || p.includes('-') || p.includes('/'))) {
    freq.value = 'custom'
    return
  }
  if (mm === '*' && hh === '*' && dd === '*' && mo === '*' && ww === '*') {
    freq.value = 'every-min'
    return
  }
  if (mm !== '*' && hh === '*' && dd === '*' && mo === '*' && ww === '*') {
    freq.value = 'every-hour'
    minute.value = Number(mm)
    return
  }
  if (mm !== '*' && hh !== '*' && dd === '*' && mo === '*' && ww === '*') {
    freq.value = 'every-day'
    minute.value = Number(mm)
    hour.value = Number(hh)
    return
  }
  if (mm !== '*' && hh !== '*' && dd === '*' && mo === '*' && /^[0-7]+$/.test(ww)) {
    freq.value = 'every-week'
    minute.value = Number(mm)
    hour.value = Number(hh)
    weekdays.value = ww.split('').map(Number).filter(n => n >= 0 && n <= 7)
    return
  }
  if (mm !== '*' && hh !== '*' && dd !== '*' && mo === '*' && ww === '*') {
    freq.value = 'every-month'
    minute.value = Number(mm)
    hour.value = Number(hh)
    monthDay.value = Number(dd)
    return
  }
  freq.value = 'custom'
}

parseCron(props.cron)

// ═══════════ 生成 cron ═══════════
const computedCron = computed(() => {
  switch (freq.value) {
    case 'every-min': return '* * * * *'
    case 'every-hour': return `${minute.value} * * * *`
    case 'every-day': return `${minute.value} ${hour.value} * * *`
    case 'every-week': {
      const dow = weekdays.value.length
        ? [...weekdays.value].sort((a, b) => a - b).join(',')
        : '1'
      return `${minute.value} ${hour.value} * * ${dow}`
    }
    case 'every-month':
      return `${minute.value} ${hour.value} ${monthDay.value} * *`
    case 'every-n-min':
      return `*/${Math.max(1, nMin.value)} * * * *`
    case 'every-n-hour':
      return `0 */${Math.max(1, nHour.value)} * * *`
    case 'custom':
    default:
      return customCron.value || '* * * * *'
  }
})

watch(computedCron, (v) => emit('update:cron', v))

// 人类可读描述
const cronDesc = computed(() => {
  switch (freq.value) {
    case 'every-min': return '每分钟执行'
    case 'every-hour': return `每小时 ${pad(minute.value)} 分执行`
    case 'every-day': return `每天 ${pad(hour.value)}:${pad(minute.value)} 执行`
    case 'every-week': {
      const labels = weekdays.value.length
        ? weekdays.value.map(w => WEEKDAY_OPTIONS.find(o => o.value === w)?.label ?? '').join('、')
        : '周一'
      return `每${labels} ${pad(hour.value)}:${pad(minute.value)} 执行`
    }
    case 'every-month': return `每月 ${monthDay.value} 日 ${pad(hour.value)}:${pad(minute.value)} 执行`
    case 'every-n-min': return `每 ${nMin.value} 分钟执行一次`
    case 'every-n-hour': return `每 ${nHour.value} 小时整点执行一次`
    case 'custom': return '自定义 cron'
  }
  return ''
})

function pad(n: number) { return String(n).padStart(2, '0') }
</script>

<template>
  <div class="cron-builder">
    <div class="cb-row">
      <span class="cb-k">重复</span>
      <NSelect
        v-model:value="freq"
        :options="FREQ_OPTIONS"
        size="small"
        style="width: 140px"
      />
    </div>

    <template v-if="['every-day', 'every-week', 'every-month'].includes(freq)">
      <div class="cb-row">
        <span class="cb-k">时</span>
        <NSelect
          v-model:value="hour"
          :options="HOUR_OPTIONS"
          size="small"
          style="width: 90px"
        />
        <span class="cb-k">分</span>
        <NSelect
          v-model:value="minute"
          :options="MINUTE_OPTIONS"
          size="small"
          style="width: 90px"
        />
      </div>
    </template>

    <template v-if="freq === 'every-hour'">
      <div class="cb-row">
        <span class="cb-k">分</span>
        <NSelect
          v-model:value="minute"
          :options="MINUTE_OPTIONS"
          size="small"
          style="width: 90px"
        />
      </div>
    </template>

    <template v-if="freq === 'every-week'">
      <div class="cb-row">
        <span class="cb-k">选择周几</span>
        <NCheckboxGroup v-model:value="weekdays">
          <NSpace :size="2">
            <NCheckbox v-for="w in WEEKDAY_OPTIONS" :key="w.value" :value="w.value">{{ w.label }}</NCheckbox>
          </NSpace>
        </NCheckboxGroup>
      </div>
    </template>

    <template v-if="freq === 'every-month'">
      <div class="cb-row">
        <span class="cb-k">每月第几天</span>
        <NSelect
          v-model:value="monthDay"
          :options="DAY_OPTIONS"
          size="small"
          style="width: 100px"
        />
      </div>
    </template>

    <template v-if="freq === 'every-n-min'">
      <div class="cb-row">
        <span class="cb-k">每</span>
        <NInputNumber
          v-model:value="nMin"
          :min="1"
          :max="59"
          size="small"
          style="width: 100px"
        />
        <span class="cb-k">分钟</span>
      </div>
    </template>

    <template v-if="freq === 'every-n-hour'">
      <div class="cb-row">
        <span class="cb-k">每</span>
        <NInputNumber
          v-model:value="nHour"
          :min="1"
          :max="23"
          size="small"
          style="width: 100px"
        />
        <span class="cb-k">小时整点</span>
      </div>
    </template>

    <template v-if="freq === 'custom'">
      <div class="cb-row">
        <span class="cb-k">cron</span>
        <input
          v-model="customCron"
          class="custom-input"
          placeholder="分 时 日 月 周"
          spellcheck="false"
          @input="emit('update:cron', ($event.target as HTMLInputElement).value)"
        />
      </div>
    </template>

    <div class="cb-preview">
      <NTag :bordered="false" size="small" type="info">{{ cronDesc }}</NTag>
      <code class="cb-cron">{{ computedCron }}</code>
    </div>
  </div>
</template>

<style scoped>
.cron-builder {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.cb-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.cb-k {
  font-size: 13px;
  color: var(--n-text-color-2);
  min-width: 70px;
  flex-shrink: 0;
}
.cb-preview {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--n-color-2);
  border-radius: 8px;
  font-size: 13px;
}
.cb-cron {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  background: var(--n-color-1);
  padding: 3px 8px;
  border-radius: 4px;
  color: var(--n-text-color-2);
  flex: 1;
  text-align: right;
}
.custom-input {
  flex: 1;
  border: 1px solid var(--n-border-color);
  border-radius: 4px;
  padding: 4px 10px;
  font-size: 13px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  background: transparent;
  color: var(--n-text-color-1);
  outline: none;
}
.custom-input:focus { border-color: var(--n-primary-color); }
</style>