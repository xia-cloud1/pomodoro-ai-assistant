// 请求封装：统一 baseURL、token 注入、后端业务码处理
// 后端约定：HTTP 恒为 200，业务状态在 body.code（200 成功 / 401 未登录 / 其他业务错误）
import { t } from './i18n.js'

export const BASE_URL = 'http://localhost:8080' // H5 开发环境；App/小程序发布时改为实际后端地址

// 刷新 token 进行中的 Promise，并发 401 时共用一个，避免重复刷新
let refreshing = null

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
	return new Promise((resolve, reject) => {
		const token = uni.getStorageSync('accessToken')
		uni.request({
			url: BASE_URL + options.url,
			method: options.method || 'GET',
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
					reject(new Error(t('networkError')))
					return
				}
				if (body && body.code === 200) {
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
								reject(new Error(message))
							})
						return
					}
					toLogin(message)
					reject(new Error(message))
					return
				}
				uni.showToast({ title: message, icon: 'none' })
				reject(new Error(message))
			},
			fail: (err) => {
				uni.showToast({ title: t('networkException'), icon: 'none' })
				reject(err)
			}
		})
	})
}
