package cz.spojenci.android.data.remote

import cz.spojenci.android.data.*
import retrofit2.http.*
import rx.Observable

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 31/07/16.
 */
interface IChallengesEndpoint {

	@GET("challenges/user/{userId}")
	fun challengesForUser(@Path("userId") userId: String): Observable<ChallengesResponse>

	@GET("challenges/all")
	fun challengesForAll(): Observable<List<Challenge>>

	@GET("challenges/id/{id}")
	fun challenge(@Path("id") challengeId: String): Observable<ChallengeDetailResponse>

	@Headers("Content-Type: application/json")
	@POST("challenges/activity")
	fun challengeUpdate(@Body update: ChallengeUpdate): Observable<Void>
}