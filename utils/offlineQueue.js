// 写请求离线队列（存储层）
//
// 设计要点：
// 1. 只排队「可安全重放」的写接口：打卡、精力、周目标、任务状态/任务打卡。
//    任务创建/删除、番茄开始/完成不排队 —— 前者重放会重复创建，后者的时长由服务端
//    按 start_time 对账，延迟重放会把离线时长算进去，破坏防作弊语义。
// 2. key 带 userId：切换账号时不会把 A 的操作重放到 B 的账号上。
// 3. 同一请求 10 秒内重复入队会去重，避免断网时连点产生多条。
// 4. 队列上限 50 条、单条最长保留 3 天，防止本地存储无限增长。

const QUEUE_PREFIX = 'offline-queue:'

/** 队列上限 */
const MAX_ITEMS = 50

/** 单条最长保留时间：3 天 */
const MAX_AGE_MS = 3 * 24 * 60 * 60 * 1000

/** 相同请求去重窗口：10 秒 */
const DEDUPE_WINDOW_MS = 10 * 1000

export function queueStorageKey(userId) {
	return QUEUE_PREFIX + (userId || 'anon')
}

/** 读取队列，顺手清理过期项和脏数据 */
export function readQueue(userId) {
	if (!userId) return []
	try {
		const raw = uni.getStorageSync(queueStorageKey(userId))
		if (!raw) return []
		const list = JSON.parse(raw)
		if (!Array.isArray(list)) return []
		const now = Date.now()
		return list.filter((it) => it && it.url && it.method && now - (it.createdAt || 0) <= MAX_AGE_MS)
	} catch (e) {
		return []
	}
}

function writeQueue(userId, items) {
	if (!userId) return
	try {
		if (!items.length) {
			uni.removeStorageSync(queueStorageKey(userId))
		} else {
			uni.setStorageSync(queueStorageKey(userId), JSON.stringify(items))
		}
	} catch (e) {
		// 存储失败不影响主流程
	}
}

/**
 * 入队。返回 true 表示已保存（或已有等价的待同步操作）。
 */
export function enqueueWrite(userId, payload) {
	if (!userId || !payload || !payload.url) return false
	const items = readQueue(userId)
	const fingerprint = fingerprintOf(payload)
	const now = Date.now()
	const duplicate = items.some(
		(it) => fingerprintOf(it) === fingerprint && now - (it.createdAt || 0) <= DEDUPE_WINDOW_MS
	)
	if (duplicate) return true

	items.push({
		id: 'w_' + now + '_' + Math.random().toString(36).slice(2, 8),
		url: payload.url,
		method: (payload.method || 'POST').toUpperCase(),
		data: payload.data == null ? null : payload.data,
		header: payload.header || null,
		createdAt: now,
		attempts: 0
	})
	// 超出上限丢弃最旧的，保证新操作优先
	if (items.length > MAX_ITEMS) {
		items.splice(0, items.length - MAX_ITEMS)
	}
	writeQueue(userId, items)
	return true
}

/** 移除已同步/不可重放的项 */
export function removeQueueItem(userId, id) {
	writeQueue(userId, readQueue(userId).filter((it) => it.id !== id))
}

/**
 * 网络仍不可用：累加重试次数；超过 5 次视为放弃并移除。
 * @returns {number} 当前累计失败次数
 */
export function markQueueItemFailed(userId, id) {
	const items = readQueue(userId)
	const item = items.find((it) => it.id === id)
	if (!item) return 0
	item.attempts = (item.attempts || 0) + 1
	if (item.attempts >= 5) {
		writeQueue(userId, items.filter((it) => it.id !== id))
		return item.attempts
	}
	writeQueue(userId, items)
	return item.attempts
}

/** 当前用户的待同步数量 */
export function queueSize(userId) {
	return readQueue(userId).length
}

/** 清空指定用户的队列（注销账号时调用） */
export function clearQueue(userId) {
	if (!userId) return
	try {
		uni.removeStorageSync(queueStorageKey(userId))
	} catch (e) {
		// 忽略
	}
}

function fingerprintOf(item) {
	try {
		return (item.method || 'POST').toUpperCase() + ' ' + item.url + ' ' + JSON.stringify(item.data || null)
	} catch (e) {
		return (item.method || 'POST') + ' ' + item.url
	}
}
