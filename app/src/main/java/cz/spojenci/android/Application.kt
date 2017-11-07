package cz.spojenci.android

import android.app.Application
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

		appComponent = DaggerAppComponent.builder()
				.appContext(this)
				.build()

		uiComponent = appComponent.uiComponent()
	}
}