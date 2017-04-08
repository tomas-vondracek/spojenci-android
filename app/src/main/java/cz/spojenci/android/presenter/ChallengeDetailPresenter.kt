package cz.spojenci.android.presenter

import android.app.Activity
import cz.spojenci.android.activity.UpdateChallengeActivity
import cz.spojenci.android.data.ChallengesRepository
import cz.spojenci.android.data.UserActivity
import cz.spojenci.android.utils.formatAsPrice
import rx.Observable
import java.math.BigDecimal
import javax.inject.Inject

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 25/03/17.
 */
class ChallengeDetailPresenter @Inject constructor(private val challengesRepo: ChallengesRepository) {

	fun challengeDetailFor(id: String): Observable<ChallengeDetailViewModel> {
		return challengesRepo.challengeDetail(id)
				.map { detail ->
					val paid = detail.paid ?: BigDecimal.ZERO
					val unitPrice = detail.unit_price ?: BigDecimal.ZERO

					ChallengeDetailViewModel(detail.name,
							paid.formatAsPrice("CZK"),
							unitPrice.formatAsPrice("CZK"),
							detail.unit,
							detail.activities.asList())
				}
	}

	fun createChallengeActivity(challengeId: String, context: Activity) {
		UpdateChallengeActivity.startFromChallenge(context, challengeId)
	}

}

data class ChallengeDetailViewModel(val name: String,
                                    val attributions: String,
                                    val unitPrice: String,
                                    val unitName: String,
                                    val activities: List<UserActivity>) {

	val hasActivities: Boolean
	get() = activities.isNotEmpty()
}