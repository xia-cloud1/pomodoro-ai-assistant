// 主题（暗黑模式）工具：H5 通过给 <html> 加 .dark 类切换 CSS 变量
const THEME_KEY = 'theme'

export function getTheme() {
	return uni.getStorageSync(THEME_KEY) || 'light'
}

export function applyTheme(theme) {
	// App/小程序端可用 theme.json 原生暗色，此处仅 H5 生效
	if (typeof document !== 'undefined' && document.documentElement) {
		document.documentElement.classList.toggle('dark', theme === 'dark')
	}
}

export function setTheme(theme) {
	uni.setStorageSync(THEME_KEY, theme)
	applyTheme(theme)
}