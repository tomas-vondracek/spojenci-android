package cz.spojenci.android

import android.app.Application
import android.util.Log
import com.google.firebase.crash.FirebaseCrash
import cz.spojenci.android.dagger.AppComponent
import cz.spojenci.android.dagger.DaggerAppComponent
import cz.spojenci.android.dagger.UiComponent
import timber.log.Timber



class Application : Application() {

	lateinit var appComponent: AppComponent
		private set
	lateinit var uiComponent: UiComponent
		private set

	override fun onCreate() {
		super.onCreate()

		Timber.plant(Timber.DebugTree())
		Timber.plant(CrashReportingTree())

		appComponent = DaggerAppComponent.builder()
				.appContext(this)
				.build()

		uiComponent = appComponent.uiComponent()
	}
}

private class CrashReportingTree : Timber.Tree() {

	override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
		if (priority == Log.VERBOSE ) {
			return
		}
		val logMessage = if (tag.isNullOrEmpty()) message else "$tag - $message"

		logMessage?.apply {
			FirebaseCrash.log(this)
		}
	}

}