package cz.spojenci.android.data

data class User(val id: String, val loginType: String, val name: String, val email: String, val photoUrl: String)

data class UserRef(val id: String, val name: String)

data class Challenge(val id: String, val name: String, val owner: UserRef)

data class ChallengeDetail(val id: String, val name: String, val user: UserRef, val activity: UserActivity)

data class UserActivity(val type: String, val date: String, val user: UserRef)
