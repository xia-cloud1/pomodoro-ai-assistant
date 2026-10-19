import { request } from '../utils/request.js'

// 分页查询任务
export function listTask(params) {
	return request({ url: '/api/task/page', method: 'GET', data: params })
}

// 创建任务
export function createTask(data) {
	return request({ url: '/api/task', method: 'POST', data })
}

// 更新任务
export function updateTask(id, data) {
	return request({ url: `/api/task/${id}`, method: 'PUT', data })
}

// 删除任务
export function deleteTask(id) {
	return request({ url: `/api/task/${id}`, method: 'DELETE' })
}

// 查询任务的标签
export function getTaskTags(id) {
	return request({ url: `/api/task/${id}/tags` })
}

// 设置任务的标签（整体替换，传空数组即清空）
export function bindTaskTags(id, tagIds) {
	return request({ url: `/api/task/${id}/tags`, method: 'PUT', data: { tagIds } })
}

// 重复任务打卡（DAILY 按天 / WEEKLY 按周，返回更新后的任务）
export function checkinTask(id) {
	return request({ url: `/api/task/${id}/checkin`, method: 'POST' })
}

// 查询任务的直接子任务
export function getTaskChildren(id) {
	return request({ url: `/api/task/${id}/children` })
}
