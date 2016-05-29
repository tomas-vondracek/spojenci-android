package cz.spojenci.android

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesProvider @Inject constructor(private val context: Context) {

	private val pref = context.getSharedPreferences("app", Context.MODE_PRIVATE)

	fun SharedPreferences.edit(prefs: SharedPreferences.Editor.() -> Unit) {
		val editor = this.edit()
		prefs(editor)
		editor.apply()
	}

	fun SharedPreferences.Editor.set(pair: Pair<String, Boolean>) {
		putBoolean(pair.first, pair.second)
	}

	var isFitConnected: Boolean
		get() = pref.getBoolean("fit_connected", false)
		set(value) = pref.edit { set ("fit_connected" to value) }

}