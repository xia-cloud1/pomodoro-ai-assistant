<template>
	<view class="page">
		<view v-if="!img" class="empty">
			<text class="tip">{{ t('shareTip') }}</text>
			<button class="btn" @click="generate">{{ t('generatePoster') }}</button>
		</view>
		<template v-else>
			<image class="poster" :src="img" mode="widthFix" @click="preview" />
			<button class="btn" @click="preview">{{ t('previewSave') }}</button>
		</template>
	</view>
</template>

<script>
	import { generatePoster } from '../../api/share.js'

	export default {
		data() {
			return { img: '' }
		},
		methods: {
			generate() {
				uni.showLoading({ title: this.t('generating') })
				generatePoster()
					.then((base64) => { this.img = base64 })
					.catch(() => {})
					.finally(() => { uni.hideLoading() })
			},
			preview() {
				uni.previewImage({ urls: [this.img] })
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
	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		margin-top: 200rpx;
	}
	.tip {
		font-size: 28rpx;
		color: var(--text3);
		margin-bottom: 40rpx;
	}
	.btn {
		width: 400rpx;
		background: var(--primary);
		color: var(--card);
		border-radius: 16rpx;
		font-size: 30rpx;
		margin-top: 20rpx;
	}
	.poster {
		width: 100%;
		border-radius: 20rpx;
	}
</style>
