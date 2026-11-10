<template>
	<view class="page">
		<view class="topbar">
			<text class="title">{{ t('taskTitle') }}</text>
			<view class="bar-actions">
				<text class="manage" @click="showTagMgr = true">{{ t('manageTags') }}</text>
				<text class="add" @click="showCreate = true">+ {{ t('newTask') }}</text>
			</view>
		</view>

		<!-- 卡片只负责展示与抛事件，数据与接口都在本页，改动完统一在这里 load() -->
		<task-card v-for="item in tasks" :key="item.id" :task="item" :expanded="!!expanded[item.id]"
			:children="childrenMap[item.id] || []" :sub-title="subTitles[item.id] || ''" @remove="remove"
			@done="done" @checkin="checkin" @toggle-children="toggleChildren" @pick-tags="openTagPick"
			@add-subtask="addSubtask" @sub-title-change="subTitles[item.id] = $event" />

		<view v-if="!loading && tasks.length === 0" class="empty">{{ t('emptyTask') }}</view>

		<task-create-dialog :show="showCreate" :tags="tags" @close="showCreate = false" @created="onCreated" />

		<task-tags-dialog :show="showTagPick" :tags="tags" :task-id="pickTaskId" :selected-ids="pickTagIds"
			@close="showTagPick = false" @saved="onTagsSaved" />

		<tag-manager-dialog :show="showTagMgr" :tags="tags" @close="showTagMgr = false" @changed="load" />
	</view>
</template>

<script>
	import { listTask, createTask, updateTask, deleteTask, checkinTask, getTaskChildren } from '../../api/task.js'
	import { listTag } from '../../api/tag.js'

	export default {
		data() {
			return {
				tasks: [],
				tags: [],
				loading: false,
				showCreate: false,
				showTagPick: false,
				showTagMgr: false,
				pickTaskId: null,
				pickTagIds: [],
				expanded: {},
				childrenMap: {},
				subTitles: {}
			}
		},
		onShow() {
			if (!uni.getStorageSync('accessToken')) {
				uni.reLaunch({ url: '/pages/login/login' })
				return
			}
			this.load()
		},
		methods: {
			load() {
				this.loading = true
				listTag().then((d) => { this.tags = d || [] }).catch(() => {})
				listTask({ pageNum: 1, pageSize: 50, topLevel: true })
					.then((data) => {
						this.tasks = data.records || []
						// 已展开的任务同步刷新子任务，避免新增后显示旧数据
						Object.keys(this.expanded).forEach((id) => {
							if (this.expanded[id]) this.loadChildren(id)
						})
					})
					.catch(() => {})
					.finally(() => { this.loading = false })
			},
			onCreated() {
				this.showCreate = false
				this.load()
			},
			// 单任务改标签：用已有 tags 预填选中项，实际提交在弹窗里
			openTagPick(item) {
				this.pickTaskId = item.id
				this.pickTagIds = (item.tags || []).map((tg) => tg.id)
				this.showTagPick = true
			},
			onTagsSaved() {
				this.showTagPick = false
				this.load()
			},
			done(item) {
				updateTask(item.id, { status: 2 })
					.then((d) => {
						if (d && d.queued) {
							// 离线：本地先标记完成，联网后自动同步
							item.status = 2
							uni.showToast({ title: this.t('offlineQueued'), icon: 'none' })
							return
						}
						this.load()
					})
					.catch(() => {})
			},
			remove(item) {
				uni.showModal({
					title: this.t('deleteTaskTitle'),
					content: this.t('deleteTaskContent', { title: item.title }),
					success: (res) => {
						if (!res.confirm) return
						deleteTask(item.id)
							.then(() => { this.load() })
							.catch(() => {})
					}
				})
			},
			checkin(item) {
				checkinTask(item.id)
					.then((d) => {
						if (d && d.queued) {
							// 离线：本地先累加连续次数，联网后自动同步
							item.streakCount = (item.streakCount || 0) + 1
							uni.showToast({ title: this.t('offlineQueued'), icon: 'none' })
							return
						}
						uni.showToast({ title: this.t('taskCheckedIn'), icon: 'none' })
						this.load()
					})
					.catch(() => {})
			},
			toggleChildren(item) {
				const open = !this.expanded[item.id]
				this.expanded[item.id] = open
				if (open) {
					if (this.subTitles[item.id] === undefined) this.subTitles[item.id] = ''
					if (!this.childrenMap[item.id]) this.loadChildren(item.id)
				}
			},
			loadChildren(id) {
				getTaskChildren(id)
					.then((list) => { this.childrenMap[id] = list || [] })
					.catch(() => {})
			},
			addSubtask(item) {
				const title = (this.subTitles[item.id] || '').trim()
				if (!title) {
					uni.showToast({ title: this.t('needTaskTitle'), icon: 'none' })
					return
				}
				createTask({ title, parentId: item.id })
					.then(() => {
						this.subTitles[item.id] = ''
						uni.showToast({ title: this.t('created'), icon: 'none' })
						this.load()
					})
					.catch(() => {})
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
	.topbar {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 24rpx;
	}
	.title {
		font-size: 40rpx;
		font-weight: bold;
		color: var(--text);
	}
	.bar-actions {
		display: flex;
		align-items: center;
	}
	.manage {
		font-size: 28rpx;
		color: var(--text3);
		margin-right: 28rpx;
	}
	.add {
		font-size: 30rpx;
		color: var(--primary);
		font-weight: bold;
	}
	.empty {
		text-align: center;
		color: var(--text4);
		font-size: 28rpx;
		margin-top: 200rpx;
	}
</style>