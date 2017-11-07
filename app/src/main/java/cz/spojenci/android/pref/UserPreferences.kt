package cz.spojenci.android.pref

import android.content.Context
import com.google.gson.Gson
import cz.spojenci.android.data.LoginType
import cz.spojenci.android.data.User
import javax.inject.Inject

class UserPreferences @Inject constructor(context: Context) : Preferences(context, "user") {

	private val gson: Gson = Gson()

	var user: User?
		get() {

			val json: String? = pref.getString("user", null)
			return json?.let { gson.fromJson(it, User::class.java) }
		}
		set(user) {
			if (user != null) {
				val json = gson.toJson(user)
				pref.edit { setString("user" to json) }
			} else {
				pref.edit { setString("user" to null) }
			}
		}

	var loginType: LoginType?
		get() {
			val typeString = pref.getString("loginType", null)
			if (! typeString.isNullOrEmpty()) {
				return LoginType.valueOf(typeString)
			}
			return null
		}
		set(value) {
			val typeString = value?.name
			pref.edit { setString("loginType" to typeString) }
		}

}