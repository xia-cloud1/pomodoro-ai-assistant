import { BASE_URL } from '../utils/request.js'
import { t } from '../utils/i18n.js'

// 生成分享海报（PNG 二进制 → base64 预览）
export function generatePoster() {
	return new Promise((resolve, reject) => {
		uni.request({
			url: BASE_URL + '/api/share/poster',
			method: 'GET',
			responseType: 'arraybuffer',
			header: { Authorization: 'Bearer ' + uni.getStorageSync('accessToken') },
			success: (res) => {
				if (res.statusCode === 200 && res.data) {
					resolve('data:image/png;base64,' + uni.arrayBufferToBase64(res.data))
				} else {
					uni.showToast({ title: t('posterFailed'), icon: 'none' })
					reject(new Error(t('generateFailed')))
				}
			},
			fail: (err) => {
				uni.showToast({ title: t('networkError'), icon: 'none' })
				reject(err)
			}
		})
	})
}
