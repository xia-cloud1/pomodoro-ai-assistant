import { request } from '../utils/request.js'

// 成就列表
export function listAchievement() {
	return request({ url: '/api/achievement' })
}
