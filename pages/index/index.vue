<template>
	<view class="page">
		<view class="header">
			<text class="hello">{{ user.username || t('user') }}</text>
			<text class="logout" @click="logout">{{ t('logout') }}</text>
		</view>
		<view class="level">Lv.{{ user.level || 0 }} · {{ user.points || 0 }} {{ t('pointsUnit') }}</view>

		<pomodoro-timer :time="displayTime" :status="statusText" :task-name="runningTaskName" />

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

		<goal-card :goal="goal" @edit="openGoal" />

		<!-- 设置目标弹窗 -->
		<mask-dialog :show="showGoal" :title="t('goalSetTitle')" :cancel-text="t('cancel')" :confirm-text="t('confirm')"
			@close="showGoal = false" @confirm="saveGoal">
			<input class="input" v-model="goalTarget" type="number" :placeholder="t('goalTargetPlaceholder')" placeholder-class="ph" />
		</mask-dialog>

		<!-- 关联任务弹窗 -->
		<task-pick-dialog :show="showTaskPick" :tasks="tasks" :selected-id="selectedTaskId"
			@close="showTaskPick = false" @pick="pickTask" />
	</view>
</template>

<script>
	import { startPomodoro, completePomodoro, interruptPomodoro, pagePomodoro } from '../../api/pomodoro.js'
	import { getGoal, setGoal } from '../../api/goal.js'
	import { listTask } from '../../api/task.js'
	import { pointsSummary } from '../../api/points.js'
	import { clearApiCache } from '../../utils/apiCache.js'

	const TOTAL_SECONDS = 25 * 60
	// 与服务端防作弊上限一致：超过这个时长的番茄无法记为「完成」
	const MAX_FOCUS_SECONDS = 4 * 60 * 60

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
			// 只有进行中且关联了任务时才在计时下方显示任务名
			runningTaskName() {
				if (!this.running || !this.selectedTaskId) return ''
				return this.selectedTaskName
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
					.catch((err) => {
						// 超过单次上限（4 小时）时服务端必然拒绝「完成」，而记录仍是 RUNNING，
						// 新的番茄也开不了——不主动给出出路，用户只会卡在「点完成→报错→再点」的循环里。
						// 判据用本地已专注时长（计时本就以服务端 startTime 为锚点），不去匹配后端文案。
						if (err && err.code === 400 && this.elapsedSeconds() > MAX_FOCUS_SECONDS) {
							this.confirmOvertimeInterrupt()
						}
					})
			},
			interrupt() {
				if (!this.pomodoroId) return
				uni.showModal({
					title: this.t('interruptTitle'),
					content: this.t('interruptContent'),
					success: (res) => {
						if (!res.confirm) return
						this.doInterrupt()
					}
				})
			},
			// 超时番茄的唯一出路：说明原因后一次确认直接中断，不再叠一层「确定中断吗」
			confirmOvertimeInterrupt() {
				uni.showModal({
					title: this.t('overtimeTitle'),
					content: this.t('overtimeContent'),
					confirmText: this.t('overtimeConfirm'),
					success: (res) => {
						if (!res.confirm) return
						this.doInterrupt()
					}
				})
			},
			doInterrupt() {
				interruptPomodoro(this.pomodoroId, '')
					.then(() => {
						uni.showToast({ title: this.t('interrupted'), icon: 'none' })
						this.reset()
					})
					.catch(() => {})
			},
			elapsedSeconds() {
				if (!this.startedAt) return 0
				return Math.floor((Date.now() - this.startedAt) / 1000)
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
	.actions {
		margin-top: 40rpx;
		display: flex;
		gap: 24rpx;
	}
	.actions-running {
		margin-top: 120rpx;
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
	/* 目标弹窗的输入框由插槽传入，样式留在页面侧 */
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
</style>