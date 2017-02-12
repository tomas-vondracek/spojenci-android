package cz.spojenci.android.data

import cz.spojenci.android.data.remote.IChallengesEndpoint
import rx.Observable
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 07/06/16.
 */
@Singleton
class ChallengesRepository @Inject constructor(private val endpoint: IChallengesEndpoint) {

    @Suppress("SENSELESS_COMPARISON")
    fun challengesForUser(userId: String): Observable<List<Challenge>> =
            endpoint.challengesForUser(userId)
                    .flatMap { Observable.from(it) }
                    .map { challenge ->
                        val owner = UserRef(challenge.uid.toString(), "")
                        val paid = BigDecimal(challenge.prispevkycelkem)
                        val unit = challenge.performance ?: ""

                        Challenge(challenge.pid.toString(), challenge.title ?: "", unit, paid, owner, challenge.status
                                ?: "")
                    }
                    .filter { it != null && it.id != null && it.name != null }
                    .toList()

}