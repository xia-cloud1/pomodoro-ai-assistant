import { request } from '../utils/request.js'

// 查询打卡状态
export function checkinStatus() {
	return request({ url: '/api/checkin/status' })
}

// 今日打卡
export function checkin() {
	return request({ url: '/api/checkin', method: 'POST' })
}

// 补签历史日期（后端用 @RequestParam 接收，必须拼在 query 上而非 body）
export function makeupCheckin(date) {
	return request({ url: '/api/checkin/makeup?date=' + date, method: 'POST' })
}
