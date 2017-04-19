package cz.spojenci.android.presenter

import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Status
import cz.spojenci.android.data.*
import cz.spojenci.android.utils.formatAsDateTime
import cz.spojenci.android.utils.formatAsPrice
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

	private val emptyChallenges: Observable<List<Challenge>> =
			Observable.create ({ emitter -> emitter.onNext(emptyList())}, Emitter.BackpressureMode.NONE)

	private val challengesFromRepo: Observable<List<Challenge>> = userService.observableUser
			.flatMap { user ->
				if (user != null) challengesRepo.challengesForUser(user.id) else emptyChallenges
			}

	private var challengesWithCache: Observable<ChallengesViewModel>? = null

	val challenges: Observable<ChallengesViewModel>
		get() {
			synchronized(this) {
				if (challengesWithCache == null) {
					challengesWithCache = challengesFromRepo
							.map { list ->
								val contributions =
										if (list.isNotEmpty()) {
											list.map { it.paid ?: BigDecimal.ZERO }.reduce { paid1, paid2 -> paid1 + paid2 }
										} else BigDecimal.ZERO
								val items = list.map { ChallengeItemModel.fromChallenge(it) }
								ChallengesViewModel(userService.user, items, contributions)
							}
							.replay(1)
							.autoConnect()
				}
				return challengesWithCache!!
			}
		}

	fun fitActivity(apiClient: GoogleApiClient): Observable<FitViewModel> = fitRepo.sessions(apiClient).map { (status, sessions) ->
		FitViewModel(status, sessions.map { FitItemModel.fromFitSession(it) })
	}

	fun clearChallengeCache() {
		synchronized(this) {
			challengesWithCache = null
		}
	}
}

data class ChallengesViewModel(val user: User?,
                               val challenges: List<ChallengeItemModel>,
                               val contributions: BigDecimal) {

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

data class ChallengeItemModel(val id: String, val name: String, val value: String) {

	companion object Factory {

		fun fromChallenge(item: Challenge): ChallengeItemModel {
			val value = item.activity_amount ?: "0"
			return ChallengeItemModel(item.id, item.name, "$value ${item.unit}")
		}
	}

}