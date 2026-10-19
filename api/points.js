import { request } from '../utils/request.js'

// 积分摘要
export function pointsSummary() {
	return request({ url: '/api/points/summary' })
}

// 积分流水
export function pointsLog(params) {
	return request({ url: '/api/points/log', data: params })
}
