package cz.spojenci.android.pref

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(context: Context) : Preferences(context, "app") {

	var isFitConnected: Boolean
		get() = pref.getBoolean("fit_connected", false)
		set(isConnected) = pref.edit { setBoolean ("fit_connected" to isConnected) }

	var isFirstRun: Boolean
		get() = pref.getBoolean("first_run", true)
		set(value) = pref.edit { setBoolean ("first_run" to value) }
}