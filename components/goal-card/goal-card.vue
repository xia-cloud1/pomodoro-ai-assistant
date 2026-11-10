<template>
	<view class="goal-card">
		<view class="goal-head">
			<text class="goal-label">{{ t('weeklyGoal') }}</text>
			<text class="goal-edit" @click="$emit('edit')">{{ t('goalEdit') }}</text>
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
</template>

<script>
	/**
	 * 本周目标卡片。数据由页面查好后传入，组件只负责展示与进度条宽度换算。
	 */
	export default {
		name: 'GoalCard',
		props: {
			goal: { type: Object, default: null }
		},
		emits: ['edit'],
		computed: {
			// 进度百分比可能超过 100（超额完成），进度条按 100 封顶
			barWidth() {
				const p = this.goal ? this.goal.progressPercent || 0 : 0
				return Math.min(p, 100) + '%'
			}
		}
	}
</script>

<style>
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
</style>