package cz.spojenci.android.presenter

import android.content.Context
import cz.spojenci.android.data.ChallengesRepository
import cz.spojenci.android.data.UserService
import cz.spojenci.android.data.local.FitActivityDatabase
import rx.Observable
import timber.log.Timber
import java.sql.SQLException
import javax.inject.Inject

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 25/04/17.
 */
class FitDetailPresenter @Inject constructor(private val context: Context,
                                             private val challengesRepo: ChallengesRepository,
                                             private val db: FitActivityDatabase,
                                             private val userService: UserService): Presenter() {

	private val challenges: Observable<List<ChallengeItemModel>> = userService.observableUser
			.flatMap { user ->
				if (user != null) challengesRepo.challengesForUser(user.id) else Observable.empty()
			}.map { list ->
				list.map { ChallengeItemModel.fromChallenge(it) }
			}

	fun viewModel(fitActivityId: String): Observable<FitDetailViewModel> =
			Observable.zip(db.fitActivityById(fitActivityId), challenges) { dbRecords, challenges ->
				val attachedChallengeName: String =
						if (dbRecords.isNotEmpty()) {
							val challengeId = dbRecords.first().challengeId
							challenges.firstOrNull{ it.id == challengeId }?.name ?: ""
						} else { "" }

				FitDetailViewModel.challenges(challenges, attachedChallengeName.isNotEmpty(), attachedChallengeName)
			}
			.doOnError { Timber.e(it, "failed to load challenges into fit detail") }
			.onErrorReturn { FitDetailViewModel.error(translateApiRequestError(context, it)) }
			.startWith(FitDetailViewModel.inProgress())

	fun attachFitActivity(action: FitAttachAction): Observable<FitAttachViewModel> {

		return challengesRepo.postChallengeActivity(challengeId = action.challengeId, activityValue = action.fitValue)
				.map { FitAttachViewModel.success() }
				.doOnNext {
					try {
						val userId = userService.user?.id ?: ""
						val result = db.storeAttachedActivity(action, userId)
						Timber.d("saved fit activity $action in database for user $userId with result $result")
					} catch(e: SQLException) {
						Timber.e("failed to save $action in db")
					}
				}
				.doOnError { Timber.e(it, "failed to attach fit activity") }
				.onErrorReturn { FitAttachViewModel.error(translateApiRequestError(context, it)) }
				.startWith(FitAttachViewModel.attaching())
	}
}

data class FitDetailViewModel(val challenges: List<ChallengeItemModel>,
                              val isLoadingChallenges: Boolean = false,
                              val isActivityAttached: Boolean = false,
                              val attachedChallenge: String = "",
                              val errorMessage: String = "") {
	companion object {

		fun inProgress() = FitDetailViewModel(challenges = emptyList(), isLoadingChallenges = true)
		fun challenges(challenges: List<ChallengeItemModel>, activityAttached: Boolean, attachedChallenge: String) =
				FitDetailViewModel(challenges = challenges,
						isLoadingChallenges = false,
						isActivityAttached = activityAttached,
						attachedChallenge = attachedChallenge)
		fun error(message: String): FitDetailViewModel =
				FitDetailViewModel(challenges = emptyList(),
						isLoadingChallenges = false,
						isActivityAttached = false,
						errorMessage = message)
	}
}

data class FitAttachViewModel(val isAttaching: Boolean, val finished: Boolean, val error: String) {

	companion object {
		fun success(): FitAttachViewModel = FitAttachViewModel(false, true,  "")
		fun error(errorMessage: String): FitAttachViewModel = FitAttachViewModel(false, false, errorMessage)
		fun attaching(): FitAttachViewModel = FitAttachViewModel(true, false, "")

	}
}
data class FitAttachAction(val fitActivityId: String, val fitValue: String, val challengeId: String)