package cz.spojenci.android.activity

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.databinding.DataBindingUtil
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.view.MenuItem
import android.webkit.*
import cz.spojenci.android.PaymentKeepAliveService
import cz.spojenci.android.R
import cz.spojenci.android.databinding.ActivityWebViewBinding
import cz.spojenci.android.utils.CookiePersistor
import cz.spojenci.android.utils.visible
import timber.log.Timber





class WebViewActivity : BaseActivity() {

	companion object {

		const val ACTION_CANCEL = "CANCEL"

		fun start(context: Activity, url: String) {

			val intent = Intent(context, WebViewActivity::class.java)
			intent.putExtra("URL", url)
			context.startActivity(intent)
		}
	}

	private val connection = object : ServiceConnection {
		// Called when the connection with the service is established
		override fun onServiceConnected(className: ComponentName, service: IBinder) {
			// Because we have bound to an explicit
			// service that is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			Timber.d("service connected")
			val binder = service as PaymentKeepAliveService.LocalBinder
			this@WebViewActivity.paymentService = binder.service

			binder.service.stopForeground(true)
		}

		// Called when the connection with the service disconnects unexpectedly
		override fun onServiceDisconnected(className: ComponentName) {
			Timber.d("service disconnected")
		}
	}

	private val serviceIntent by lazy { Intent(this, PaymentKeepAliveService::class.java) }
	private var paymentService: PaymentKeepAliveService? = null

	private lateinit var binding: ActivityWebViewBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = DataBindingUtil.setContentView(this, R.layout.activity_web_view)
		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		val url: String? = intent.getStringExtra("URL")

		binding.progressBar.max = 100

		val webView = binding.webView
		webView.webViewClient = object : WebViewClient() {
			override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
				binding.progressBar.visible = true
			}

			override fun onPageFinished(view: WebView?, url: String?) {
				super.onPageFinished(view, url)
				supportActionBar?.title = view?.title
				binding.progressBar.visible = false
			}
		}
		webView.webChromeClient = object : WebChromeClient() {

			override fun onProgressChanged(view: WebView?, newProgress: Int) {
				binding.progressBar.progress = newProgress
			}
		}
		webView.settings.javaScriptEnabled = true

		setupCookies(url)

		url?.apply {
			Timber.d("Opening url $this")
			webView.loadUrl(this)
		}

		startService(serviceIntent)
	}

	override fun onBackPressed() {
		if (binding.webView.canGoBack()) {
			binding.webView.goBack()
		} else {
			super.onBackPressed()
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		if (intent.action == ACTION_CANCEL) {
			stopService(serviceIntent)
			finish()
		}
	}

	override fun onStart() {
		super.onStart()
		bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
	}

	override fun onPause() {
		super.onPause()

		if (isFinishing) {
			stopService(serviceIntent)
		} else {
			setupNotificationChannel()

			// start service to stay alive until user returns
			val openActivityPendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
			val cancelIntent = Intent(this, WebViewActivity::class.java)
			cancelIntent.action = ACTION_CANCEL
			val cancelPendingIntent = PendingIntent.getActivity(this, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT)

			val notification = NotificationCompat.Builder(this, "payment")
					.setContentTitle(getText(R.string.payment_notification_title))
					.setContentText(getText(R.string.payment_notification_message))
					.setSmallIcon(R.drawable.ic_stat_payment)
					.setContentIntent(openActivityPendingIntent)
					.setTicker(getText(R.string.challenge_detail_pay))
					.addAction(R.drawable.ic_close, getString(R.string.cancel), cancelPendingIntent)
					.setChannelId("service")
					.build()
			paymentService?.startForeground(1, notification)
		}
	}

	override fun onStop() {
		super.onStop()
		unbindService(connection)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		if (item.itemId == android.R.id.home) {
			finish()
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

	private fun setupNotificationChannel() {
		if (Build.VERSION.SDK_INT < 26) {
			return
		}
		val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		if (notificationManager.getNotificationChannel("service") != null) {
			return
		}

		val channel = NotificationChannel("service","Foreground service",
				NotificationManager.IMPORTANCE_LOW)
		channel.description = "Foreground service channel"
		notificationManager.createNotificationChannel(channel)
	}
}
