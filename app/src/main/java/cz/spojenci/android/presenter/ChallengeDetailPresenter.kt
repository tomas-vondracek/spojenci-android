package cz.spojenci.android.presenter

import android.app.Activity
import cz.spojenci.android.activity.UpdateChallengeActivity
import cz.spojenci.android.data.ChallengeDetail
import cz.spojenci.android.data.ChallengesRepository
import cz.spojenci.android.data.UserActivity
import cz.spojenci.android.utils.formatAsPrice
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.math.BigDecimal
import javax.inject.Inject

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 25/03/17.
 */

interface ChallengeDetailPresentable {

	fun enableCreateActivity()
}

class ChallengeDetailPresenter @Inject constructor(private val challengesRepo: ChallengesRepository) {

	private var challengeDetail: ChallengeDetail? = null
	private lateinit var view: ChallengeDetailPresentable

	fun startFrom(view: ChallengeDetailPresentable) {
		this.view = view
	}

	fun challengeDetailFor(id: String): Observable<ChallengeDetailViewModel> {
		return challengesRepo.challengeDetail(id)
				.observeOn(AndroidSchedulers.mainThread())
				.doOnNext { detail ->
					challengeDetail = detail
					view.enableCreateActivity()
				}
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

	fun createChallengeActivity(context: Activity) {
		challengeDetail?.let { detail ->
			UpdateChallengeActivity.startFromChallenge(context, detail.id, detail.unit)
		}
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