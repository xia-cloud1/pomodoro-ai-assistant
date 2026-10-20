<template>
	<view class="page">
		<view class="header">
			<text class="hello">{{ user.username || t('user') }}</text>
			<text class="logout" @click="logout">{{ t('logout') }}</text>
		</view>
		<view class="level">Lv.{{ user.level || 0 }} · {{ user.points || 0 }} {{ t('pointsUnit') }}</view>

		<view class="timer">
			<text class="time">{{ displayTime }}</text>
			<text class="status">{{ statusText }}</text>
			<text v-if="running && selectedTaskId" class="running-task">{{ selectedTaskName }}</text>
		</view>

		<!-- 关联任务（仅未开始时可选） -->
		<view v-if="!running" class="task-pick" @click="showTaskPick = true">
			<text class="tp-label">{{ t('linkedTask') }}</text>
			<text class="tp-value">{{ selectedTaskName }}</text>
			<text class="tp-arrow">›</text>
		</view>

		<view class="actions" :class="{ 'actions-running': running }">
			<button v-if="!running" class="btn start" @click="start">{{ t('startFocus') }}</button>
			<template v-else>
				<button class="btn complete" @click="complete">{{ t('complete') }}</button>
				<button class="btn interrupt" @click="interrupt">{{ t('interrupt') }}</button>
			</template>
		</view>

		<!-- 本周目标 -->
		<view class="goal-card">
			<view class="goal-head">
				<text class="goal-label">{{ t('weeklyGoal') }}</text>
				<text class="goal-edit" @click="openGoal">{{ t('goalEdit') }}</text>
			</view>
			<view v-if="goal && goal.targetPomodoros">
				<view class="goal-nums">
					<text class="goal-done">{{ goal.achievedPomodoros }}</text>
					<text class="goal-target">/ {{ goal.targetPomodoros }} {{ t('pomodoroUnit') }}</text>
					<text class="goal-percent">{{ goal.progressPercent }}%</text>
				</view>
				<view class="goal-bar">
					<view class="goal-bar-inner" :style="{ width: barWidth }"></view>
				</view>
				<text class="goal-range">{{ goal.weekStart }} ~ {{ goal.weekEnd }}</text>
			</view>
			<text v-else class="goal-empty">{{ t('goalEmpty') }}</text>
		</view>

		<!-- 设置目标弹窗 -->
		<view v-if="showGoal" class="mask" @click="showGoal = false">
			<view class="dialog" @click.stop>
				<text class="d-title">{{ t('goalSetTitle') }}</text>
				<input class="input" v-model="goalTarget" type="number" :placeholder="t('goalTargetPlaceholder')" placeholder-class="ph" />
				<view class="d-actions">
					<button class="d-btn cancel" @click="showGoal = false">{{ t('cancel') }}</button>
					<button class="d-btn ok" @click="saveGoal">{{ t('confirm') }}</button>
				</view>
			</view>
		</view>

		<!-- 关联任务弹窗 -->
		<view v-if="showTaskPick" class="mask" @click="showTaskPick = false">
			<view class="dialog" @click.stop>
				<text class="d-title">{{ t('pickTask') }}</text>
				<scroll-view scroll-y class="task-list">
					<view class="task-opt" @click="pickTask(null)">
						<text class="to-name">{{ t('noTaskOption') }}</text>
						<text v-if="!selectedTaskId" class="to-check">✓</text>
					</view>
					<view v-for="item in tasks" :key="item.id" class="task-opt" @click="pickTask(item.id)">
						<text class="to-name">{{ item.title }}</text>
						<text v-if="selectedTaskId === item.id" class="to-check">✓</text>
					</view>
					<view v-if="!tasks.length" class="task-empty">{{ t('emptyTask') }}</view>
				</scroll-view>
				<view class="d-actions">
					<button class="d-btn cancel" @click="showTaskPick = false">{{ t('cancel') }}</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { startPomodoro, completePomodoro, interruptPomodoro, pagePomodoro } from '../../api/pomodoro.js'
	import { getGoal, setGoal } from '../../api/goal.js'
	import { listTask } from '../../api/task.js'
	import { pointsSummary } from '../../api/points.js'
	import { clearApiCache } from '../../utils/apiCache.js'

	const TOTAL_SECONDS = 25 * 60

	export default {
		data() {
			return {
				user: {},
				running: false,
				pomodoroId: null,
				timerSeconds: TOTAL_SECONDS,
				timer: null,
				startedAt: 0,
				goal: null,
				showGoal: false,
				goalTarget: '',
				tasks: [],
				selectedTaskId: null,
				showTaskPick: false
			}
		},
		computed: {
			displayTime() {
				const m = Math.floor(this.timerSeconds / 60)
				const s = this.timerSeconds % 60
				return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0')
			},
			statusText() {
				if (!this.running) return this.t('readyFocus')
				if (this.timerSeconds > 0) return this.t('focusing')
				return this.t('timeUp')
			},
			barWidth() {
				const p = this.goal ? this.goal.progressPercent || 0 : 0
				return Math.min(p, 100) + '%'
			},
			selectedTaskName() {
				if (!this.selectedTaskId) return this.t('noTask')
				const task = this.tasks.find((item) => item.id === this.selectedTaskId)
				return task ? task.title : this.t('noTask')
			}
		},
		onShow() {
			const token = uni.getStorageSync('accessToken')
			if (!token) {
				uni.reLaunch({ url: '/pages/login/login' })
				return
			}
			this.user = JSON.parse(uni.getStorageSync('user') || '{}')
			this.loadGoal()
			this.loadTasks()
			this.loadUser()
			this.restoreRunning()
		},
		onUnload() {
			this.clearTimer()
		},
		methods: {
			start() {
				const payload = this.selectedTaskId ? { taskId: this.selectedTaskId } : {}
				startPomodoro(payload)
					.then((data) => {
						this.pomodoroId = data.id
						this.running = true
						this.syncTimer(data.startTime)
					})
					.catch(() => {})
			},
			complete() {
				if (!this.pomodoroId) return
				completePomodoro(this.pomodoroId)
					.then(() => {
						uni.showToast({ title: this.t('completeToast'), icon: 'none' })
						this.reset()
						this.loadGoal()
						this.loadTasks()
						this.loadUser()
					})
					.catch(() => {})
			},
			interrupt() {
				if (!this.pomodoroId) return
				uni.showModal({
					title: this.t('interruptTitle'),
					content: this.t('interruptContent'),
					success: (res) => {
						if (!res.confirm) return
						interruptPomodoro(this.pomodoroId, '')
							.then(() => {
								uni.showToast({ title: this.t('interrupted'), icon: 'none' })
								this.reset()
							})
							.catch(() => {})
					}
				})
			},
			loadGoal() {
				getGoal()
					.then((data) => {
						this.goal = data
					})
					.catch(() => {})
			},
			loadTasks() {
				listTask({ pageNum: 1, pageSize: 50, topLevel: true })
					.then((data) => {
						this.tasks = (data.records || []).filter((item) => item.status !== 2)
						// 选中任务已删除/已完成时取消关联
						if (this.selectedTaskId && !this.tasks.some((item) => item.id === this.selectedTaskId)) {
							this.selectedTaskId = null
						}
					})
					.catch(() => {})
			},
			loadUser() {
				pointsSummary()
					.then((data) => {
						if (!data) return
						this.user = Object.assign({}, this.user, { level: data.level, points: data.points })
						uni.setStorageSync('user', JSON.stringify(this.user))
					})
					.catch(() => {})
			},
			// 页面刷新/切回时从服务端恢复进行中的番茄，避免重复开始被 409 卡住
			restoreRunning() {
				pagePomodoro({ pageNum: 1, pageSize: 1, status: 'RUNNING' })
					.then((data) => {
						const record = (data.records || [])[0]
						if (!record) {
							if (this.running && !this.pomodoroId) this.reset()
							return
						}
						this.pomodoroId = record.id
						this.running = true
						if (record.taskId) this.selectedTaskId = record.taskId
						this.syncTimer(record.startTime)
					})
					.catch(() => {})
			},
			pickTask(id) {
				this.selectedTaskId = id
				this.showTaskPick = false
			},
			// 计时以服务端 startTime 为锚点，切后台后仍按真实时间对齐
			syncTimer(startTime) {
				const start = startTime ? new Date(startTime).getTime() : Date.now()
				this.startedAt = isNaN(start) ? Date.now() : start
				this.startTicker()
			},
			startTicker() {
				this.clearTimer()
				this.updateRemaining()
				if (this.timerSeconds > 0) {
					this.timer = setInterval(() => this.updateRemaining(), 1000)
				}
			},
			updateRemaining() {
				if (!this.startedAt) return
				const elapsed = Math.floor((Date.now() - this.startedAt) / 1000)
				this.timerSeconds = Math.max(0, TOTAL_SECONDS - elapsed)
				if (this.timerSeconds <= 0) this.clearTimer()
			},
			openGoal() {
				this.goalTarget = this.goal && this.goal.targetPomodoros ? String(this.goal.targetPomodoros) : ''
				this.showGoal = true
			},
			saveGoal() {
				const target = parseInt(this.goalTarget, 10)
				if (!target || target < 1 || target > 200) {
					uni.showToast({ title: this.t('goalTargetRange'), icon: 'none' })
					return
				}
				setGoal(target)
					.then((data) => {
						if (data && data.queued) {
							// 离线：先用本地目标更新卡片，联网后队列重放覆盖服务端
							this.goal = { ...(this.goal || {}), targetPomodoros: target }
							this.showGoal = false
							uni.showToast({ title: this.t('offlineQueued'), icon: 'none' })
							return
						}
						this.goal = data
						this.showGoal = false
						uni.showToast({ title: this.t('saved'), icon: 'none' })
					})
					.catch(() => {})
			},
			reset() {
				this.clearTimer()
				this.running = false
				this.pomodoroId = null
				this.startedAt = 0
				this.timerSeconds = TOTAL_SECONDS
			},
			clearTimer() {
				if (this.timer) {
					clearInterval(this.timer)
					this.timer = null
				}
			},
			logout() {
				this.clearTimer()
				uni.removeStorageSync('accessToken')
				uni.removeStorageSync('refreshToken')
				uni.removeStorageSync('user')
				clearApiCache()
				uni.reLaunch({ url: '/pages/login/login' })
			}
		}
	}
</script>

<style>
	.page {
		min-height: 100vh;
		background: var(--bg);
		padding: 40rpx;
	}
	.header {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.hello {
		font-size: 40rpx;
		font-weight: bold;
		color: var(--text);
	}
	.logout {
		font-size: 28rpx;
		color: var(--text3);
	}
	.level {
		font-size: 28rpx;
		color: var(--primary);
		margin-top: 12rpx;
	}
	.timer {
		margin-top: 120rpx;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.time {
		font-size: 160rpx;
		font-weight: bold;
		color: var(--text);
		letter-spacing: 4rpx;
	}
	.status {
		font-size: 28rpx;
		color: var(--text3);
		margin-top: 20rpx;
	}
	.actions {
		margin-top: 40rpx;
		display: flex;
		gap: 24rpx;
	}
	.actions-running {
		margin-top: 120rpx;
	}
	.running-task {
		font-size: 26rpx;
		color: var(--primary);
		margin-top: 14rpx;
	}
	.task-pick {
		display: flex;
		align-items: center;
		margin-top: 80rpx;
		padding: 24rpx 28rpx;
		background: var(--card);
		border-radius: 16rpx;
	}
	.tp-label {
		font-size: 28rpx;
		color: var(--text3);
		margin-right: 20rpx;
	}
	.tp-value {
		flex: 1;
		font-size: 28rpx;
		color: var(--text);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.tp-arrow {
		font-size: 34rpx;
		color: var(--text4);
		margin-left: 12rpx;
	}
	.task-list {
		max-height: 560rpx;
		margin-bottom: 24rpx;
	}
	.task-opt {
		display: flex;
		align-items: center;
		padding: 22rpx 0;
		border-bottom: 1rpx solid var(--border-light);
	}
	.to-name {
		flex: 1;
		font-size: 30rpx;
		color: var(--text);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.to-check {
		font-size: 30rpx;
		color: var(--primary);
		margin-left: 16rpx;
	}
	.task-empty {
		font-size: 26rpx;
		color: var(--text4);
		text-align: center;
		padding: 40rpx 0;
	}
	.btn {
		flex: 1;
		border-radius: 16rpx;
		font-size: 32rpx;
		color: var(--card);
	}
	.start {
		background: var(--primary);
	}
	.complete {
		background: #2fb344;
	}
	.interrupt {
		background: var(--danger);
	}
	.goal-card {
		margin-top: 80rpx;
		background: var(--card);
		border-radius: 20rpx;
		padding: 28rpx;
	}
	.goal-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.goal-label {
		font-size: 30rpx;
		font-weight: bold;
		color: var(--text);
	}
	.goal-edit {
		font-size: 26rpx;
		color: var(--primary);
	}
	.goal-nums {
		display: flex;
		align-items: baseline;
		margin-top: 20rpx;
	}
	.goal-done {
		font-size: 56rpx;
		font-weight: bold;
		color: var(--primary);
	}
	.goal-target {
		font-size: 28rpx;
		color: var(--text3);
		margin-left: 10rpx;
	}
	.goal-percent {
		font-size: 28rpx;
		color: var(--text2);
		margin-left: auto;
	}
	.goal-bar {
		height: 16rpx;
		background: var(--border);
		border-radius: 8rpx;
		margin-top: 16rpx;
		overflow: hidden;
	}
	.goal-bar-inner {
		height: 100%;
		background: var(--primary);
		border-radius: 8rpx;
	}
	.goal-range {
		font-size: 24rpx;
		color: var(--text4);
		margin-top: 12rpx;
	}
	.goal-empty {
		font-size: 26rpx;
		color: var(--text4);
		margin-top: 16rpx;
	}
	.mask {
		position: fixed;
		left: 0;
		top: 0;
		right: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.5);
		display: flex;
		align-items: center;
		justify-content: center;
		z-index: 10;
	}
	.dialog {
		width: 600rpx;
		background: var(--card);
		border-radius: 24rpx;
		padding: 40rpx;
	}
	.d-title {
		font-size: 34rpx;
		font-weight: bold;
		color: var(--text);
		display: block;
		margin-bottom: 30rpx;
	}
	.input {
		height: 88rpx;
		border: 1rpx solid var(--border);
		border-radius: 16rpx;
		padding: 0 24rpx;
		margin-bottom: 24rpx;
		font-size: 30rpx;
	}
	.ph {
		color: var(--text4);
	}
	.d-actions {
		display: flex;
		gap: 20rpx;
		margin-top: 10rpx;
	}
	.d-btn {
		flex: 1;
		border-radius: 16rpx;
		font-size: 30rpx;
	}
	.cancel {
		background: var(--border);
		color: var(--text2);
	}
	.ok {
		background: var(--primary);
		color: #fff;
	}
</style>
