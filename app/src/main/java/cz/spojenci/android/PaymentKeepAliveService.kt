package cz.spojenci.android

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import timber.log.Timber


class PaymentKeepAliveService : Service() {

	private val binder = LocalBinder()

	inner class LocalBinder : Binder() {
		internal val service: PaymentKeepAliveService
			get() = this@PaymentKeepAliveService
	}

	override fun onBind(intent: Intent): IBinder = binder

	override fun onDestroy() {
		super.onDestroy()
		Timber.d("Destroying payment service")
	}
}
