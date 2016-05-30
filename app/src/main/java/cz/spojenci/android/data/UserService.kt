package cz.spojenci.android.data

import rx.Observable

enum class LoginType {
	FACEBOOK,
	GOOGLE
}

interface IUserService {

	fun signInWithEmail(email: String, password: String): Observable<User>

	fun signInWithSocial(token: String, type: LoginType): Observable<User>

}

class UserService : IUserService {

	override fun signInWithEmail(email: String, password: String): Observable<User> {
		return Observable.error(NotImplementedError())
	}


	override fun signInWithSocial(token: String, type: LoginType): Observable<User> {
		return Observable.error(NotImplementedError())
	}
}

