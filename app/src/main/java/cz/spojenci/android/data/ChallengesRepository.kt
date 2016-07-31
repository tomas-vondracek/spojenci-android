package cz.spojenci.android.data

import cz.spojenci.android.data.remote.IChallengesEndpoint
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 07/06/16.
 */
@Singleton
class ChallengesRepository @Inject constructor(private val endpoint: IChallengesEndpoint) {

	fun challengesForUser(userId: String): Observable<List<Challenge>> = endpoint.challengesForUser(userId)

}