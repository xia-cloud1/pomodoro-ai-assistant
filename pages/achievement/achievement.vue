<template>
	<view class="page">
		<view v-for="a in list" :key="a.id" :class="['card', a.unlocked ? 'unlocked' : 'locked']">
			<text class="icon">{{ a.icon || '🏆' }}</text>
			<view class="info">
				<text class="name">{{ a.name }}</text>
				<text class="desc">{{ a.description }}</text>
			</view>
			<text class="flag">{{ a.unlocked ? t('unlocked') : t('locked') }}</text>
		</view>
		<view v-if="list.length === 0" class="empty">{{ t('emptyAchievement') }}</view>
	</view>
</template>

<script>
	import { listAchievement } from '../../api/achievement.js'

	export default {
		data() {
			return { list: [] }
		},
		onLoad() {
			listAchievement().then((d) => { this.list = d || [] }).catch(() => {})
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
		display: flex;
		align-items: center;
		background: var(--card);
		border-radius: 20rpx;
		padding: 28rpx;
		margin-bottom: 20rpx;
	}
	.card.locked {
		opacity: 0.55;
	}
	.icon {
		font-size: 60rpx;
		margin-right: 24rpx;
	}
	.info {
		flex: 1;
		display: flex;
		flex-direction: column;
	}
	.name {
		font-size: 32rpx;
		font-weight: bold;
		color: var(--text);
	}
	.desc {
		font-size: 24rpx;
		color: var(--text3);
		margin-top: 8rpx;
	}
	.flag {
		font-size: 24rpx;
		color: var(--primary);
	}
	.empty {
		text-align: center;
		color: var(--text4);
		margin-top: 200rpx;
	}
</style>
