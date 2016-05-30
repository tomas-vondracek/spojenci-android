package cz.spojenci.android.pref

import android.content.Context
import com.google.gson.Gson
import cz.spojenci.android.data.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(private val context: Context) : Preferences(context, "user") {

	private val gson: Gson = Gson()

	var user: User?
		get() {

			val json: String? = pref.getString("user", null)
			return json?.let { gson.fromJson(it, User::class.java) }
		}
		set(user) {
			user?.let {
				val json = gson.toJson(it)
				pref.edit({ setString("user" to json) })
			}
		}

}