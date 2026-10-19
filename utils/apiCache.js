// API 离线缓存：只缓存成功的 GET 响应，网络失败时回退到最近一次成功结果。
// 设计要点：
// 1. key 带 userId —— 同一台设备切换账号时不会读到上一个账号的数据；
// 2. 参数排序后再拼接 —— {a:1,b:2} 与 {b:2,a:1} 命中同一份缓存；
// 3. 写缓存失败（如存储空间不足）静默忽略，缓存永远不影响主流程。

const CACHE_PREFIX = 'api-cache:'

/** 默认离线可用时长：7 天 */
export const DEFAULT_CACHE_TTL = 7 * 24 * 60 * 60 * 1000

/** 生成缓存 key：用户 + 接口 + 参数指纹 */
export function apiCacheKey(userId, url, data) {
	return CACHE_PREFIX + (userId || 'anon') + ':' + url + '?' + stableStringify(data)
}

/**
 * 读取缓存，超过 ttl 视为失效并顺手清理。
 * @returns 缓存数据；不存在/已过期/解析失败返回 null
 */
export function readApiCache(key, ttl) {
	try {
		const raw = uni.getStorageSync(key)
		if (!raw) return null
		const entry = JSON.parse(raw)
		if (!entry || typeof entry.t !== 'number') return null
		if (Date.now() - entry.t > (ttl == null ? DEFAULT_CACHE_TTL : ttl)) {
			uni.removeStorageSync(key)
			return null
		}
		return entry.d
	} catch (e) {
		return null
	}
}

/** 写入缓存；存储异常时静默失败 */
export function writeApiCache(key, data) {
	try {
		uni.setStorageSync(key, JSON.stringify({ t: Date.now(), d: data }))
	} catch (e) {
		// 忽略：离线缓存是增强能力，不影响正常请求
	}
}

/** 清空全部 API 缓存（退出登录 / 注销账号时调用） */
export function clearApiCache() {
	try {
		const info = uni.getStorageInfoSync()
		;(info.keys || []).forEach((key) => {
			if (key.indexOf(CACHE_PREFIX) === 0) {
				uni.removeStorageSync(key)
			}
		})
	} catch (e) {
		// 忽略
	}
}

/** 稳定序列化：对象 key 排序，避免参数顺序不同导致缓存不命中 */
function stableStringify(value) {
	if (value == null) return ''
	if (typeof value !== 'object') return String(value)
	if (Array.isArray(value)) return '[' + value.map(stableStringify).join(',') + ']'
	return '{' + Object.keys(value)
		.sort()
		.map((k) => k + ':' + stableStringify(value[k]))
		.join(',') + '}'
}
