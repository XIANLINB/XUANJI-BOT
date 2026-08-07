// 定时任务管理 API
import { get, post, put, del } from './http'

export interface SchedulerJob {
  id: number
  name: string
  jobType: string
  cron: string
  targetPlatform: string
  targetBot: string
  targetType: string
  targetId: string
  content: string
  enabled: boolean
  lastRun: number
  nextRun: number
  runCount: number
  failCount: number
  remark: string
  createdAt: number
}

export const schedulerApi = {
  // 任务 CRUD
  listJobs: () => get('/console/scheduler/jobs'),
  createJob: (body: Record<string, any>) => post('/console/scheduler/jobs', body),
  updateJob: (id: number, body: Record<string, any>) => put(`/console/scheduler/jobs/${id}`, body),
  deleteJob: (id: number) => del(`/console/scheduler/jobs/${id}`),
  // 启停 / 手动触发
  toggleJob: (id: number, enabled: boolean) => post(`/console/scheduler/jobs/${id}/toggle?enabled=${enabled}`),
  runJob: (id: number) => post(`/console/scheduler/jobs/${id}/run`),
  // 执行日志
  jobLogs: (id: number, limit = 50) => get(`/console/scheduler/jobs/${id}/logs`, { limit }),
  // 执行分析（批次5）
  schedulerStats: () => get('/console/scheduler/stats'),
  schedulerTrend: (days = 7) => get('/console/scheduler/trend', { days }),
  // cron 校验 + 下次执行预览
  cronPreview: (cron: string) => get('/console/scheduler/cron-preview', { cron })
}
