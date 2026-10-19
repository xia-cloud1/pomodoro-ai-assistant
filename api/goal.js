import { request } from '../utils/request.js'

// 设置/更新周目标（weekStart 缺省=本周，后端归一化到周一）
export function setGoal(targetPomodoros, weekStart) {
	return request({
		url: '/api/goal',
		method: 'PUT',
		data: weekStart ? { targetPomodoros, weekStart } : { targetPomodoros }
	})
}

// 查询周目标状态（weekStart 缺省=本周）
export function getGoal(weekStart) {
	return request({ url: '/api/goal', data: weekStart ? { weekStart } : {} })
}
