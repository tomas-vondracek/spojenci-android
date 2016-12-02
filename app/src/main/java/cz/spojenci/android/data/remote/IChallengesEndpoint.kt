package cz.spojenci.android.data.remote

import cz.spojenci.android.data.Challenge
import retrofit2.http.GET
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
}