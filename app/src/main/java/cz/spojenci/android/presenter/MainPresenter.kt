package cz.spojenci.android.presenter

import android.os.SystemClock
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Status
import cz.spojenci.android.data.*
import cz.spojenci.android.presenter.MainPresenter.Companion.DEFAULT_CACHE_TIME_MS
import cz.spojenci.android.data.CachableData
import cz.spojenci.android.utils.formatAsDateTime
import cz.spojenci.android.utils.formatAsPrice
import cz.spojenci.android.data.logSource
import rx.Emitter
import rx.Observable
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 31/03/17.
 */
@Singleton
class MainPresenter @Inject constructor(private val challengesRepo: ChallengesRepository,
                                        private val fitRepo: IFitRepository,
                                        private val userService: UserService) {

	companion object {
		val DEFAULT_CACHE_TIME_MS: Long = 1000*60*5
	}

	private val emptyChallenges: Observable<List<Challenge>> =
			Observable.create ({ emitter -> emitter.onNext(emptyList())}, Emitter.BackpressureMode.NONE)

	private var cachedChallenges: CachedChallenges? = null

	private val challengesFromRepo: Observable<List<Challenge>> = userService.observableUser
			.flatMap { user ->
				if (user != null) challengesRepo.challengesForUser(user.id) else emptyChallenges
			}.doOnNext { list ->
				cachedChallenges = CachedChallenges(challenges = list)
			}

	private val challengesFromMemory: Observable<List<Challenge>> =
			Observable.fromCallable { cachedChallenges }
					.compose(logSource<CachedChallenges?>("MEMORY"))
					.map { cachedChallenges -> cachedChallenges?.takeIf { it.isUpToDate() }?.challenges ?: emptyList() }

	val challenges: Observable<ChallengesViewModel> = Observable.concat(challengesFromMemory, challengesFromRepo)
			.first { list -> list.isNotEmpty() }
			.map {
				list ->
				ChallengesViewModel(userService.user, list)
			}

	fun fitActivity(apiClient: GoogleApiClient): Observable<FitViewModel> = fitRepo.sessions(apiClient).map { (status, sessions) ->
		FitViewModel(status, sessions.map { FitItemModel.fromFitSession(it) })
	}

	fun clearChallengeCache() {
		cachedChallenges = null
	}
}

data class CachedChallenges(val challenges: List<Challenge>, val maxAgeMs: Long = DEFAULT_CACHE_TIME_MS): CachableData {

	private val timeCreated = SystemClock.elapsedRealtime()

	override fun isUpToDate(): Boolean {
		return SystemClock.elapsedRealtime() - timeCreated < maxAgeMs
	}
}

data class ChallengesViewModel(val user: User?,
                               val challenges: List<Challenge>) {

	private val contributions: BigDecimal =
			if (challenges.isNotEmpty()) {
				challenges.map { it.paid ?: BigDecimal.ZERO }.reduce { paid1, paid2 -> paid1 + paid2 }
			} else BigDecimal.ZERO

	fun contributions(currency: String): String = contributions.formatAsPrice(currency)
}

data class FitItemModel(val id: String, val description: String, val time: String) {

	companion object Factory {
		fun fromFitSession(session: FitSession): FitItemModel {
			return FitItemModel(session.id, session.description, session.timestamp.formatAsDateTime())
		}
	}
}
data class FitViewModel(val status: Status, val items: List<FitItemModel>)