import { request } from '../utils/request.js'

// 某天日统计（date 缺省=今天）
export function getDailyStat(date) {
	return request({ url: '/api/daily-stat', data: date ? { date } : {} })
}

// 热力图（近 365 天）
export function getHeatmap() {
	return request({ url: '/api/stat/heatmap' })
}

// 时段分布（近 30 天）
export function getHourDistribution() {
	return request({ url: '/api/stat/hour-distribution' })
}

// 月/年报表（month 缺省=年报）
export function getReport(year, month) {
	return request({ url: '/api/stat/report', data: { year, month } })
}
