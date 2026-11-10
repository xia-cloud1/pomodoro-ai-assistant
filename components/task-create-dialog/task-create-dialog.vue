<template>
	<mask-dialog :show="show" :title="t('newTaskTitle')" :cancel-text="t('cancel')" :confirm-text="t('confirm')"
		@close="$emit('close')" @confirm="submit">
		<input class="input" v-model="form.title" :placeholder="t('taskTitlePlaceholder')" placeholder-class="ph" />
		<input class="input" v-model="form.estimatedPomodoros" type="number" :placeholder="t('estPomodoroPlaceholder')"
			placeholder-class="ph" />
		<view class="quads">
			<text v-for="q in 4" :key="q" :class="['q', form.quadrant === q ? 'q-active' : '']" @click="form.quadrant = q">{{ t(quadNameKey(q)) }}</text>
		</view>
		<view class="repeats">
			<text v-for="r in repeatOptions" :key="r" :class="['q', form.repeatType === r ? 'q-active' : '']"
				@click="form.repeatType = r">{{ t(repeatNameKey(r)) }}</text>
		</view>
		<picker mode="date" @change="onDate">
			<view class="date">{{ t('dueDate') }}：{{ form.dueDate || t('noLimit') }}</view>
		</picker>
		<view v-if="tags.length" class="tag-block">
			<text class="block-label">{{ t('tags') }}</text>
			<tag-picker :tags="tags" :selected-ids="form.tagIds" @toggle="toggleTag" />
		</view>
	</mask-dialog>
</template>

<script>
	import { createTask, bindTaskTags } from '../../api/task.js'
	import { quadNameKey, repeatNameKey, REPEAT_OPTIONS } from '../../utils/taskMeta.js'

	const EMPTY_FORM = () => ({ title: '', quadrant: null, estimatedPomodoros: null, dueDate: '', tagIds: [], repeatType: 'NONE' })

	/**
	 * 新建任务弹窗。表单状态与提交都在组件内（含「先建任务拿 id 再绑标签」两步），
	 * 建好只抛 created，让列表页自己去刷新。
	 */
	export default {
		name: 'TaskCreateDialog',
		props: {
			show: { type: Boolean, default: false },
			tags: { type: Array, default: () => [] }
		},
		emits: ['close', 'created'],
		data() {
			return {
				form: EMPTY_FORM(),
				// 复制一份，避免组件改到 taskMeta 里的共享数组
				repeatOptions: [...REPEAT_OPTIONS]
			}
		},
		watch: {
			// 每次打开都从空表单开始（原来是在页面的 openCreate 里重置）
			show(val) {
				if (val) this.form = EMPTY_FORM()
			}
		},
		methods: {
			quadNameKey,
			repeatNameKey,
			onDate(e) {
				this.form.dueDate = e.detail.value
			},
			toggleTag(id) {
				const i = this.form.tagIds.indexOf(id)
				if (i >= 0) this.form.tagIds.splice(i, 1)
				else this.form.tagIds.push(id)
			},
			// 先建任务拿到 id，再绑定选中的标签
			submit() {
				if (!this.form.title) {
					uni.showToast({ title: this.t('needTaskTitle'), icon: 'none' })
					return
				}
				const payload = { title: this.form.title, repeatType: this.form.repeatType || 'NONE' }
				if (this.form.quadrant) payload.quadrant = this.form.quadrant
				if (this.form.estimatedPomodoros) payload.estimatedPomodoros = Number(this.form.estimatedPomodoros)
				if (this.form.dueDate) payload.dueDate = this.form.dueDate
				createTask(payload)
					.then((created) => {
						const id = created && created.id
						if (id && this.form.tagIds.length) return bindTaskTags(id, this.form.tagIds)
					})
					.then(() => {
						uni.showToast({ title: this.t('created'), icon: 'none' })
						this.$emit('created')
					})
					.catch(() => {})
			}
		}
	}
</script>

<style>
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
	.quads {
		display: flex;
		flex-wrap: wrap;
		gap: 16rpx;
		margin-bottom: 24rpx;
	}
	.q {
		font-size: 24rpx;
		color: var(--text2);
		padding: 10rpx 20rpx;
		border-radius: 8rpx;
		border: 1rpx solid var(--border);
	}
	.q-active {
		color: var(--primary);
		border-color: var(--primary);
		background: var(--primary-light);
	}
	.date {
		height: 88rpx;
		line-height: 88rpx;
		border: 1rpx solid var(--border);
		border-radius: 16rpx;
		padding: 0 24rpx;
		font-size: 30rpx;
		color: var(--text);
		margin-bottom: 24rpx;
	}
	.tag-block {
		margin-bottom: 24rpx;
	}
	.block-label {
		font-size: 26rpx;
		color: var(--text3);
		display: block;
		margin-bottom: 16rpx;
	}
	.repeats {
		display: flex;
		gap: 16rpx;
		margin-bottom: 24rpx;
	}
</style>