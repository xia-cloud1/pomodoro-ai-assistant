import { request } from '../utils/request.js'

// 排行榜
export function getRank(limit) {
	return request({ url: '/api/rank', data: { limit: limit || 20 } })
}
