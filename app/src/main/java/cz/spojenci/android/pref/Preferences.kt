package cz.spojenci.android.pref

import android.content.Context
import android.content.SharedPreferences

abstract class Preferences(context: Context, name: String) {

	protected val pref = context.getSharedPreferences(name, Context.MODE_PRIVATE)

	fun SharedPreferences.edit(prefs: SharedPreferences.Editor.() -> Unit) {
		val editor = this.edit()
		prefs(editor)
		editor.apply()
	}

	fun SharedPreferences.Editor.setBoolean(pair: Pair<String, Boolean>) {
		putBoolean(pair.first, pair.second)
	}

	fun SharedPreferences.Editor.setString(pair: Pair<String, String?>) {
		putString(pair.first, pair.second)
	}

	fun clear() {
		pref.edit().clear().apply()
	}
}