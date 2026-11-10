<template>
	<view v-if="show" class="mask" @click="onMaskClick">
		<view class="dialog" @click.stop>
			<text v-if="title" class="d-title">{{ title }}</text>
			<slot></slot>
			<view class="d-actions">
				<button v-if="cancelText" class="d-btn cancel" @click="$emit('close')">{{ cancelText }}</button>
				<button v-if="confirmText" :class="['d-btn', confirmType === 'danger' ? 'danger' : 'ok']"
					@click="$emit('confirm')">{{ confirmText }}</button>
			</view>
		</view>
	</view>
</template>

<script>
	/**
	 * 通用弹窗外壳：遮罩 + 居中卡片 + 标题 + 底部按钮。
	 * 内容放默认插槽，按钮文案由 cancelText / confirmText 控制（留空则不渲染该按钮），
	 * 破坏性操作（注销账号等）把 confirmType 设为 danger，确认按钮走红色。
	 * 样式自带，不依赖引用页面的样式（小程序的组件样式隔离下也能正常渲染）。
	 */
	export default {
		name: 'MaskDialog',
		props: {
			show: { type: Boolean, default: false },
			title: { type: String, default: '' },
			cancelText: { type: String, default: '' },
			confirmText: { type: String, default: '' },
			// 确认按钮语义：primary（默认）/ danger（注销、删除等破坏性操作）
			confirmType: { type: String, default: 'primary' },
			// 点遮罩是否关闭（少数弹窗要求必须点按钮）
			maskClosable: { type: Boolean, default: true }
		},
		emits: ['close', 'confirm'],
		methods: {
			onMaskClick() {
				if (this.maskClosable) this.$emit('close')
			}
		}
	}
</script>

<style>
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
	.d-actions {
		display: flex;
		gap: 20rpx;
		margin-top: 10rpx;
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
	.danger {
		background: var(--danger);
		color: var(--card);
	}
</style>