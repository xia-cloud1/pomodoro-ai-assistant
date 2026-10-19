import { request } from '../utils/request.js'

// 标签列表
export function listTag() {
	return request({ url: '/api/tag' })
}

// 创建标签（name 必填，color 可选，如 '#FF6600'）
export function createTag(name, color) {
	return request({ url: '/api/tag', method: 'POST', data: { name, color } })
}

// 更新标签
export function updateTag(id, name, color) {
	return request({ url: `/api/tag/${id}`, method: 'PUT', data: { name, color } })
}

// 删除标签
export function deleteTag(id) {
	return request({ url: `/api/tag/${id}`, method: 'DELETE' })
}
