package cz.spojenci.android.presenter

import android.app.Activity
import cz.spojenci.android.activity.UpdateChallengeActivity
import cz.spojenci.android.data.ChallengeDetail
import cz.spojenci.android.data.ChallengesRepository
import cz.spojenci.android.data.UserActivity
import cz.spojenci.android.utils.formatAsDateTime
import cz.spojenci.android.utils.formatAsPrice
import cz.spojenci.android.utils.parseAsServerDate
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
					val items = detail.activities
							.filter { ! it.isComment() }
							.map { UserActivityItemViewModel.fromDetail(detail, it) }

					ChallengeDetailViewModel(detail.name,
							paid.formatAsPrice("CZK"),
							unitPrice.formatAsPrice("CZK"),
							detail.unit,
							items)
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
                                    val activities: List<UserActivityItemViewModel>) {

	val hasActivities: Boolean
	get() = activities.isNotEmpty()
}

data class UserActivityItemViewModel(val date: String,
                                     val value: String,
                                     val money: String) {
	companion object Factory {
		fun fromDetail(detail: ChallengeDetail, activity: UserActivity): UserActivityItemViewModel {
			val date = activity.date?.parseAsServerDate()?.formatAsDateTime() ?: ""
			val unitPrice = detail.unit_price ?: BigDecimal.ZERO
			val moneyToPay = BigDecimal(activity.value).multiply(unitPrice)
			val value = if (activity.isComment()) activity.value else "${activity.value} ${detail.unit}"

			return UserActivityItemViewModel(date, value ?: "", moneyToPay.formatAsPrice("CZK"))
		}
	}
}

private fun UserActivity.isComment(): Boolean {
	return this.type == "COMMENT"
}