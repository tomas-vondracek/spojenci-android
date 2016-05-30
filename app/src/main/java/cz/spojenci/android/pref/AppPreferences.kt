package cz.spojenci.android.pref

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(private val context: Context) : Preferences(context, "app") {

	var isFitConnected: Boolean
		get() = pref.getBoolean("fit_connected", false)
		set(isConnected) = pref.edit { setBoolean ("fit_connected" to isConnected) }

}