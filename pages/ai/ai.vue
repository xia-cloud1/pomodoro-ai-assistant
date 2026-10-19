<template>
	<view class="page">
		<view class="tabs">
			<text v-for="tab in tabs" :key="tab.k" :class="['tab', mode === tab.k ? 'on' : '']" @click="switchMode(tab.k)">{{ t(tab.name) }}</text>
		</view>

		<!-- 报告模式：日报/周报/月报 -->
		<template v-if="isReport">
			<view class="stats">
				<view class="s-item">
					<text class="s-num">{{ stat.pomodoros || 0 }}</text>
					<text class="s-l">{{ t('pomodoroUnit') }}</text>
				</view>
				<view class="s-item">
					<text class="s-num">{{ stat.minutes || 0 }}</text>
					<text class="s-l">{{ t('minutesUnit') }}</text>
				</view>
				<view class="s-item">
					<text class="s-num">{{ stat.score || 0 }}</text>
					<text class="s-l">{{ t('scoreUnit') }}</text>
				</view>
			</view>

			<view class="analysis">
				<text class="a-title">{{ t('aiAnalysis') }}{{ streaming && !analysis ? '（' + t('generating') + '）' : '' }}</text>
				<text class="a-body">{{ analysis || (streaming ? '' : t('aiNotReady')) }}</text>
			</view>
		</template>

		<!-- 问答模式 -->
		<view v-else-if="mode === 'ask'" class="panel">
			<textarea class="ta" v-model="askQ" :placeholder="t('askPlaceholder')" placeholder-class="ph" />
			<button class="btn" :disabled="asking" @click="doAsk">{{ asking ? t('thinking') : t('askBtn') }}</button>
			<view v-if="askAnswer" class="answer">
				<text class="a-title">{{ t('answer') }}</text>
				<text class="a-body">{{ askAnswer }}</text>
			</view>
		</view>

		<!-- 任务拆分模式 -->
		<view v-else-if="mode === 'split'" class="panel">
			<input class="input" v-model="splitTitle" :placeholder="t('splitTitlePlaceholder')" placeholder-class="ph" />
			<input class="input" v-model="splitDesc" :placeholder="t('splitDescPlaceholder')" placeholder-class="ph" />
			<button class="btn" :disabled="splitting" @click="doSplit">{{ splitting ? t('splitting') : t('splitTaskBtn') }}</button>
			<view v-if="subtasks.length" class="subtasks">
				<text class="a-title">{{ t('subtasks') }}</text>
				<view v-for="(s, i) in subtasks" :key="i" class="subtask">{{ i + 1 }}. {{ s }}</view>
			</view>
		</view>

		<!-- 黄金专注时段模式 -->
		<view v-else class="panel">
			<view v-if="golden.periods && golden.periods.length">
				<view v-for="(p, i) in golden.periods" :key="i" class="gp">
					<text class="gp-label">{{ p.label }}</text>
					<text class="gp-cnt">{{ t('gpCnt', { n: p.totalCount, m: p.totalMinutes }) }}</text>
				</view>
				<view v-if="golden.aiSuggestion" class="answer">
					<text class="a-title">{{ t('suggestion') }}</text>
					<text class="a-body">{{ golden.aiSuggestion }}</text>
				</view>
			</view>
			<view v-else class="empty">{{ t('emptyGolden') }}</view>
		</view>
	</view>
</template>

<script>
	import { dailyReport, weeklyReport, monthlyReport, streamWeeklyReport, ask, splitTask, goldenHours } from '../../api/ai.js'

	export default {
		data() {
			return {
				tabs: [
					{ k: 'daily', name: 'tabDaily' },
					{ k: 'weekly', name: 'tabWeekly' },
					{ k: 'monthly', name: 'tabMonthly' },
					{ k: 'ask', name: 'tabAsk' },
					{ k: 'split', name: 'tabSplit' },
					{ k: 'golden', name: 'tabGolden' }
				],
				mode: 'daily',
				stat: {},
				analysis: '',
				streamSeq: 0,
				streaming: false,
				askQ: '',
				askAnswer: '',
				asking: false,
				splitTitle: '',
				splitDesc: '',
				subtasks: [],
				splitting: false,
				golden: { periods: [], aiSuggestion: '' }
			}
		},
		computed: {
			isReport() {
				return ['daily', 'weekly', 'monthly'].indexOf(this.mode) >= 0
			}
		},
		onLoad() {
			this.load()
		},
		methods: {
			switchMode(m) {
				this.mode = m
				this.stat = {}
				this.analysis = ''
				this.streaming = false
				this.streamSeq++ // 使旧的流式回调失效
				if (this.isReport) {
					this.load()
				} else if (m === 'golden') {
					this.loadGolden()
				}
			},
			load() {
				const now = new Date()
				if (this.mode === 'daily') {
					dailyReport().then((d) => {
						this.stat = { pomodoros: d.totalPomodoros, minutes: d.totalMinutes, score: d.focusScore }
						this.analysis = d.aiAnalysis || ''
					}).catch(() => {})
				} else if (this.mode === 'weekly') {
					weeklyReport().then((d) => {
						this.stat = { pomodoros: d.totalPomodoros, minutes: d.totalMinutes, score: d.avgFocusMinutes }
					}).catch(() => {})
					this.streaming = true
					const seq = ++this.streamSeq
					streamWeeklyReport(
						(chunk) => { if (seq === this.streamSeq) this.analysis += chunk },
						() => { if (seq === this.streamSeq) this.streaming = false },
						() => {
							if (seq !== this.streamSeq) return
							this.streaming = false
							if (!this.analysis) this.analysis = this.t('aiNotReady')
						}
					)
				} else {
					monthlyReport(now.getFullYear(), now.getMonth() + 1).then((d) => {
						this.stat = { pomodoros: d.summary.totalPomodoros, minutes: d.summary.totalMinutes, score: d.summary.avgFocusScore }
						this.analysis = d.aiAnalysis || ''
					}).catch(() => {})
				}
			},
			doAsk() {
				if (!this.askQ.trim()) {
					uni.showToast({ title: this.t('needQuestion'), icon: 'none' })
					return
				}
				this.asking = true
				this.askAnswer = ''
				ask(this.askQ.trim()).then((d) => {
					this.askAnswer = d.answer || this.t('aiUnavailable')
				}).catch(() => {}).finally(() => { this.asking = false })
			},
			doSplit() {
				if (!this.splitTitle.trim()) {
					uni.showToast({ title: this.t('needTitle'), icon: 'none' })
					return
				}
				this.splitting = true
				this.subtasks = []
				splitTask(this.splitTitle.trim(), this.splitDesc.trim()).then((d) => {
					this.subtasks = d.subtasks || []
					if (!this.subtasks.length) {
						uni.showToast({ title: this.t('aiUnavailable'), icon: 'none' })
					}
				}).catch(() => {}).finally(() => { this.splitting = false })
			},
			loadGolden() {
				goldenHours().then((d) => { this.golden = d || { periods: [], aiSuggestion: '' } }).catch(() => {})
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
	.tabs {
		display: flex;
		flex-wrap: wrap;
		background: var(--card);
		border-radius: 16rpx;
		margin-bottom: 24rpx;
		overflow: hidden;
	}
	.tab {
		flex: 1;
		min-width: 25%;
		text-align: center;
		padding: 24rpx 0;
		font-size: 28rpx;
		color: var(--text2);
	}
	.tab.on {
		color: var(--card);
		background: var(--primary);
	}
	.stats {
		display: flex;
		background: var(--card);
		border-radius: 20rpx;
		padding: 40rpx 0;
		margin-bottom: 24rpx;
	}
	.s-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.s-num {
		font-size: 44rpx;
		font-weight: bold;
		color: var(--primary);
	}
	.s-l {
		font-size: 24rpx;
		color: var(--text3);
		margin-top: 8rpx;
	}
	.analysis {
		background: var(--card);
		border-radius: 20rpx;
		padding: 28rpx;
	}
	.a-title {
		font-size: 30rpx;
		font-weight: bold;
		color: var(--text);
		display: block;
		margin-bottom: 16rpx;
	}
	.a-body {
		font-size: 28rpx;
		color: var(--text2);
		line-height: 1.7;
		white-space: pre-wrap;
	}
	.panel {
		background: var(--card);
		border-radius: 20rpx;
		padding: 28rpx;
	}
	.ta {
		width: 100%;
		height: 160rpx;
		border: 1rpx solid var(--border);
		border-radius: 16rpx;
		padding: 20rpx 24rpx;
		margin-bottom: 20rpx;
		font-size: 28rpx;
		box-sizing: border-box;
	}
	.input {
		height: 80rpx;
		border: 1rpx solid var(--border);
		border-radius: 16rpx;
		padding: 0 24rpx;
		margin-bottom: 20rpx;
		font-size: 28rpx;
	}
	.btn {
		background: var(--primary);
		color: var(--card);
		border-radius: 16rpx;
		font-size: 30rpx;
	}
	.btn[disabled] {
		opacity: 0.6;
	}
	.answer {
		margin-top: 24rpx;
	}
	.subtasks {
		margin-top: 24rpx;
	}
	.subtask {
		padding: 18rpx 0;
		border-bottom: 1rpx solid var(--border-light);
		font-size: 28rpx;
		color: var(--text);
	}
	.gp {
		display: flex;
		flex-direction: column;
		padding: 24rpx;
		background: var(--primary-light);
		border-radius: 16rpx;
		margin-bottom: 20rpx;
	}
	.gp-label {
		font-size: 34rpx;
		font-weight: bold;
		color: var(--primary);
	}
	.gp-cnt {
		font-size: 26rpx;
		color: var(--text3);
		margin-top: 8rpx;
	}
	.empty {
		font-size: 28rpx;
		color: var(--text3);
		text-align: center;
		padding: 60rpx 0;
	}
	.ph {
		color: var(--text4);
	}
</style>
