<template>
	<view class="page">
		<view class="brand">{{ t('brand') }}</view>
		<view class="card">
			<view class="tabs">
				<view :class="['tab', mode === 'login' ? 'active' : '']" @click="mode = 'login'">{{ t('login') }}</view>
				<view :class="['tab', mode === 'register' ? 'active' : '']" @click="mode = 'register'">{{ t('register') }}</view>
			</view>
			<input class="input" v-model="username" :placeholder="t('username')" placeholder-class="ph" />
			<input class="input" v-model="password" password :placeholder="t('password')" placeholder-class="ph" />
			<button class="btn" :loading="loading" @click="submit">{{ mode === 'login' ? t('login') : t('register') }}</button>
		</view>
	</view>
</template>

<script>
	import { login, register } from '../../api/auth.js'

	export default {
		data() {
			return { mode: 'login', username: '', password: '', loading: false }
		},
		methods: {
			submit() {
				if (!this.username || !this.password) {
					uni.showToast({ title: this.t('needUserPass'), icon: 'none' })
					return
				}
				this.loading = true
				const fn = this.mode === 'login' ? login : register
				fn(this.username, this.password)
					.then((data) => {
						if (this.mode === 'register') {
							uni.showToast({ title: this.t('registerSuccess'), icon: 'none' })
							this.mode = 'login'
							this.password = ''
							return
						}
						uni.setStorageSync('accessToken', data.accessToken)
						uni.setStorageSync('refreshToken', data.refreshToken)
						uni.setStorageSync('user', JSON.stringify(data.user))
						uni.reLaunch({ url: '/pages/index/index' })
					})
					.catch(() => {})
					.finally(() => { this.loading = false })
			}
		}
	}
</script>

<style>
	.page {
		min-height: 100vh;
		background: var(--bg);
		display: flex;
		flex-direction: column;
		align-items: center;
		padding-top: 160rpx;
	}
	.brand {
		font-size: 40rpx;
		font-weight: bold;
		color: var(--text);
		margin-bottom: 60rpx;
	}
	.card {
		width: 600rpx;
		background: var(--card);
		border-radius: 24rpx;
		padding: 40rpx;
		box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.06);
	}
	.tabs {
		display: flex;
		margin-bottom: 40rpx;
	}
	.tab {
		flex: 1;
		text-align: center;
		font-size: 32rpx;
		color: var(--text3);
		padding: 16rpx 0;
		border-bottom: 4rpx solid transparent;
	}
	.tab.active {
		color: var(--primary);
		font-weight: bold;
		border-bottom-color: var(--primary);
	}
	.input {
		height: 88rpx;
		border: 1rpx solid var(--border);
		border-radius: 16rpx;
		padding: 0 24rpx;
		margin-bottom: 28rpx;
		font-size: 30rpx;
	}
	.ph {
		color: var(--text4);
	}
	.btn {
		margin-top: 20rpx;
		background: var(--primary);
		color: var(--card);
		border-radius: 16rpx;
		font-size: 32rpx;
	}
</style>
