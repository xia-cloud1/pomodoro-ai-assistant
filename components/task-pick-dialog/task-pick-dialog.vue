<template>
	<mask-dialog :show="show" :title="t('pickTask')" :cancel-text="t('cancel')" @close="$emit('close')">
		<scroll-view scroll-y class="task-list">
			<view class="task-opt" @click="$emit('pick', null)">
				<text class="to-name">{{ t('noTaskOption') }}</text>
				<text v-if="!selectedId" class="to-check">✓</text>
			</view>
			<view v-for="item in tasks" :key="item.id" class="task-opt" @click="$emit('pick', item.id)">
				<text class="to-name">{{ item.title }}</text>
				<text v-if="selectedId === item.id" class="to-check">✓</text>
			</view>
			<view v-if="!tasks.length" class="task-empty">{{ t('emptyTask') }}</view>
		</scroll-view>
	</mask-dialog>
</template>

<script>
	/**
	 * 关联任务选择弹窗。开始番茄前选一个任务，选中结果通过 pick 事件抛给页面。
	 * 只有取消按钮，选中即关闭（由页面把 show 置 false）。
	 */
	export default {
		name: 'TaskPickDialog',
		props: {
			show: { type: Boolean, default: false },
			tasks: { type: Array, default: () => [] },
			// 当前选中的任务 id，null 表示不关联
			selectedId: { type: [Number, String], default: null }
		},
		emits: ['close', 'pick']
	}
</script>

<style>
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
</style>