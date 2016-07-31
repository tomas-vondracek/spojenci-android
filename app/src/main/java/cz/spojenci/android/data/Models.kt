package cz.spojenci.android.data

data class User(val id: String, val loginType: String, val name: String, val email: String, val photoUrl: String?)

data class UserRef(val id: String, val name: String)

data class Challenge(val id: String, val name: String, val owner: UserRef, val done: String)

data class ChallengeDetail(val id: String, val name: String, val user: UserRef, val activity: UserActivity)

data class UserActivity(val type: String, val date: String, val user: UserRef)

// responses:
data class UserResponse(val user: User)

data class LoginResponse(val newRegistration: Boolean)

// requests:

data class AccountLogin(val login: String, val password: String)
data class SocialLogin(val token: String, val type: String)
data class LoginRequest private constructor(val account: AccountLogin?, val socialLogin: SocialLogin?) {

	companion object {
		fun account(login: String, password: String): LoginRequest {
			return LoginRequest(AccountLogin(login, password), null)
		}

		fun social(token: String, type: LoginType): LoginRequest {
			return LoginRequest(null, SocialLogin(token, type.name))
		}
	}
}