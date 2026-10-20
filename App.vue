<script>
	import { getTheme, applyTheme } from './utils/theme.js'
	import { syncTabBar } from './utils/i18n.js'
	import { initOfflineQueue, flushOfflineQueue } from './utils/request.js'
	export default {
		onLaunch: function() {
			applyTheme(getTheme())
			syncTabBar()
			// 监听网络恢复；启动时若本地还有待同步的写操作，先尝试一次静默同步
			initOfflineQueue()
			flushOfflineQueue({ silent: true })
			console.log('App Launch')
		},
		onShow: function() {
			flushOfflineQueue({ silent: true })
			console.log('App Show')
		},
		onHide: function() {
			console.log('App Hide')
		}
	}
</script>

<style>
	/* 全局主题变量：亮色默认，html.dark 切换暗色 */
	:root {
		--bg: #f5f6fa;
		--card: #ffffff;
		--text: #333333;
		--text2: #666666;
		--text3: #999999;
		--text4: #bbbbbb;
		--border: #eeeeee;
		--border-light: #f5f5f5;
		--fill: #f0f0f0;
		--primary: #6b5ce7;
		--primary-light: #f8f7ff;
		--danger: #e05050;
	}
	:root.dark {
		--bg: #12121a;
		--card: #1e1e2c;
		--text: #e8e8ea;
		--text2: #b0b0b8;
		--text3: #78787f;
		--text4: #55555c;
		--border: #2a2a38;
		--border-light: #262632;
		--fill: #2a2a38;
		--primary: #8b7df0;
		--primary-light: #262236;
		--danger: #ff6b6b;
	}
	page {
		background-color: var(--bg);
		color: var(--text);
	}
</style>
