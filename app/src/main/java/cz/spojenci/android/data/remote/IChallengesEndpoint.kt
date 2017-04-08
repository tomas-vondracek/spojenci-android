package cz.spojenci.android.data.remote

import cz.spojenci.android.data.Challenge
import cz.spojenci.android.data.ChallengeDetail
import cz.spojenci.android.data.ChallengeUpdate
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import rx.Observable

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 31/07/16.
 */
interface IChallengesEndpoint {

	@GET("challenges/user/{userId}")
	fun challengesForUser(@Path("userId") userId: String): Observable<List<Challenge>>

	@GET("challenges/all")
	fun challengesForAll(): Observable<List<Challenge>>

	@GET("challenges/id/{id}")
	fun challenge(@Path("id") challengeId: String): Observable<ChallengeDetail>

	@POST("challenges/activity")
	fun challengeUpdate(update: ChallengeUpdate): Observable<Void>
}