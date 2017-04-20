package cz.spojenci.android.activity

import android.app.Activity
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.CookieSyncManager
import android.webkit.WebView
import android.webkit.WebViewClient
import cz.spojenci.android.R
import cz.spojenci.android.databinding.ActivityWebViewBinding
import cz.spojenci.android.utils.CookiePersistor
import timber.log.Timber



class WebViewActivity : BaseActivity() {

	companion object {

		fun start(context: Activity, url: String): Unit {

			val intent = Intent(context, WebViewActivity::class.java)
			intent.putExtra("URL", url)
			context.startActivity(intent)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val binding: ActivityWebViewBinding = DataBindingUtil.setContentView(this, R.layout.activity_web_view)
		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		val url: String? = intent.getStringExtra("URL")

		val webView = binding.webView
		webView.setWebViewClient(object : WebViewClient() {
			override fun onPageFinished(view: WebView?, url: String?) {
				super.onPageFinished(view, url)
				supportActionBar?.title = view?.title

			}
		})
		webView.settings.javaScriptEnabled = true

		setupCookies(url)

		url?.apply {
			Timber.d("Opening url $this")
			webView.loadUrl(this)
		}
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		if (item.itemId == android.R.id.home) {
			onBackPressed()
		}

		return super.onOptionsItemSelected(item)
	}

	private fun setupCookies(url: String?) {
		val cookies = CookiePersistor(this).loadAll()

		val cookieManager = CookieManager.getInstance()
		cookieManager.removeSessionCookie()

		cookies.forEach { cookie ->
			val cookieString = cookie.name() + "=" + cookie.value() + "; domain=" + cookie.domain()
			cookieManager.setCookie(url, cookieString)
		}
		CookieSyncManager.getInstance().sync()
	}
}
