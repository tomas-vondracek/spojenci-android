package cz.spojenci.android

import cz.spojenci.android.utils.PreferencesCookiePersistor
import okhttp3.Cookie
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
@RunWith(RobolectricTestRunner::class)
@Config(constants = BuildConfig::class)
class PreferencesCookiePersistorTests {

	@Test
	@Throws(Exception::class)
	fun testLoadAll() {
		val persistor = PreferencesCookiePersistor(RuntimeEnvironment.application)
		val cookie = Cookie.parse(HttpUrl.parse("http://spojenci.cz"), "session_id=3e45668706606a2556c82615f93fac67; path=/; httponly")
		assertNotNull(cookie)

		val cookiesSet: Set<Cookie> = setOf(cookie!!)
		persistor.saveAll(cookiesSet)

		val newPersistor = PreferencesCookiePersistor(RuntimeEnvironment.application)
		val cookies = newPersistor.loadAll()
		assertEquals(1, cookies.size)
		assertEquals(cookie, cookies[0])
	}
}