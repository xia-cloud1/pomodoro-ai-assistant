import { request, BASE_URL } from '../utils/request.js'
import { t } from '../utils/i18n.js'

// AI 日报
export function dailyReport(date) {
	return request({ url: '/api/ai/daily-report', data: date ? { date } : {} })
}

// AI 周报（普通接口，拿统计数字）
export function weeklyReport(weekStart) {
	return request({ url: '/api/ai/weekly-report', data: weekStart ? { weekStart } : {} })
}

// AI 月报
export function monthlyReport(year, month) {
	return request({ url: '/api/ai/monthly-report', data: { year, month } })
}

// AI 周报 SSE 流式（H5 用 fetch 读流；后端 data 行是纯文本 chunk）
export function streamWeeklyReport(onChunk, onDone, onError) {
	// #ifdef H5
	const token = uni.getStorageSync('accessToken')
	fetch(BASE_URL + '/api/ai/weekly-report/stream', {
		method: 'GET',
		headers: { Authorization: 'Bearer ' + token, Accept: 'text/event-stream' }
	})
		.then(async (res) => {
			if (!res.ok || !res.body) {
				onError(new Error(t('networkError')))
				return
			}
			const reader = res.body.getReader()
			const decoder = new TextDecoder('utf-8')
			let buffer = ''
			for (;;) {
				const { done, value } = await reader.read()
				if (done) break
				buffer += decoder.decode(value, { stream: true })
				let idx
				while ((idx = buffer.indexOf('\n')) >= 0) {
					const line = buffer.slice(0, idx).replace(/\r$/, '')
					buffer = buffer.slice(idx + 1)
					if (line.startsWith('data:')) {
						const payload = line.slice(5).trim()
						if (payload) onChunk(payload)
					}
				}
			}
			onDone()
		})
		.catch((err) => onError(err))
	// #endif

	// #ifndef H5
	// App / 小程序没有 fetch 流式读取能力：退回普通接口，一次性拿到 AI 文本后走同样的回调
	weeklyReport()
		.then((d) => {
			if (d && d.aiAnalysis) onChunk(d.aiAnalysis)
			onDone()
		})
		.catch((err) => onError(err))
	// #endif
}

// 效率问答（RAG）
export function ask(question) {
	return request({ url: '/api/ai/ask', method: 'POST', data: { question } })
}

// AI 任务拆分
export function splitTask(title, description) {
	return request({ url: '/api/ai/split-task', method: 'POST', data: { title, description } })
}

// 黄金专注时段
export function goldenHours() {
	return request({ url: '/api/ai/golden-hours' })
}
