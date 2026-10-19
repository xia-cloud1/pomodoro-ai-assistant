import { request } from '../utils/request.js'

// 登录
export function login(username, password) {
	return request({ url: '/api/auth/login', method: 'POST', data: { username, password } })
}

// 注册
export function register(username, password) {
	return request({ url: '/api/auth/register', method: 'POST', data: { username, password } })
}

// 刷新 access token（该接口在拦截器白名单内，无需携带旧 token）
export function refreshToken(token) {
	return request({ url: '/api/auth/refresh', method: 'POST', data: { refreshToken: token } })
}
