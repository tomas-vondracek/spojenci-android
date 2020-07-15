package cz.spojenci.android.data

import com.franmontiel.persistentcookiejar.ClearableCookieJar
import cz.spojenci.android.data.remote.IUserEndpoint
import cz.spojenci.android.pref.UserPreferences
import cz.spojenci.android.utils.saveSessionIdCookie
import rx.Observable
import rx.subjects.BehaviorSubject
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

	var userLoginType: LoginType?
		get() = prefs.loginType

		private set(value) {
			prefs.loginType = value
		}

	val isSignedIn: Boolean
		get() = prefs.user != null

	var user: User?
		get() = prefs.user
		private set(value) {
			prefs.user = value
			userSubject.onNext(value)
		}

	private var userSubject = BehaviorSubject.create<User?>()

	val observableUser: Observable<User?> = userSubject.asObservable()

	init {
		userSubject.onNext(user)
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
				.doOnNext { response ->
					Timber.d("login response: $response")
				}

		return signIn(signInRequest, type)
	}

	private fun signIn(request: Observable<LoginResponse>, type: LoginType): Observable<User> {
		return request
				.doOnNext { response ->
					cookieJar.saveSessionIdCookie(response.sessionId)
				}
				.flatMap { endpoint.me() }
				.map { response -> response.user }
				.doOnNext { user ->
					if (user == null || user.id.isNullOrEmpty()) {
						throw IllegalArgumentException("illegal user $user")
					}

					this.user = user
					this.userLoginType = type
				}
	}

	fun signOut(): Observable<Void> {
		return endpoint.logout()
				.doOnError { ex -> Timber.w(ex, "Log out failed") }
				.onErrorResumeNext { Observable.empty() }
				.doOnCompleted { cleanUpPersistedData() }
	}

	fun updateUserProfile(): Observable<User> {
		return endpoint.me()
				.map { response -> response.user }
				.doOnNext { user ->
					if (user != null && !user.id.isNullOrEmpty()) {
						this.user = user
					}
				}
	}

	private fun cleanUpPersistedData() {
		user = null
		userLoginType = null
		prefs.clear()
		cookieJar.clear()
	}

}

