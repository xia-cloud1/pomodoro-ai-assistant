<template>
	<view class="page">
		<view class="card user">
			<text class="name">{{ user.username || t('user') }}</text>
			<text class="lv">Lv.{{ summary.level || 0 }}</text>
			<view class="progress-bg">
				<view class="progress" :style="{ width: (summary.progressPercent || 0) + '%' }"></view>
			</view>
			<text class="pts">{{ summary.points || 0 }} {{ t('pointsUnit') }} · {{ t('toNext') }} {{ summary.pointsToNextLevel || 0 }}</text>
		</view>

		<view class="card">
			<text class="sec-title">{{ t('checkinStreak', { n: checkin.streakDays || 0 }) }}</text>
			<button v-if="!checkin.checkedToday" class="btn-primary" @click="doCheckin">{{ t('checkinToday') }}</button>
			<view v-else class="checked">{{ t('checkedToday') }}</view>
			<picker mode="date" :end="today" @change="onMakeup">
				<view class="makeup">{{ t('makeupCheckin') }}</view>
			</picker>
		</view>

		<view class="card">
			<text class="sec-title">{{ t('energyTitle') }}</text>
			<view class="stars">
				<text v-for="n in 5" :key="n" :class="['star', energy.energyLevel >= n ? 'on' : '']" @click="energy.energyLevel = n">{{ n }}</text>
			</view>
			<input class="input" v-model="energy.mood" :placeholder="t('moodPlaceholder')" placeholder-class="ph" />
			<button class="btn-primary" @click="doSaveEnergy">{{ t('saveEnergy') }}</button>
		</view>

		<view class="card menu">
			<view class="menu-item" @click="go('/pages/rank/rank')"><text>{{ t('rank') }}</text><text class="arrow">›</text></view>
			<view class="menu-item" @click="go('/pages/ai/ai')"><text>{{ t('aiReport') }}</text><text class="arrow">›</text></view>
			<view class="menu-item" @click="go('/pages/achievement/achievement')"><text>{{ t('achievements') }}</text><text class="arrow">›</text></view>
			<view class="menu-item" @click="go('/pages/share/share')"><text>{{ t('sharePoster') }}</text><text class="arrow">›</text></view>
			<view class="menu-item" @click="doExport"><text>{{ t('exportData') }}</text><text class="arrow">›</text></view>
			<view class="menu-item"><text>{{ t('darkMode') }}</text><switch :checked="isDark" @change="onThemeChange" color="#6b5ce7" /></view>
			<view class="menu-item"><text>{{ t('language') }}</text><view class="lang-opts"><text :class="['lang-opt', lang === 'zh' ? 'on' : '']" @click="onLang('zh')">中文</text><text :class="['lang-opt', lang === 'en' ? 'on' : '']" @click="onLang('en')">English</text></view></view>
			<view class="menu-item danger" @click="doDelete"><text>{{ t('deleteAccount') }}</text><text class="arrow">›</text></view>
		</view>

		<button class="logout" @click="logout">{{ t('logoutBtn') }}</button>

		<!-- 注销确认弹窗 -->
		<view v-if="showDelete" class="mask" @click="showDelete = false">
			<view class="dialog" @click.stop>
				<text class="d-title">{{ t('deleteAccountTitle') }}</text>
				<text class="d-tip">{{ t('deleteAccountTip') }}</text>
				<input class="input" v-model="deletePwd" password :placeholder="t('deleteAccountPlaceholder')" placeholder-class="ph" />
				<view class="d-btns">
					<button class="d-cancel" @click="showDelete = false">{{ t('cancel') }}</button>
					<button class="d-ok" @click="confirmDelete">{{ t('confirmDelete') }}</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { pointsSummary } from '../../api/points.js'
	import { checkinStatus, checkin, makeupCheckin } from '../../api/checkin.js'
	import { saveEnergy, getEnergy } from '../../api/energy.js'
	import { deleteAccount, exportPersonalData } from '../../api/user.js'
	import { getTheme, setTheme } from '../../utils/theme.js'
	import { getLang, setLang } from '../../utils/i18n.js'
	import { clearApiCache } from '../../utils/apiCache.js'
	import { clearOfflineQueueForCurrentUser } from '../../utils/request.js'

	export default {
		data() {
			return { user: {}, summary: {}, checkin: {}, energy: { energyLevel: null, mood: '' }, showDelete: false, deletePwd: '', isDark: false, lang: '', today: '' }
		},
		onShow() {
			if (!uni.getStorageSync('accessToken')) {
				uni.reLaunch({ url: '/pages/login/login' })
				return
			}
			this.user = JSON.parse(uni.getStorageSync('user') || '{}')
			this.isDark = getTheme() === 'dark'
			this.lang = getLang()
			this.today = this.todayStr()
			this.load()
		},
		methods: {
			todayStr() {
				const d = new Date()
				const p = (n) => (n < 10 ? '0' + n : '' + n)
				return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate())
			},
			load() {
				pointsSummary().then((d) => { this.summary = d || {} }).catch(() => {})
				checkinStatus().then((d) => { this.checkin = d || {} }).catch(() => {})
				// 回显今天的精力自评，避免重进页面变空白后覆盖已有记录
				getEnergy().then((d) => {
					if (d) this.energy = { energyLevel: d.energyLevel || null, mood: d.mood || '' }
				}).catch(() => {})
			},
			doCheckin() {
				checkin().then((d) => {
					if (d && d.queued) {
						// 离线：乐观点亮今日打卡，联网后队列会自动重放
						this.checkin = {
							...(this.checkin || {}),
							checkedToday: true,
							streakDays: (this.checkin.streakDays || 0) + 1
						}
						uni.showToast({ title: this.t('offlineQueued'), icon: 'none' })
						return
					}
					uni.showToast({ title: this.t('checkinSuccess'), icon: 'none' })
					this.load()
				}).catch(() => {})
			},
			onMakeup(e) {
				const date = e.detail.value
				if (!date) return
				makeupCheckin(date).then((d) => {
					if (d && d.queued) {
						uni.showToast({ title: this.t('offlineQueued'), icon: 'none' })
						return
					}
					uni.showToast({ title: this.t('makeupDone'), icon: 'none' })
					this.load()
				}).catch(() => {})
			},
			doSaveEnergy() {
				if (!this.energy.energyLevel) {
					uni.showToast({ title: this.t('needEnergyLevel'), icon: 'none' })
					return
				}
				saveEnergy({ energyLevel: this.energy.energyLevel, mood: this.energy.mood })
					.then((d) => {
						uni.showToast({
							title: this.t(d && d.queued ? 'offlineQueued' : 'saved'),
							icon: 'none'
						})
					})
					.catch(() => {})
			},
			go(url) {
				uni.navigateTo({ url })
			},
			doExport() {
				exportPersonalData().then(() => {
					uni.showToast({ title: this.t('exported'), icon: 'none' })
				}).catch(() => {
					uni.showToast({ title: this.t('exportFailed'), icon: 'none' })
				})
			},
			onThemeChange(e) {
				this.isDark = e.detail.value
				setTheme(this.isDark ? 'dark' : 'light')
			},
			onLang(l) {
				if (this.lang === l) return
				this.lang = l
				setLang(l)
			},
			doDelete() {
				this.showDelete = true
				this.deletePwd = ''
			},
			confirmDelete() {
				if (!this.deletePwd) {
					uni.showToast({ title: this.t('needPassword'), icon: 'none' })
					return
				}
				deleteAccount(this.deletePwd).then(() => {
					// 账号已删除，待同步的队列不能再重放（否则只会 401）
					clearOfflineQueueForCurrentUser()
					uni.showToast({ title: this.t('accountDeleted'), icon: 'none' })
					setTimeout(() => this.logout(), 800)
				}).catch(() => {})
			},
			logout() {
				uni.removeStorageSync('accessToken')
				uni.removeStorageSync('refreshToken')
				uni.removeStorageSync('user')
				clearApiCache()
				uni.reLaunch({ url: '/pages/login/login' })
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
	.card {
		background: var(--card);
		border-radius: 20rpx;
		padding: 28rpx;
		margin-bottom: 24rpx;
	}
	.user {
		display: flex;
		flex-direction: column;
	}
	.name {
		font-size: 36rpx;
		font-weight: bold;
		color: var(--text);
	}
	.lv {
		font-size: 26rpx;
		color: var(--primary);
		margin-top: 8rpx;
	}
	.progress-bg {
		height: 16rpx;
		background: var(--fill);
		border-radius: 8rpx;
		margin-top: 16rpx;
		overflow: hidden;
	}
	.progress {
		height: 100%;
		background: var(--primary);
		border-radius: 8rpx;
	}
	.pts {
		font-size: 24rpx;
		color: var(--text3);
		margin-top: 12rpx;
	}
	.sec-title {
		font-size: 30rpx;
		font-weight: bold;
		color: var(--text);
		display: block;
		margin-bottom: 20rpx;
	}
	.btn-primary {
		background: var(--primary);
		color: var(--card);
		border-radius: 16rpx;
		font-size: 30rpx;
	}
	.checked {
		font-size: 30rpx;
		color: #2fb344;
	}
	.makeup {
		font-size: 26rpx;
		color: var(--primary);
		margin-top: 20rpx;
		text-align: center;
	}
	.stars {
		display: flex;
		gap: 24rpx;
		margin-bottom: 24rpx;
	}
	.star {
		width: 72rpx;
		height: 72rpx;
		line-height: 72rpx;
		text-align: center;
		border-radius: 50%;
		border: 2rpx solid var(--border);
		color: var(--text3);
		font-size: 30rpx;
	}
	.star.on {
		background: var(--primary);
		border-color: var(--primary);
		color: var(--card);
	}
	.input {
		height: 80rpx;
		border: 1rpx solid var(--border);
		border-radius: 16rpx;
		padding: 0 24rpx;
		margin-bottom: 20rpx;
		font-size: 28rpx;
	}
	.ph {
		color: var(--text4);
	}
	.menu-item {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 24rpx 0;
		border-bottom: 1rpx solid var(--border-light);
		font-size: 30rpx;
		color: var(--text);
	}
	.menu-item:last-child {
		border-bottom: none;
	}
	.arrow {
		color: var(--text3);
		font-size: 36rpx;
	}
	.lang-opts {
		display: flex;
		align-items: center;
	}
	.lang-opt {
		font-size: 26rpx;
		color: var(--text3);
		padding: 8rpx 20rpx;
		border-radius: 8rpx;
		margin-left: 16rpx;
	}
	.lang-opt.on {
		color: var(--primary);
		background: var(--primary-light);
	}
	.logout {
		margin-top: 20rpx;
		background: var(--card);
		color: var(--danger);
		border-radius: 16rpx;
		font-size: 30rpx;
	}
	.menu-item.danger {
		color: var(--danger);
	}
	.mask {
		position: fixed;
		top: 0;
		left: 0;
		right: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.5);
		display: flex;
		align-items: center;
		justify-content: center;
		z-index: 999;
	}
	.dialog {
		width: 80%;
		background: var(--card);
		border-radius: 20rpx;
		padding: 40rpx 32rpx;
	}
	.d-title {
		font-size: 34rpx;
		font-weight: bold;
		color: var(--text);
		display: block;
		text-align: center;
	}
	.d-tip {
		font-size: 26rpx;
		color: var(--text3);
		display: block;
		text-align: center;
		margin: 16rpx 0 28rpx;
	}
	.d-btns {
		display: flex;
		gap: 20rpx;
		margin-top: 8rpx;
	}
	.d-btns button {
		flex: 1;
		border-radius: 16rpx;
		font-size: 30rpx;
	}
	.d-cancel {
		background: var(--fill);
		color: var(--text2);
	}
	.d-ok {
		background: var(--danger);
		color: var(--card);
	}
</style>
