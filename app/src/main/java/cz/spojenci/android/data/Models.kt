package cz.spojenci.android.data

import java.math.BigDecimal

data class User(val id: String, val loginType: String, val name: String, val surname: String, val email: String, val photo_url: String?)

data class UserRef(val user_id: String, val user_name: String)

data class Challenge(val id: String, val name: String, val unit: String, val to_pay: BigDecimal?, val paid: BigDecimal?,
                     val unit_price: BigDecimal?, val currency: String, val owner: UserRef, val done: String?, val activity_amount: String?)

data class ChallengeDetail(val id: String, val name: String, val unit: String, val to_pay: BigDecimal?, val paid: BigDecimal?,
                           val unit_price: BigDecimal?, val user: UserRef, val done: String?, val activity_amount: String,
                           val supporters: Array<UserRef>, val activities: Array<UserActivity>)

data class UserActivity(val type: String, val date: String?, val user: UserRef, val value: String?, val comment: String?)

data class ChallengeUpdate(val id: String, val type: String, val value: String?, val comment: String?)

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