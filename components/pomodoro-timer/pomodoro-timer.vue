<template>
	<view class="timer">
		<text class="time">{{ time }}</text>
		<text class="status">{{ status }}</text>
		<text v-if="taskName" class="running-task">{{ taskName }}</text>
	</view>
</template>

<script>
	/**
	 * 番茄钟大计时显示。纯展示组件：不做计时，剩余时间由页面按服务端 startTime 算好后传进来。
	 */
	export default {
		name: 'PomodoroTimer',
		props: {
			time: { type: String, default: '00:00' },
			status: { type: String, default: '' },
			// 进行中关联的任务名，未关联/未开始时不传
			taskName: { type: String, default: '' }
		}
	}
</script>

<style>
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
	.running-task {
		font-size: 26rpx;
		color: var(--primary);
		margin-top: 14rpx;
	}
</style>