package cz.spojenci.android.data

import java.math.BigDecimal
import java.math.BigInteger

data class User(val id: String, val loginType: String, val name: String, val email: String, val photoUrl: String?)

data class UserRef(val id: String, val name: String)

data class Challenge(val id: String, val name: String, val unit:String, val paid: BigDecimal,
					 val owner: UserRef, val done: String)

data class ChallengeDetail(val id: String, val name: String, val unit:String, val paid: BigInteger,
						   val user: UserRef, val activity: UserActivity)

data class UserActivity(val type: String, val date: String, val user: UserRef)

// responses:
data class UserResponse(val user: User)

data class LoginResponse(val newRegistration: Boolean)

// requests:

data class AccountLogin(val login: String, val password: String)
data class Social(val token: String, val type: String)
data class LoginRequest private constructor(val account: AccountLogin?, val social: Social?) {

	companion object {
		fun account(login: String, password: String): LoginRequest {
			return LoginRequest(AccountLogin(login, password), null)
		}

		fun social(token: String, type: LoginType): LoginRequest {
			return LoginRequest(null, Social(token, type.name))
		}
	}
}

data class ServerChallenge(
	var akce: String? = null,
	var challenge: String? = null,
	var challengeEmail: String? = null,
	var challengeName: String? = null,
	var deleted: Boolean = false,
	var description: String? = null,
	var florbal: Boolean = false,
	var hash: String? = null,
	var hidden: Boolean = false,
	var lyze: Boolean = false,
	var multiply: Int = 0,
	var onlymecanpay: Boolean = false,
	var orientacnizavod: Boolean = false,
	var originatingContact: Any? = null,
	var outdoor: Boolean = false,
	var performance: String? = null,
	var pid: Int = 0,
	var potapeni: Boolean = false,
	var prispevkycelkem: Int = 0,
	var sfId: Int = 0,
	var souhlas: Boolean = false,
	var status: String? = null,
	var stolnitenis: Boolean = false,
	var support: Any? = null,
	var supporting: Int = 0,
	var tanec: Boolean = false,
	var title: String? = null,
	var uid: Int = 0
)