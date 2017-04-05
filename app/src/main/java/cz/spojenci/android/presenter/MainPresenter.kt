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

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 31/03/17.
 */
class MainPresenter @Inject constructor(private val challengesRepo: ChallengesRepository,
                                        private val fitRepo: IFitRepository,
                                        private val userService: UserService) {

	private val emptyChallenges: Observable<List<Challenge>> = Observable.create ({ emitter -> emitter.onNext(emptyList())}, Emitter.BackpressureMode.NONE)

	private val challenges: Observable<List<Challenge>> = userService.observableUser
			.flatMap { user ->
				if (user != null) challengesRepo.challengesForUser(user.id) else emptyChallenges
			}

	val challengesViewModel: Observable<ChallengesViewModel> = challenges.map {
		list -> ChallengesViewModel(userService.user, list)
	}

	fun fitActivity(apiClient: GoogleApiClient): Observable<FitViewModel> = fitRepo.sessions(apiClient).map { (status, sessions) ->
		FitViewModel(status, sessions.map { FitItemModel.fromFitSession(it) })
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