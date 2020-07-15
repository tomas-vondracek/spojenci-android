package cz.spojenci.android.utils

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.net.MalformedURLException

private val cookieUrl = HttpUrl.parse("http://www.spojenci.cz")
        ?: throw MalformedURLException("invalid cookie url")

val CookieJar.appSessionIdCookie: Cookie?
    get() = loadForRequest(cookieUrl).first { cookie -> cookie.name() == "session_id" }

fun CookieJar.saveSessionIdCookie(sessionId: String) {
    val cookie = Cookie.parse(cookieUrl,"session_id=${sessionId}; path=/; httponly")
    cookie?.let { saveFromResponse(cookieUrl, listOf(it)) }
}