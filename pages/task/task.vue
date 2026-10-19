<template>
	<view class="page">
		<view class="topbar">
			<text class="title">{{ t('taskTitle') }}</text>
			<view class="bar-actions">
				<text class="manage" @click="openTagMgr">{{ t('manageTags') }}</text>
				<text class="add" @click="openCreate">+ {{ t('newTask') }}</text>
			</view>
		</view>

		<view v-for="item in tasks" :key="item.id" class="task" @longpress="remove(item)">
			<view class="t-main">
				<text class="t-title" :class="{ done: item.status === 2 }">{{ item.title }}</text>
				<text class="t-desc" v-if="item.description">{{ item.description }}</text>
			</view>
			<view v-if="item.tags && item.tags.length" class="t-tags">
				<text v-for="tg in item.tags" :key="tg.id" class="tag" :style="{ background: tg.color || 'var(--primary)' }">{{ tg.name }}</text>
			</view>
			<view class="t-meta">
				<text class="quad" :style="{ background: quadColor(item.quadrant) }">{{ quadName(item.quadrant) }}</text>
				<text class="pomo">🍅 {{ item.actualPomodoros || 0 }}/{{ item.estimatedPomodoros || 0 }}</text>
				<text class="stat">{{ statusName(item.status) }}</text>
			</view>
			<view v-if="item.repeatType && item.repeatType !== 'NONE'" class="t-repeat">
				<text class="repeat-badge">🔁 {{ repeatName(item.repeatType) }}</text>
				<text class="streak">{{ t('streakSuffix', { n: item.streakCount || 0 }) }}</text>
			</view>
			<view class="t-actions">
				<text v-if="item.repeatType && item.repeatType !== 'NONE'" class="checkin-btn" @click="checkin(item)">{{ t('checkinTask') }}</text>
				<text class="sub-btn" @click="toggleChildren(item)">{{ expanded[item.id] ? '▾' : '▸' }} {{ t('subtasks') }}</text>
				<text class="tag-btn" @click="openTagPick(item)">🏷 {{ t('tags') }}</text>
				<text v-if="item.status !== 2" class="done-btn" @click="done(item)">{{ t('complete') }}</text>
			</view>

			<!-- 子任务展开区 -->
			<view v-if="expanded[item.id]" class="sub-block">
				<view v-for="c in childrenMap[item.id] || []" :key="c.id" class="sub-row">
					<text class="sub-title" :class="{ done: c.status === 2 }">· {{ c.title }}</text>
					<text v-if="c.status === 2" class="sub-check">✓</text>
				</view>
				<view v-if="!(childrenMap[item.id] || []).length" class="sub-empty">{{ t('emptySubtask') }}</view>
				<view class="sub-add">
					<input class="sub-input" v-model="subTitles[item.id]" :placeholder="t('subtaskPlaceholder')" placeholder-class="ph" />
					<text class="sub-add-btn" @click="addSubtask(item)">{{ t('addSubtask') }}</text>
				</view>
			</view>
		</view>

		<view v-if="!loading && tasks.length === 0" class="empty">{{ t('emptyTask') }}</view>

		<!-- 新建弹窗 -->
		<view v-if="showCreate" class="mask" @click="showCreate = false">
			<view class="dialog" @click.stop>
				<text class="d-title">{{ t('newTaskTitle') }}</text>
				<input class="input" v-model="form.title" :placeholder="t('taskTitlePlaceholder')" placeholder-class="ph" />
				<input class="input" v-model="form.estimatedPomodoros" type="number" :placeholder="t('estPomodoroPlaceholder')" placeholder-class="ph" />
				<view class="quads">
					<text v-for="q in 4" :key="q" :class="['q', form.quadrant === q ? 'q-active' : '']" @click="form.quadrant = q">{{ quadName(q) }}</text>
				</view>
				<view class="repeats">
					<text v-for="r in repeatOptions" :key="r" :class="['q', form.repeatType === r ? 'q-active' : '']" @click="form.repeatType = r">{{ repeatName(r) }}</text>
				</view>
				<picker mode="date" @change="onDate">
					<view class="date">{{ t('dueDate') }}：{{ form.dueDate || t('noLimit') }}</view>
				</picker>
				<view v-if="tags.length" class="tag-block">
					<text class="block-label">{{ t('tags') }}</text>
					<view class="tag-list">
						<text v-for="tg in tags" :key="tg.id" :class="['tag-opt', form.tagIds.indexOf(tg.id) >= 0 ? 'tag-opt-on' : '']"
							:style="form.tagIds.indexOf(tg.id) >= 0 ? { background: tg.color || 'var(--primary)', borderColor: 'transparent', color: '#fff' } : {}"
							@click="toggleTag(form.tagIds, tg.id)">{{ tg.name }}</text>
					</view>
				</view>
				<view class="d-actions">
					<button class="d-btn cancel" @click="showCreate = false">{{ t('cancel') }}</button>
					<button class="d-btn ok" @click="submit">{{ t('confirm') }}</button>
				</view>
			</view>
		</view>

		<!-- 单任务改标签弹窗 -->
		<view v-if="showTagPick" class="mask" @click="showTagPick = false">
			<view class="dialog" @click.stop>
				<text class="d-title">{{ t('pickTags') }}</text>
				<view v-if="tags.length" class="tag-list">
					<text v-for="tg in tags" :key="tg.id" :class="['tag-opt', pickTagIds.indexOf(tg.id) >= 0 ? 'tag-opt-on' : '']"
						:style="pickTagIds.indexOf(tg.id) >= 0 ? { background: tg.color || 'var(--primary)', borderColor: 'transparent', color: '#fff' } : {}"
						@click="toggleTag(pickTagIds, tg.id)">{{ tg.name }}</text>
				</view>
				<view v-else class="tag-empty">{{ t('emptyTag') }}</view>
				<view class="d-actions">
					<button class="d-btn cancel" @click="showTagPick = false">{{ t('cancel') }}</button>
					<button class="d-btn ok" @click="saveTaskTags">{{ t('confirm') }}</button>
				</view>
			</view>
		</view>

		<!-- 标签管理弹窗 -->
		<view v-if="showTagMgr" class="mask" @click="showTagMgr = false">
			<view class="dialog" @click.stop>
				<text class="d-title">{{ t('manageTags') }}</text>
				<view v-if="tags.length" class="tag-mgr">
					<view v-for="tg in tags" :key="tg.id" class="tag-row">
						<text class="tag-dot" :style="{ background: tg.color || 'var(--primary)' }"></text>
						<text class="tag-row-name">{{ tg.name }}</text>
						<text class="tag-row-del" @click="removeTag(tg)">✕</text>
					</view>
				</view>
				<view v-else class="tag-empty">{{ t('emptyTag') }}</view>
				<input class="input" v-model="newTagName" :placeholder="t('tagNamePlaceholder')" placeholder-class="ph" />
				<view class="d-actions">
					<button class="d-btn cancel" @click="showTagMgr = false">{{ t('cancel') }}</button>
					<button class="d-btn ok" @click="addTag">{{ t('addTag') }}</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { listTask, createTask, updateTask, deleteTask, bindTaskTags, checkinTask, getTaskChildren } from '../../api/task.js'
	import { listTag, createTag, deleteTag } from '../../api/tag.js'

	// 新建标签时按顺序循环取色
	const TAG_COLORS = ['#FF6600', '#0066FF', '#2fb344', '#6b5ce7', '#f0a020']

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
				newTagName: '',
				form: { title: '', quadrant: null, estimatedPomodoros: null, dueDate: '', tagIds: [], repeatType: 'NONE' },
				repeatOptions: ['NONE', 'DAILY', 'WEEKLY'],
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
			openCreate() {
				this.form = { title: '', quadrant: null, estimatedPomodoros: null, dueDate: '', tagIds: [], repeatType: 'NONE' }
				this.showCreate = true
			},
			onDate(e) {
				this.form.dueDate = e.detail.value
			},
			toggleTag(list, id) {
				const i = list.indexOf(id)
				if (i >= 0) list.splice(i, 1)
				else list.push(id)
			},
			// 新建任务：先建任务拿到 id，再绑定选中的标签
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
						this.showCreate = false
						uni.showToast({ title: this.t('created'), icon: 'none' })
						this.load()
					})
					.catch(() => {})
			},
			// 单任务改标签：用已有 tags 预填选中项
			openTagPick(item) {
				this.pickTaskId = item.id
				this.pickTagIds = (item.tags || []).map((tg) => tg.id)
				this.showTagPick = true
			},
			saveTaskTags() {
				bindTaskTags(this.pickTaskId, this.pickTagIds)
					.then(() => {
						this.showTagPick = false
						uni.showToast({ title: this.t('saved'), icon: 'none' })
						this.load()
					})
					.catch(() => {})
			},
			openTagMgr() {
				this.newTagName = ''
				this.showTagMgr = true
			},
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
						this.load()
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
							.then(() => { this.load() })
							.catch(() => {})
					}
				})
			},
			done(item) {
				updateTask(item.id, { status: 2 })
					.then(() => { this.load() })
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
			repeatName(type) {
				const keys = { NONE: 'repeatNone', DAILY: 'repeatDaily', WEEKLY: 'repeatWeekly' }
				return keys[type] ? this.t(keys[type]) : ''
			},
			checkin(item) {
				checkinTask(item.id)
					.then(() => {
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
			},
			quadName(q) {
				const keys = { 1: 'quad1', 2: 'quad2', 3: 'quad3', 4: 'quad4' }
				return this.t(keys[q] || 'quad0')
			},
			quadColor(q) {
				return { 1: 'var(--danger)', 2: 'var(--primary)', 3: '#f0a020', 4: 'var(--text3)' }[q] || 'var(--text3)'
			},
			statusName(s) {
				const keys = { 0: 'status0', 1: 'status1', 2: 'status2', 3: 'status3' }
				return keys[s] ? this.t(keys[s]) : ''
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
	.empty {
		text-align: center;
		color: var(--text4);
		font-size: 28rpx;
		margin-top: 200rpx;
	}
	.mask {
		position: fixed;
		left: 0;
		top: 0;
		right: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.5);
		display: flex;
		align-items: center;
		justify-content: center;
		z-index: 10;
	}
	.dialog {
		width: 600rpx;
		background: var(--card);
		border-radius: 24rpx;
		padding: 40rpx;
	}
	.d-title {
		font-size: 34rpx;
		font-weight: bold;
		color: var(--text);
		display: block;
		margin-bottom: 30rpx;
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
	.d-actions {
		display: flex;
		gap: 20rpx;
	}
	.d-btn {
		flex: 1;
		border-radius: 16rpx;
		font-size: 30rpx;
	}
	.cancel {
		background: var(--fill);
		color: var(--text2);
	}
	.ok {
		background: var(--primary);
		color: var(--card);
	}
	.repeats {
		display: flex;
		gap: 16rpx;
		margin-bottom: 24rpx;
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
</style>
