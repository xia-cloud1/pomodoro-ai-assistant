import { request, BASE_URL } from '../utils/request.js'
import { t } from '../utils/i18n.js'

// 注销账号（密码二次确认，删除全部数据）
export function deleteAccount(password) {
	return request({ url: '/api/user/account/delete', method: 'POST', data: { password } })
}

// 导出个人全部数据（JSON 附件下载）
export function exportPersonalData() {
	const token = uni.getStorageSync('accessToken')
	return new Promise((resolve, reject) => {
		uni.downloadFile({
			url: BASE_URL + '/api/export/personal',
			header: { Authorization: 'Bearer ' + token },
			success: (res) => {
				if (res.statusCode === 200) resolve(res.tempFilePath)
				else reject(new Error(t('exportFailed')))
			},
			fail: (err) => reject(err)
		})
	})
}