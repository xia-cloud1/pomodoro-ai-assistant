import { request } from '../utils/request.js'

// 查询打卡状态
export function checkinStatus() {
	return request({ url: '/api/checkin/status' })
}

// 今日打卡（断网时入队，联网自动重放；重复打卡服务端返回 409，同步时视为已生效丢弃）
export function checkin() {
	return request({ url: '/api/checkin', method: 'POST', queue: true })
}

// 补签历史日期（后端用 @RequestParam 接收，必须拼在 query 上而非 body）
export function makeupCheckin(date) {
	return request({ url: '/api/checkin/makeup?date=' + date, method: 'POST', queue: true })
}
