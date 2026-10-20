import { request } from '../utils/request.js'

// 保存精力自评（energyLevel 1-5 必填；按天 upsert，可安全离线重放）
export function saveEnergy(data) {
	return request({ url: '/api/energy', method: 'POST', data, queue: true })
}

// 查询某天精力自评（date 缺省=今天；不能传 undefined，否则会拼成 date=undefined 导致后端解析失败）
export function getEnergy(date) {
	return request({ url: '/api/energy', data: date ? { date } : {} })
}
