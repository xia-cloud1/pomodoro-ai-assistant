// 请求封装：统一 baseURL、token 注入、后端业务码处理
// 后端约定：HTTP 恒为 200，业务状态在 body.code（200 成功 / 401 未登录 / 其他业务错误）
import { t } from './i18n.js'
import { apiCacheKey, readApiCache, writeApiCache, clearApiCache } from './apiCache.js'
import {
	enqueueWrite,
	readQueue,
	removeQueueItem,
	markQueueItemFailed,
	queueSize,
	clearQueue
} from './offlineQueue.js'

export const BASE_URL = 'http://localhost:8080' // H5 开发环境；App/小程序发布时改为实际后端地址

// 刷新 token 进行中的 Promise，并发 401 时共用一个，避免重复刷新
let refreshing = null

// 离线提示节流：断网时首页会并发多个 GET，避免连续弹多次 toast
let lastOfflineToastAt = 0

// 队列同步状态：防止并发触发重复重放
let flushing = false
let lastFlushAttemptAt = 0
let networkBound = false

/** 写请求已入队的返回值：页面可据此做乐观更新与差异化提示 */
export const QUEUED_RESULT = { queued: true, offline: true }

function requestError(message, code, network) {
	const err = new Error(message)
	err.code = code
	err.network = !!network
	return err
}

function currentUserId() {
	try {
		const user = JSON.parse(uni.getStorageSync('user') || '{}')
		return user.id || null
	} catch (e) {
		return null
	}
}

function notifyOfflineCache() {
	const now = Date.now()
	if (now - lastOfflineToastAt < 3000) return
	lastOfflineToastAt = now
	uni.showToast({ title: t('offlineCache'), icon: 'none' })
}

// 直接调 uni.request 而非 api/auth.js，避免 request ↔ auth 循环依赖
function doRefresh() {
	if (!refreshing) {
		refreshing = new Promise((resolve, reject) => {
			const rt = uni.getStorageSync('refreshToken')
			if (!rt) {
				reject(new Error('no refresh token'))
				return
			}
			uni.request({
				url: BASE_URL + '/api/auth/refresh',
				method: 'POST',
				data: { refreshToken: rt },
				header: { 'Content-Type': 'application/json' },
				success: (res) => {
					const body = res.data
					if (res.statusCode === 200 && body && body.code === 200 && body.data && body.data.accessToken) {
						uni.setStorageSync('accessToken', body.data.accessToken)
						if (body.data.refreshToken) uni.setStorageSync('refreshToken', body.data.refreshToken)
						resolve(body.data.accessToken)
					} else {
						reject(new Error('refresh failed'))
					}
				},
				fail: reject
			})
		}).finally(() => {
			refreshing = null
		})
	}
	return refreshing
}

// token 彻底失效：清空登录态并回登录页
function toLogin(message) {
	uni.removeStorageSync('accessToken')
	uni.removeStorageSync('refreshToken')
	uni.removeStorageSync('user')
	clearApiCache()
	const pages = getCurrentPages()
	const cur = pages.length ? pages[pages.length - 1] : null
	if (!cur || cur.route !== 'pages/login/login') {
		uni.showToast({ title: message || t('pleaseLogin'), icon: 'none' })
		uni.reLaunch({ url: '/pages/login/login' })
	} else {
		uni.showToast({ title: message || t('loginFailed'), icon: 'none' })
	}
}

export function request(options, _retried) {
	const method = (options.method || 'GET').toUpperCase()
	// GET 默认写入离线缓存；个别接口可传 { cache: false } 关闭
	const cacheable = method === 'GET' && options.cache !== false
	const cacheKey = cacheable ? apiCacheKey(currentUserId(), options.url, options.data) : ''
	return new Promise((resolve, reject) => {
		const token = uni.getStorageSync('accessToken')
		uni.request({
			url: BASE_URL + options.url,
			method,
			data: options.data || {},
			header: {
				'Content-Type': 'application/json',
				...(token ? { Authorization: 'Bearer ' + token } : {}),
				...(options.header || {})
			},
			success: (res) => {
				const body = res.data
				if (res.statusCode !== 200) {
					uni.showToast({ title: t('networkError'), icon: 'none' })
					reject(requestError(t('networkError'), res.statusCode))
					return
				}
				if (body && body.code === 200) {
					if (cacheable) writeApiCache(cacheKey, body.data)
					// 任一请求成功都说明网络已恢复，顺手尝试同步离线队列（内部有节流）
					if (!options._fromQueue) scheduleFlush()
					resolve(body.data)
					return
				}
				const code = body ? body.code : 500
				const message = body ? body.message : t('requestFailed')
				if (code === 401) {
					// 登录/注册/刷新自身的 401 是业务失败（如密码错误），不能去刷新重放
					const isAuthApi = (options.url || '').indexOf('/api/auth/') === 0
					// access token 过期 → 刷新一次并重放原请求；刷新失败或已重试过则回登录页
					if (!_retried && !isAuthApi) {
						doRefresh()
							.then(() => resolve(request(options, true)))
							.catch(() => {
								toLogin(message)
								reject(requestError(message, 401))
							})
						return
					}
					toLogin(message)
					reject(requestError(message, 401))
					return
				}
				uni.showToast({ title: message, icon: 'none' })
				reject(requestError(message, code))
			},
			fail: (err) => {
				// 写请求：网络不可用时入队，联网后按顺序重放。
				// 页面拿到 QUEUED_RESULT 后做乐观更新并提示「已离线保存」。
				if (options.queue && !options._fromQueue) {
					const userId = currentUserId()
					if (userId && enqueueWrite(userId, {
						url: options.url,
						method,
						data: options.data || {},
						header: options.header
					})) {
						resolve(QUEUED_RESULT)
						return
					}
				}
				// 网络不可用时回退到最近一次成功的 GET 结果（如刷新后离线恢复番茄状态）
				if (cacheable) {
					const cached = readApiCache(cacheKey, options.cacheTtl)
					if (cached !== null) {
						notifyOfflineCache()
						resolve(cached)
						return
					}
				}
				uni.showToast({ title: t('networkException'), icon: 'none' })
				reject(requestError(t('networkException'), -1, true))
			}
		})
	})
}

/**
 * 网络恢复时同步离线写队列。
 * 顺序重放，遇到网络错误立即停止（保留剩余项）；业务错误说明重放无意义或已生效，直接丢弃。
 * @returns {Promise<number>} 本次成功同步的条数
 */
export function flushOfflineQueue(options = {}) {
	const userId = currentUserId()
	if (flushing || !userId) return Promise.resolve(0)
	const items = readQueue(userId)
	if (!items.length) return Promise.resolve(0)

	flushing = true
	let synced = 0
	return (async () => {
		for (const item of items) {
			try {
				await request({
					url: item.url,
					method: item.method,
					data: item.data || {},
					header: item.header || undefined,
					cache: false,
					queue: false,
					_fromQueue: true
				})
				removeQueueItem(userId, item.id)
				synced++
			} catch (e) {
				if (e && e.network) {
					// 仍然断网：累加重试次数（超过 5 次自动丢弃），本轮停止
					markQueueItemFailed(userId, item.id)
					break
				}
				if (e && e.code === 401) {
					// 登录态失效：保留队列，等用户重新登录后再同步
					break
				}
				if (e && (e.code === 429 || e.code >= 500)) {
					// 限流 / 服务端临时故障：保留操作，下一轮再试
					markQueueItemFailed(userId, item.id)
					break
				}
				// 409 = 已生效（如重复打卡），400/404 = 不可重放，均丢弃避免死循环
				removeQueueItem(userId, item.id)
			}
		}
		return synced
	})().finally(() => {
		flushing = false
		lastFlushAttemptAt = Date.now()
		if (synced > 0 && !options.silent) {
			uni.showToast({ title: t('offlineSynced', { n: synced }), icon: 'none' })
		}
	})
}

/** 有请求成功或网络恢复时，节流触发一次静默同步 */
function scheduleFlush() {
	if (flushing) return
	const userId = currentUserId()
	if (!userId || !queueSize(userId)) return
	if (Date.now() - lastFlushAttemptAt < 15000) return
	lastFlushAttemptAt = Date.now()
	setTimeout(() => { flushOfflineQueue({ silent: true }) }, 0)
}

/** App 启动时调用：监听网络恢复，后续联网自动同步 */
export function initOfflineQueue() {
	if (networkBound) return
	networkBound = true
	if (typeof uni.onNetworkStatusChange === 'function') {
		uni.onNetworkStatusChange((res) => {
			if (res && res.isConnected) {
				// 网络恢复时给用户一个同步结果提示；启动/前台恢复的静默同步不打扰
				setTimeout(() => { flushOfflineQueue() }, 500)
			}
		})
	}
}

/** 注销账号时清理该用户的待同步队列（账号已不存在，重放只会 401） */
export function clearOfflineQueueForCurrentUser() {
	clearQueue(currentUserId())
}
