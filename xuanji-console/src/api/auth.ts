// 控制台登录鉴权 API（对应后端 /xuanji/api/v1/auth/**）
import { get, post } from './http'

export const authApi = {
  // 用 6 位访问口令登录，成功后后端下发 HttpOnly 会话 cookie
  authLogin: (body: { pin: string }) => post('/auth/login', body),
  // 销毁会话并清除 cookie
  authLogout: () => post('/auth/logout'),
  // 返回当前会话是否已登录（供路由守卫判断），无需已登录即可访问
  authMe: () => get('/auth/me')
}
