package cz.spojenci.android.data

import com.franmontiel.persistentcookiejar.ClearableCookieJar
import cz.spojenci.android.data.remote.IUserEndpoint
import cz.spojenci.android.pref.UserPreferences
import rx.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class LoginType {
	EMAIL,
	FACEBOOK,
	GOOGLE
}

@Singleton
class UserService @Inject constructor(private val endpoint: IUserEndpoint,
                                      private val prefs: UserPreferences,
                                      private val cookieJar: ClearableCookieJar) {

	val userLoginType: LoginType?
		get() = prefs.loginType

	val isSignedIn: Boolean
		get() {
			return prefs.user != null
		}

	val user: User?
		get() {
			return prefs.user
		}

	fun signInWithEmail(email: String, password: String): Observable<User> {
		Timber.d("singing in with email %s", email)
		val signInRequest = endpoint.login(LoginRequest.account(email, password))

		return signIn(signInRequest, LoginType.EMAIL)
	}


	fun signInWithSocial(token: String, type: LoginType): Observable<User> {
		Timber.d("singing in with token from %s", type)
		if (type == LoginType.EMAIL) {
			throw IllegalArgumentException("illegal login type $type for social login")
		}
		val signInRequest = endpoint.login(LoginRequest.social(token, type))

		return signIn(signInRequest, type)
	}

	private fun signIn(request: Observable<LoginResponse>, type: LoginType): Observable<User> {
		return request
				.flatMap { endpoint.me() }
				.map { it.user }
				.doOnNext { user ->
					if (user == null || user.id.isNullOrEmpty()) {
						throw IllegalArgumentException("illegal user $user")
					}

					prefs.user = user; prefs.loginType = type
				}
	}


	fun signOut(): Observable<Void> {
		return endpoint.logout()
				.doOnNext { prefs.clear(); cookieJar.clear() }
	}

}

