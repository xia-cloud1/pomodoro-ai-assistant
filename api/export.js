import { BASE_URL } from '../utils/request.js'
import { t } from '../utils/i18n.js'

function csvUrl(start, end) {
	const query = []
	if (start) query.push('start=' + start)
	if (end) query.push('end=' + end)
	return BASE_URL + '/api/export/pomodoro' + (query.length ? '?' + query.join('&') : '')
}

// 导出番茄记录 CSV（默认近 30 天）。H5 直接触发浏览器下载。
export function exportPomodoroCsv(start, end) {
	const token = uni.getStorageSync('accessToken')
	const url = csvUrl(start, end)

	// #ifdef H5
	return new Promise((resolve, reject) => {
		uni.request({
			url,
			method: 'GET',
			responseType: 'arraybuffer',
			header: { Authorization: 'Bearer ' + token },
			success: (res) => {
				if (res.statusCode !== 200) {
					reject(new Error(t('exportFailed')))
					return
				}
				const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
				const link = document.createElement('a')
				link.href = URL.createObjectURL(blob)
				link.download = 'pomodoro_' + new Date().toISOString().slice(0, 10).replace(/-/g, '') + '.csv'
				document.body.appendChild(link)
				link.click()
				document.body.removeChild(link)
				setTimeout(() => URL.revokeObjectURL(link.href), 1000)
				resolve()
			},
			fail: reject
		})
	})
	// #endif

	// #ifndef H5
	return new Promise((resolve, reject) => {
		uni.downloadFile({
			url,
			header: { Authorization: 'Bearer ' + token },
			success: (res) => {
				if (res.statusCode === 200) resolve(res.tempFilePath)
				else reject(new Error(t('exportFailed')))
			},
			fail: reject
		})
	})
	// #endif
}
