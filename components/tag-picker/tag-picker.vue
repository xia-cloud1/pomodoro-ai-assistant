<template>
	<view v-if="tags.length" class="tag-list">
		<text v-for="tg in tags" :key="tg.id" :class="['tag-opt', selectedIds.indexOf(tg.id) >= 0 ? 'tag-opt-on' : '']"
			:style="selectedIds.indexOf(tg.id) >= 0 ? { background: tg.color || 'var(--primary)', borderColor: 'transparent', color: '#fff' } : {}"
			@click="$emit('toggle', tg.id)">{{ tg.name }}</text>
	</view>
	<view v-else-if="emptyText" class="tag-empty">{{ emptyText }}</view>
</template>

<script>
	/**
	 * 标签多选。只负责渲染与抛事件，选中集合由使用方持有（新建任务的表单 / 单任务改标签各有一份）。
	 * 点某个标签抛 toggle(id)，由使用方决定加进还是移出选中集合。
	 */
	export default {
		name: 'TagPicker',
		props: {
			tags: { type: Array, default: () => [] },
			selectedIds: { type: Array, default: () => [] },
			// 没有标签时的兜底文案，留空则不渲染任何东西
			emptyText: { type: String, default: '' }
		},
		emits: ['toggle']
	}
</script>

<style>
	.tag-list {
		display: flex;
		flex-wrap: wrap;
		gap: 16rpx;
		margin-bottom: 24rpx;
	}
	.tag-opt {
		font-size: 24rpx;
		color: var(--text2);
		padding: 10rpx 24rpx;
		border-radius: 8rpx;
		border: 1rpx solid var(--border);
	}
	.tag-opt-on {
		font-weight: bold;
	}
	.tag-empty {
		font-size: 26rpx;
		color: var(--text4);
		margin-bottom: 24rpx;
	}
</style>