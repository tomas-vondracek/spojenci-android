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

    private val challengesInMemory: MutableMap<String, List<Challenge>> = mutableMapOf()

    @Suppress("SENSELESS_COMPARISON")
    fun challengesForUser(userId: String, forceRefresh: Boolean = false): Observable<List<Challenge>> {
        val cachedChallenges: Observable<List<Challenge>>
        if (forceRefresh) {
            challengesInMemory.remove(userId)
            cachedChallenges = Observable.empty()
        } else {
            cachedChallenges = Observable.fromCallable { challengesInMemory[userId] }
                    .compose(logSource("MEMORY"))
        }

        val challengesOnServer = endpoint.challengesForUser(userId)
                .flatMap { Observable.from(it) }
                .filter { it != null && it.id != null && it.name != null }
                .toList()
                .compose(logSource("SERVER"))
                .doOnNext { challengesInMemory.put(userId, it) }

        return Observable.concat(cachedChallenges, challengesOnServer)
                .first { list -> list != null }
    }

    fun challengeDetail(challengeId: String): Observable<ChallengeDetail> =
            endpoint.challenge(challengeId)

    fun postChallengeActivity(challengeId: String, activityValue: String, comment: String): Observable<Void> {
	    val type = if (activityValue.isNotEmpty()) "SPORT" else "COMMENT"
	    return endpoint.challengeUpdate(ChallengeUpdate(challengeId, type, activityValue, comment))
    }
}