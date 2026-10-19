<template>
	<view class="page">
		<view class="card today">
			<view class="today-item">
				<text class="num">{{ today.completedCount || 0 }}</text>
				<text class="lbl">{{ t('completedPomodoros') }}</text>
			</view>
			<view class="today-item">
				<text class="num">{{ today.totalMinutes || 0 }}</text>
				<text class="lbl">{{ t('focusMinutes') }}</text>
			</view>
			<view class="today-item">
				<text class="num">{{ today.focusScore || 0 }}</text>
				<text class="lbl">{{ t('focusScore') }}</text>
			</view>
		</view>

		<!-- 月报表 -->
		<view class="card">
			<view class="sec-head">
				<text class="sec-title no-margin">{{ t('monthlyReport') }}</text>
				<view class="month-nav">
					<text class="nav-btn" @click="changeMonth(-1)">‹</text>
					<text class="month-label">{{ reportYear }}-{{ pad(reportMonth) }}</text>
					<text class="nav-btn" @click="changeMonth(1)">›</text>
				</view>
			</view>
			<view class="report-grid">
				<view class="rg-item">
					<text class="rg-num">{{ report.completedCount || 0 }}</text>
					<text class="rg-lbl">{{ t('completedPomodoros') }}</text>
				</view>
				<view class="rg-item">
					<text class="rg-num">{{ report.totalMinutes || 0 }}</text>
					<text class="rg-lbl">{{ t('focusMinutes') }}</text>
				</view>
				<view class="rg-item">
					<text class="rg-num">{{ report.activeDays || 0 }}</text>
					<text class="rg-lbl">{{ t('reportActiveDays') }}</text>
				</view>
				<view class="rg-item">
					<text class="rg-num">{{ report.avgFocusScore || 0 }}</text>
					<text class="rg-lbl">{{ t('focusScore') }}</text>
				</view>
			</view>
			<view class="report-sub">
				<text class="report-line">{{ t('reportCompletionRate') }}: {{ report.completionRate || 0 }}%</text>
				<text v-if="report.bestDay && report.bestDay.date" class="report-line">{{ t('reportBestDay') }}: {{ report.bestDay.date }} · {{ report.bestDay.count }}</text>
			</view>
			<view v-if="report.dailyBreakdown && report.dailyBreakdown.length" class="report-chart">
				<view v-for="d in report.dailyBreakdown" :key="d.date" class="rc-col">
					<view class="rc-bar" :style="{ height: reportBarHeight(d.completedCount) }"></view>
				</view>
			</view>
		</view>

		<view class="card">
			<text class="sec-title">{{ t('heatmapTitle') }}</text>
			<view class="heatmap">
				<view v-for="c in heatmap" :key="c.date" class="cell" :style="{ background: heatColor(c.count) }"></view>
			</view>
		</view>

		<view class="card">
			<text class="sec-title">{{ t('hourDistTitle') }}</text>
			<view v-for="h in hours" :key="h.hour" class="hour-row">
				<text class="hour">{{ t('hourSuffix', { h: h.hour }) }}</text>
				<view class="bar-bg">
					<view class="bar" :style="{ width: barWidth(h.count) }"></view>
				</view>
				<text class="cnt">{{ h.count }}</text>
			</view>
		</view>

		<!-- 番茄记录 -->
		<view class="card">
			<view class="sec-head">
				<text class="sec-title no-margin">{{ t('pomodoroHistory') }}</text>
				<text class="export-btn" @click="doExport">{{ t('exportCsv') }}</text>
			</view>
			<view v-for="r in history" :key="r.id" class="hist-row">
				<view class="hist-main">
					<text class="hist-title">{{ historyTitle(r) }}</text>
					<text class="hist-time">{{ formatTime(r.startTime) }}</text>
				</view>
				<view class="hist-right">
					<text class="hist-status" :style="{ color: statusColor(r.status) }">{{ statusName(r.status) }}</text>
					<text class="hist-dur">{{ durationText(r) }}</text>
				</view>
			</view>
			<view v-if="!history.length" class="hist-empty">{{ t('emptyHistory') }}</view>
			<view v-if="historyPage < historyPages" class="load-more" @click="loadMore">{{ t('loadMore') }}</view>
		</view>
	</view>
</template>

<script>
	import { getDailyStat, getHeatmap, getHourDistribution, getReport } from '../../api/stat.js'
	import { pagePomodoro } from '../../api/pomodoro.js'
	import { listTask } from '../../api/task.js'
	import { exportPomodoroCsv } from '../../api/export.js'

	export default {
		data() {
			return {
				today: {},
				heatmap: [],
				hours: [],
				maxHourCount: 1,
				report: {},
				reportYear: 0,
				reportMonth: 0,
				history: [],
				historyPage: 1,
				historyPages: 0,
				taskTitleMap: {}
			}
		},
		computed: {
			maxReportDay() {
				const list = (this.report && this.report.dailyBreakdown) || []
				return Math.max(1, ...list.map((d) => d.completedCount || 0))
			}
		},
		onShow() {
			if (!uni.getStorageSync('accessToken')) {
				uni.reLaunch({ url: '/pages/login/login' })
				return
			}
			if (!this.reportYear) {
				const now = new Date()
				this.reportYear = now.getFullYear()
				this.reportMonth = now.getMonth() + 1
			}
			this.load()
		},
		methods: {
			load() {
				getDailyStat().then((d) => { this.today = d || {} }).catch(() => {})
				getHeatmap().then((d) => { this.heatmap = d || [] }).catch(() => {})
				getHourDistribution().then((d) => {
					this.hours = (d || []).slice().sort((a, b) => a.hour - b.hour)
					this.maxHourCount = Math.max(1, ...this.hours.map((h) => h.count))
				}).catch(() => {})
				this.loadReport()
				this.loadTaskTitles()
				this.loadHistory(1)
			},
			loadReport() {
				getReport(this.reportYear, this.reportMonth)
					.then((d) => { this.report = d || {} })
					.catch(() => {})
			},
			changeMonth(delta) {
				const d = new Date(this.reportYear, this.reportMonth - 1 + delta, 1)
				this.reportYear = d.getFullYear()
				this.reportMonth = d.getMonth() + 1
				this.loadReport()
			},
			pad(n) {
				return n < 10 ? '0' + n : '' + n
			},
			reportBarHeight(c) {
				return Math.max(2, Math.round(((c || 0) / this.maxReportDay) * 80)) + 'rpx'
			},
			loadTaskTitles() {
				listTask({ pageNum: 1, pageSize: 100 })
					.then((d) => {
						const map = {}
						;(d.records || []).forEach((task) => { map[task.id] = task.title })
						this.taskTitleMap = map
					})
					.catch(() => {})
			},
			loadHistory(page) {
				pagePomodoro({ pageNum: page, pageSize: 10 })
					.then((d) => {
						this.historyPage = Number(d.pageNum) || page
						this.historyPages = Number(d.pages) || 0
						const records = d.records || []
						this.history = page === 1 ? records : this.history.concat(records)
					})
					.catch(() => {})
			},
			loadMore() {
				if (this.historyPage < this.historyPages) this.loadHistory(this.historyPage + 1)
			},
			historyTitle(r) {
				return (r.taskId && this.taskTitleMap[r.taskId]) ? this.taskTitleMap[r.taskId] : this.t('noTask')
			},
			formatTime(t) {
				if (!t) return ''
				return String(t).replace('T', ' ').slice(5, 16)
			},
			durationText(r) {
				if (r.status === 'RUNNING') return '--'
				const min = Math.round((r.duration || 0) / 60)
				return Math.max(1, min) + ' ' + this.t('minutesUnit')
			},
			statusName(s) {
				const keys = { RUNNING: 'statusRunning', COMPLETED: 'statusCompleted', INTERRUPTED: 'statusInterrupted' }
				return keys[s] ? this.t(keys[s]) : s
			},
			statusColor(s) {
				return { RUNNING: 'var(--primary)', COMPLETED: '#2fb344', INTERRUPTED: 'var(--danger)' }[s] || 'var(--text3)'
			},
			doExport() {
				exportPomodoroCsv()
					.then(() => { uni.showToast({ title: this.t('csvExported'), icon: 'none' }) })
					.catch(() => { uni.showToast({ title: this.t('exportFailed'), icon: 'none' }) })
			},
			heatColor(c) {
				if (!c || c === 0) return 'var(--fill)'
				if (c === 1) return '#c6e48b'
				if (c === 2) return '#7bc96f'
				if (c === 3) return '#40c463'
				return '#216e39'
			},
			barWidth(c) {
				return Math.round((c / this.maxHourCount) * 100) + '%'
			}
		}
	}
</script>

<style>
	.page {
		min-height: 100vh;
		background: var(--bg);
		padding: 30rpx;
	}
	.card {
		background: var(--card);
		border-radius: 20rpx;
		padding: 28rpx;
		margin-bottom: 24rpx;
	}
	.today {
		display: flex;
		justify-content: space-around;
	}
	.today-item {
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.num {
		font-size: 48rpx;
		font-weight: bold;
		color: var(--primary);
	}
	.lbl {
		font-size: 24rpx;
		color: var(--text3);
		margin-top: 8rpx;
	}
	.sec-title {
		font-size: 30rpx;
		font-weight: bold;
		color: var(--text);
		display: block;
		margin-bottom: 20rpx;
	}
	.heatmap {
		display: flex;
		flex-wrap: wrap;
		gap: 6rpx;
	}
	.cell {
		width: 22rpx;
		height: 22rpx;
		border-radius: 4rpx;
	}
	.hour-row {
		display: flex;
		align-items: center;
		margin-bottom: 12rpx;
	}
	.hour {
		width: 80rpx;
		font-size: 24rpx;
		color: var(--text2);
	}
	.bar-bg {
		flex: 1;
		height: 24rpx;
		background: var(--fill);
		border-radius: 12rpx;
		overflow: hidden;
	}
	.bar {
		height: 100%;
		background: var(--primary);
		border-radius: 12rpx;
	}
	.cnt {
		width: 60rpx;
		text-align: right;
		font-size: 24rpx;
		color: var(--text3);
	}
	.sec-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 20rpx;
	}
	.sec-title.no-margin {
		margin-bottom: 0;
	}
	.month-nav {
		display: flex;
		align-items: center;
	}
	.nav-btn {
		font-size: 36rpx;
		color: var(--primary);
		padding: 0 16rpx;
	}
	.month-label {
		font-size: 26rpx;
		color: var(--text2);
	}
	.report-grid {
		display: flex;
		justify-content: space-between;
	}
	.rg-item {
		display: flex;
		flex-direction: column;
		align-items: center;
		flex: 1;
	}
	.rg-num {
		font-size: 40rpx;
		font-weight: bold;
		color: var(--primary);
	}
	.rg-lbl {
		font-size: 22rpx;
		color: var(--text3);
		margin-top: 6rpx;
	}
	.report-sub {
		display: flex;
		flex-direction: column;
		margin-top: 20rpx;
	}
	.report-line {
		font-size: 24rpx;
		color: var(--text3);
		margin-top: 4rpx;
	}
	.report-chart {
		display: flex;
		align-items: flex-end;
		gap: 4rpx;
		height: 84rpx;
		margin-top: 20rpx;
	}
	.rc-col {
		flex: 1;
		display: flex;
		align-items: flex-end;
		height: 100%;
	}
	.rc-bar {
		width: 100%;
		background: var(--primary);
		border-radius: 3rpx;
	}
	.export-btn {
		font-size: 26rpx;
		color: var(--primary);
		border: 1rpx solid var(--primary);
		padding: 6rpx 20rpx;
		border-radius: 8rpx;
	}
	.hist-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 16rpx 0;
		border-bottom: 1rpx solid var(--border-light);
	}
	.hist-main {
		display: flex;
		flex-direction: column;
		flex: 1;
		overflow: hidden;
	}
	.hist-title {
		font-size: 28rpx;
		color: var(--text);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.hist-time {
		font-size: 22rpx;
		color: var(--text4);
		margin-top: 4rpx;
	}
	.hist-right {
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		margin-left: 20rpx;
	}
	.hist-status {
		font-size: 24rpx;
	}
	.hist-dur {
		font-size: 22rpx;
		color: var(--text3);
		margin-top: 4rpx;
	}
	.hist-empty {
		font-size: 26rpx;
		color: var(--text4);
		text-align: center;
		padding: 30rpx 0;
	}
	.load-more {
		font-size: 26rpx;
		color: var(--primary);
		text-align: center;
		padding-top: 20rpx;
	}
</style>
