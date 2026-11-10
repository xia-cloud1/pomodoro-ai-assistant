<template>
	<mask-dialog :show="show" :title="t('pickTags')" :cancel-text="t('cancel')" :confirm-text="t('confirm')"
		@close="$emit('close')" @confirm="save">
		<tag-picker :tags="tags" :selected-ids="pickedIds" :empty-text="t('emptyTag')" @toggle="toggleTag" />
	</mask-dialog>
</template>

<script>
	import { bindTaskTags } from '../../api/task.js'

	/**
	 * 给单个任务改标签。打开时用任务已有的标签预填勾选，确认后整体覆盖绑定。
	 */
	export default {
		name: 'TaskTagsDialog',
		props: {
			show: { type: Boolean, default: false },
			tags: { type: Array, default: () => [] },
			taskId: { type: [Number, String], default: null },
			// 该任务当前已绑定的标签 id
			selectedIds: { type: Array, default: () => [] }
		},
		emits: ['close', 'saved'],
		data() {
			return {
				pickedIds: []
			}
		},
		watch: {
			// 每次打开都以传入的当前绑定为起点
			show(val) {
				if (val) this.pickedIds = [...this.selectedIds]
			}
		},
		methods: {
			toggleTag(id) {
				const i = this.pickedIds.indexOf(id)
				if (i >= 0) this.pickedIds.splice(i, 1)
				else this.pickedIds.push(id)
			},
			save() {
				bindTaskTags(this.taskId, this.pickedIds)
					.then(() => {
						uni.showToast({ title: this.t('saved'), icon: 'none' })
						this.$emit('saved')
					})
					.catch(() => {})
			}
		}
	}
</script>