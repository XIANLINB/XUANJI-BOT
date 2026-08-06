// 首启引导 API
import { get, post } from './http'

export const setupApi = {
  setupStatus: () => get('/setup/status'),
  setupPin: (body: { pin: string }) => post('/setup/pin', body),
  setupBot: (body: Record<string, string>) => post('/setup/bot', body),
  setupComplete: () => post('/setup/complete'),
  setupVerify: (body: { pin: string }) => post('/setup/verify', body)
}
