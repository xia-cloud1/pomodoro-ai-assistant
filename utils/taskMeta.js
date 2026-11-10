// 任务的四象限 / 重复类型 / 状态的展示映射。
// 拆分组件后这些映射会被卡片、新建弹窗等多处用到，集中在这里，避免各组件各抄一份。
// 统一返回 i18n 的 key，由调用方用 t() 取词（i18n 是全局 mixin，页面与子组件都能用）。

export const REPEAT_NONE = 'NONE'
export const REPEAT_OPTIONS = ['NONE', 'DAILY', 'WEEKLY']
// 新建标签时按顺序循环取色
export const TAG_COLORS = ['#FF6600', '#0066FF', '#2fb344', '#6b5ce7', '#f0a020']

const QUAD_NAME_KEYS = { 1: 'quad1', 2: 'quad2', 3: 'quad3', 4: 'quad4' }
const QUAD_COLORS = { 1: 'var(--danger)', 2: 'var(--primary)', 3: '#f0a020', 4: 'var(--text3)' }
const REPEAT_NAME_KEYS = { NONE: 'repeatNone', DAILY: 'repeatDaily', WEEKLY: 'repeatWeekly' }
const STATUS_NAME_KEYS = { 0: 'status0', 1: 'status1', 2: 'status2', 3: 'status3' }

export function quadNameKey(quadrant) {
	return QUAD_NAME_KEYS[quadrant] || 'quad0'
}

export function quadColor(quadrant) {
	return QUAD_COLORS[quadrant] || 'var(--text3)'
}

export function repeatNameKey(type) {
	return REPEAT_NAME_KEYS[type] || ''
}

export function statusNameKey(status) {
	return STATUS_NAME_KEYS[status] || ''
}

// 是否为重复任务（重复任务才有打卡按钮与连续次数）
export function isRepeatTask(task) {
	return !!task && !!task.repeatType && task.repeatType !== REPEAT_NONE
}