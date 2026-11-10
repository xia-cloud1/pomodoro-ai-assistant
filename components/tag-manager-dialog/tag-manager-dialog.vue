<template>
	<mask-dialog :show="show" :title="t('manageTags')" :cancel-text="t('cancel')" :confirm-text="t('addTag')"
		@close="$emit('close')" @confirm="addTag">
		<view v-if="tags.length" class="tag-mgr">
			<view v-for="tg in tags" :key="tg.id" class="tag-row">
				<text class="tag-dot" :style="{ background: tg.color || 'var(--primary)' }"></text>
				<text class="tag-row-name">{{ tg.name }}</text>
				<text class="tag-row-del" @click="removeTag(tg)">✕</text>
			</view>
		</view>
		<view v-else class="tag-empty">{{ t('emptyTag') }}</view>
		<input class="input" v-model="newTagName" :placeholder="t('tagNamePlaceholder')" placeholder-class="ph" />
	</mask-dialog>
</template>

<script>
	import { createTag, deleteTag } from '../../api/tag.js'
	import { TAG_COLORS } from '../../utils/taskMeta.js'

	/**
	 * 标签管理：列出全部标签、删除、新增（新增自动按顺序取色）。
	 * 改动只抛 changed，标签列表仍由任务页统一持有，避免两处各存一份。
	 */
	export default {
		name: 'TagManagerDialog',
		props: {
			show: { type: Boolean, default: false },
			tags: { type: Array, default: () => [] }
		},
		emits: ['close', 'changed'],
		data() {
			return {
				newTagName: ''
			}
		},
		watch: {
			show(val) {
				if (val) this.newTagName = ''
			}
		},
		methods: {
			addTag() {
				const name = (this.newTagName || '').trim()
				if (!name) {
					uni.showToast({ title: this.t('needTagName'), icon: 'none' })
					return
				}
				const color = TAG_COLORS[this.tags.length % TAG_COLORS.length]
				createTag(name, color)
					.then(() => {
						this.newTagName = ''
						uni.showToast({ title: this.t('saved'), icon: 'none' })
						this.$emit('changed')
					})
					.catch(() => {})
			},
			removeTag(tag) {
				uni.showModal({
					title: this.t('deleteTagTitle'),
					content: this.t('deleteTagContent', { name: tag.name }),
					success: (res) => {
						if (!res.confirm) return
						deleteTag(tag.id)
							.then(() => { this.$emit('changed') })
							.catch(() => {})
					}
				})
			}
		}
	}
</script>

<style>
	.tag-mgr {
		margin-bottom: 24rpx;
	}
	.tag-row {
		display: flex;
		align-items: center;
		padding: 20rpx 0;
		border-bottom: 1rpx solid var(--border-light);
	}
	.tag-dot {
		width: 24rpx;
		height: 24rpx;
		border-radius: 6rpx;
		margin-right: 20rpx;
	}
	.tag-row-name {
		flex: 1;
		font-size: 30rpx;
		color: var(--text);
	}
	.tag-row-del {
		font-size: 32rpx;
		color: var(--danger);
		padding: 0 10rpx;
	}
	.tag-empty {
		font-size: 26rpx;
		color: var(--text4);
		margin-bottom: 24rpx;
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
</style>