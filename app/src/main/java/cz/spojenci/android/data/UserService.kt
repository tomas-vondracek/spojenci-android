package cz.spojenci.android.data

import cz.spojenci.android.data.remote.IUserEndpoint
import rx.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class LoginType {
	FACEBOOK,
	GOOGLE
}

@Singleton
class UserService @Inject constructor(private val endpoint: IUserEndpoint) {

	fun signInWithEmail(email: String, password: String): Observable<User> {
		Timber.d("singing in with email %s", email)
		val signInRequest = endpoint.login(LoginRequest.account(email, password))

		return signIn(signInRequest)
	}


	fun signInWithSocial(token: String, type: LoginType): Observable<User> {
		Timber.d("singing in with token from %s", type)
		val signInRequest = endpoint.login(LoginRequest.social(token, type))

		return signIn(signInRequest)
	}

	private fun signIn(request: Observable<LoginResponse>): Observable<User> {
		return request
				.flatMap { endpoint.me() }
				.map { it.user }
	}

	// TODO logout
}

