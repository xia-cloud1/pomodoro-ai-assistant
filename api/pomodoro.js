import { request } from '../utils/request.js'

// 开始番茄钟
export function startPomodoro(data) {
	return request({ url: '/api/pomodoro/start', method: 'POST', data: data || {} })
}

// 完成番茄钟
export function completePomodoro(id) {
	return request({ url: `/api/pomodoro/${id}/complete`, method: 'POST' })
}

// 中断番茄钟
export function interruptPomodoro(id, reason) {
	return request({ url: `/api/pomodoro/${id}/interrupt`, method: 'POST', data: { interruptReason: reason } })
}

// 分页查询番茄记录
export function pagePomodoro(params) {
	return request({ url: '/api/pomodoro/page', method: 'GET', data: params })
}
