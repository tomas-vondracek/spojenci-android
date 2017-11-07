package cz.spojenci.android.utils

import android.content.Context
import android.content.SharedPreferences
import com.franmontiel.persistentcookiejar.persistence.CookiePersistor
import com.franmontiel.persistentcookiejar.persistence.SerializableCookie
import okhttp3.Cookie
import timber.log.Timber

/**
 * Modified [[com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor]]
 *
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 24/03/17.
 */
class PreferencesCookiePersistor(private val sharedPreferences: SharedPreferences) : CookiePersistor {

	constructor(context: Context) : this(context.getSharedPreferences("Cookies", Context.MODE_PRIVATE))

	override fun loadAll(): List<Cookie> {
		val cookies = sharedPreferences.all.map { (_, value) ->
			val serializedCookie = value as String
			SerializableCookie().decode(serializedCookie)
		}
		return cookies
	}

	override fun saveAll(cookies: Collection<Cookie>) {
		Timber.d("Saving cookies $cookies")
		val editor = sharedPreferences.edit()
		cookies.forEach { cookie ->
			editor.putString(createCookieKey(cookie), SerializableCookie().encode(cookie))
		}
		editor.apply()
	}

	override fun removeAll(cookies: Collection<Cookie>) {
		val editor = sharedPreferences.edit()
		cookies.forEach { cookie -> editor.remove(createCookieKey(cookie)) }
		editor.apply()
	}

	private fun createCookieKey(cookie: Cookie): String {
		val protocol = if (cookie.secure()) "https" else "http"
		return "$protocol://${cookie.domain()}${cookie.path()}|${cookie.name()}"
	}

	override fun clear() {
		sharedPreferences.edit().clear().apply()
	}
}

fun CookiePersistor.findCookie(name: String): Cookie? = loadAll().find { cookie -> cookie.name() == name }