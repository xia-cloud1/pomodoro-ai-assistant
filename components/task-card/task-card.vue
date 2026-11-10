<template>
	<view class="task" @longpress="$emit('remove', task)">
		<view class="t-main">
			<text class="t-title" :class="{ done: task.status === 2 }">{{ task.title }}</text>
			<text class="t-desc" v-if="task.description">{{ task.description }}</text>
		</view>
		<view v-if="task.tags && task.tags.length" class="t-tags">
			<text v-for="tg in task.tags" :key="tg.id" class="tag" :style="{ background: tg.color || 'var(--primary)' }">{{ tg.name }}</text>
		</view>
		<view class="t-meta">
			<text class="quad" :style="{ background: quadColor(task.quadrant) }">{{ t(quadNameKey(task.quadrant)) }}</text>
			<text class="pomo">🍅 {{ task.actualPomodoros || 0 }}/{{ task.estimatedPomodoros || 0 }}</text>
			<text class="stat">{{ statusText }}</text>
		</view>
		<view v-if="repeat" class="t-repeat">
			<text class="repeat-badge">🔁 {{ t(repeatNameKey(task.repeatType)) }}</text>
			<text class="streak">{{ t('streakSuffix', { n: task.streakCount || 0 }) }}</text>
		</view>
		<view class="t-actions">
			<text v-if="repeat" class="checkin-btn" @click="$emit('checkin', task)">{{ t('checkinTask') }}</text>
			<text class="sub-btn" @click="$emit('toggle-children', task)">{{ expanded ? '▾' : '▸' }} {{ t('subtasks') }}</text>
			<text class="tag-btn" @click="$emit('pick-tags', task)">🏷 {{ t('tags') }}</text>
			<text v-if="task.status !== 2" class="done-btn" @click="$emit('done', task)">{{ t('complete') }}</text>
		</view>

		<!-- 子任务展开区：子任务列表与输入框内容都由页面持有，本组件只展示与抛事件 -->
		<view v-if="expanded" class="sub-block">
			<view v-for="c in children" :key="c.id" class="sub-row">
				<text class="sub-title" :class="{ done: c.status === 2 }">· {{ c.title }}</text>
				<text v-if="c.status === 2" class="sub-check">✓</text>
			</view>
			<view v-if="!children.length" class="sub-empty">{{ t('emptySubtask') }}</view>
			<view class="sub-add">
				<input class="sub-input" :value="subTitle" placeholder-class="ph" :placeholder="t('subtaskPlaceholder')"
					@input="onSubInput" />
				<text class="sub-add-btn" @click="$emit('add-subtask', task)">{{ t('addSubtask') }}</text>
			</view>
		</view>
	</view>
</template>

<script>
	import { quadNameKey, quadColor, repeatNameKey, statusNameKey, isRepeatTask } from '../../utils/taskMeta.js'

	/**
	 * 任务卡片。纯展示组件：不查接口、不改数据，用户操作一律抛事件给列表页处理，
	 * 这样「谁改了数据就在谁那儿刷新列表」这条线不会被拆散。
	 */
	export default {
		name: 'TaskCard',
		props: {
			task: { type: Object, required: true },
			// 子任务是否展开、已加载的子任务、子任务输入框内容（都按任务 id 存在页面里）
			expanded: { type: Boolean, default: false },
			children: { type: Array, default: () => [] },
			subTitle: { type: String, default: '' }
		},
		emits: ['remove', 'checkin', 'toggle-children', 'pick-tags', 'done', 'add-subtask', 'sub-title-change'],
		computed: {
			repeat() {
				return isRepeatTask(this.task)
			},
			statusText() {
				const key = statusNameKey(this.task.status)
				return key ? this.t(key) : ''
			}
		},
		methods: {
			quadNameKey,
			quadColor,
			repeatNameKey,
			// 输入框内容由页面持有，这里只把新值抛上去
			onSubInput(e) {
				this.$emit('sub-title-change', (e.detail && e.detail.value) || '')
			}
		}
	}
</script>

<style>
	.task {
		background: var(--card);
		border-radius: 20rpx;
		padding: 28rpx;
		margin-bottom: 20rpx;
	}
	.t-title {
		font-size: 32rpx;
		color: var(--text);
		font-weight: bold;
	}
	.t-title.done {
		color: var(--text3);
		text-decoration: line-through;
	}
	.t-desc {
		font-size: 26rpx;
		color: var(--text3);
		margin-top: 8rpx;
	}
	.t-tags {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		margin-top: 16rpx;
	}
	.tag {
		font-size: 22rpx;
		color: #fff;
		padding: 4rpx 16rpx;
		border-radius: 8rpx;
	}
	.t-meta {
		display: flex;
		align-items: center;
		margin-top: 16rpx;
	}
	.quad {
		font-size: 22rpx;
		color: var(--card);
		padding: 4rpx 16rpx;
		border-radius: 8rpx;
		margin-right: 16rpx;
	}
	.pomo {
		font-size: 26rpx;
		color: var(--text2);
		margin-right: 16rpx;
	}
	.stat {
		font-size: 26rpx;
		color: var(--primary);
	}
	.t-actions {
		display: flex;
		justify-content: flex-end;
		gap: 20rpx;
		margin-top: 16rpx;
	}
	.tag-btn {
		font-size: 26rpx;
		color: var(--primary);
		border: 1rpx solid var(--primary);
		padding: 8rpx 24rpx;
		border-radius: 8rpx;
	}
	.done-btn {
		font-size: 26rpx;
		color: #2fb344;
		border: 1rpx solid #2fb344;
		padding: 8rpx 24rpx;
		border-radius: 8rpx;
	}
	.t-repeat {
		display: flex;
		align-items: center;
		margin-top: 14rpx;
	}
	.repeat-badge {
		font-size: 24rpx;
		color: var(--primary);
		background: var(--fill);
		padding: 4rpx 16rpx;
		border-radius: 8rpx;
		margin-right: 16rpx;
	}
	.streak {
		font-size: 24rpx;
		color: var(--text3);
	}
	.checkin-btn {
		font-size: 26rpx;
		color: #f0a020;
		border: 1rpx solid #f0a020;
		padding: 8rpx 24rpx;
		border-radius: 8rpx;
	}
	.sub-btn {
		font-size: 26rpx;
		color: var(--text3);
		border: 1rpx solid var(--border);
		padding: 8rpx 24rpx;
		border-radius: 8rpx;
	}
	.sub-block {
		margin-top: 18rpx;
		padding-top: 18rpx;
		border-top: 1rpx solid var(--border-light);
	}
	.sub-row {
		display: flex;
		align-items: center;
		padding: 8rpx 0;
	}
	.sub-title {
		font-size: 28rpx;
		color: var(--text2);
		flex: 1;
	}
	.sub-title.done {
		color: var(--text4);
		text-decoration: line-through;
	}
	.sub-check {
		font-size: 26rpx;
		color: #2fb344;
	}
	.sub-empty {
		font-size: 26rpx;
		color: var(--text4);
		padding: 8rpx 0;
	}
	.sub-add {
		display: flex;
		align-items: center;
		margin-top: 12rpx;
	}
	.sub-input {
		flex: 1;
		height: 68rpx;
		border: 1rpx solid var(--border);
		border-radius: 12rpx;
		padding: 0 18rpx;
		font-size: 28rpx;
	}
	.sub-add-btn {
		font-size: 28rpx;
		color: var(--primary);
		padding: 0 20rpx;
	}
	.ph {
		color: var(--text4);
	}
</style>