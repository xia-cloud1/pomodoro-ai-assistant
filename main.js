import App from './App'

// #ifndef VUE3
import Vue from 'vue'
import './uni.promisify.adaptor'
Vue.config.productionTip = false
App.mpType = 'app'
const app = new Vue({
	...App
})
app.$mount()
// #endif

// #ifdef VUE3
import {
	createSSRApp
} from 'vue'
import { t, syncNavTitle, syncTabBar } from './utils/i18n.js'
export function createApp() {
	const app = createSSRApp(App)
	app.mixin({
		methods: {
			t(key, params) {
				return t(key, params)
			}
		},
		onShow() {
			syncNavTitle()
			syncTabBar()
		}
	})
	return {
		app
	}
}
// #endif
