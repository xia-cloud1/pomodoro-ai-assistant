<template>
	<view class="page">
		<view class="my-rank">{{ t('myRank', { n: data.myRank || '-' }) }}</view>
		<view v-for="r in data.list" :key="r.userId" :class="['row', r.isMe ? 'me' : '']">
			<text class="rk">{{ r.rank }}</text>
			<text class="nm">{{ r.nickname || r.username }}</text>
			<text class="lv">Lv.{{ r.level }}</text>
			<text class="pt">{{ r.points }}</text>
		</view>
		<view v-if="!data.list || data.list.length === 0" class="empty">{{ t('emptyRank') }}</view>
	</view>
</template>

<script>
	import { getRank } from '../../api/rank.js'

	export default {
		data() {
			return { data: {} }
		},
		onLoad() {
			getRank(20).then((d) => { this.data = d || {} }).catch(() => {})
		}
	}
</script>

<style>
	.page {
		min-height: 100vh;
		background: var(--bg);
		padding: 30rpx;
	}
	.my-rank {
		font-size: 28rpx;
		color: var(--primary);
		font-weight: bold;
		margin-bottom: 24rpx;
	}
	.row {
		display: flex;
		align-items: center;
		background: var(--card);
		border-radius: 16rpx;
		padding: 24rpx 28rpx;
		margin-bottom: 16rpx;
	}
	.row.me {
		border: 2rpx solid var(--primary);
	}
	.rk {
		width: 60rpx;
		font-size: 30rpx;
		font-weight: bold;
		color: var(--primary);
	}
	.nm {
		flex: 1;
		font-size: 30rpx;
		color: var(--text);
	}
	.lv {
		font-size: 24rpx;
		color: var(--text3);
		margin-right: 20rpx;
	}
	.pt {
		font-size: 30rpx;
		color: #f0a020;
		font-weight: bold;
	}
	.empty {
		text-align: center;
		color: var(--text4);
		margin-top: 200rpx;
	}
</style>
