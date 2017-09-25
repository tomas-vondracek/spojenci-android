package cz.spojenci.android.presenter

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.support.customtabs.CustomTabsIntent
import android.support.v4.app.ActivityCompat
import cz.spojenci.android.R
import cz.spojenci.android.activity.UpdateChallengeActivity
import cz.spojenci.android.data.ChallengeDetail
import cz.spojenci.android.data.ChallengesRepository
import cz.spojenci.android.data.UserActivity
import cz.spojenci.android.data.UserService
import cz.spojenci.android.utils.formatAsDate
import cz.spojenci.android.utils.formatAsPrice
import cz.spojenci.android.utils.parseAsServerDate
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject



/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 25/03/17.
 */

interface ChallengeDetailPresentable {

}

class ChallengeDetailPresenter @Inject constructor(private val context: Context,
                                                   private val userService: UserService,
                                                   private val challengesRepo: ChallengesRepository): Presenter() {

	private var challengeDetail: ChallengeDetail? = null
	private lateinit var view: ChallengeDetailPresentable

	fun startFrom(view: ChallengeDetailPresentable) {
		this.view = view
	}

	fun challengeDetailFor(id: String, challengeName: String): Observable<ChallengeDetailViewModel> {
		return challengesRepo.challengeDetail(id)
				.observeOn(AndroidSchedulers.mainThread())
				.doOnNext { detail ->
					challengeDetail = detail
				}
				.map<ChallengeDetailViewModel> { detail ->
					val paid = detail.paid ?: BigDecimal.ZERO
					val toPay = (detail.to_pay ?: BigDecimal.ZERO) - paid
					val unitPrice = detail.unit_price ?: BigDecimal.ZERO
					val items = detail.activities
							.filter { ! it.isComment() }
							.map { UserActivityItemViewModel.fromDetail(detail, it) }

					ChallengeDetailViewModel.Success(detail.name,
							paid.formatAsPrice("CZK"),
							toPay.formatAsPrice("CZK"),
							unitPrice.formatAsPrice("CZK"),
							detail.unit,
							items)
				}
				.doOnError { ex -> Timber.e(ex, "Failed to load challenge detail") }
				.onErrorReturn { ex -> ChallengeDetailViewModel.Error(translateApiRequestError(context, ex)) }
				.startWith(ChallengeDetailViewModel.InProgress(challengeName))
	}

	fun createChallengeActivity(context: Activity, requestCode: Int) {
		challengeDetail?.let { (id, name, unit) ->
			UpdateChallengeActivity.startFromChallengeForResult(context, id, unit, name, requestCode)
		}
	}

	fun openPayment(activity: Activity) {
		challengeDetail?.let { detail ->

			val user = userService.user ?: return
			val identifier = detail.id
			val url = "https://www.darujme.cz/dar/index.php?template=darujme&page=checkout&currency=CZK&client=09121402" +
					"&project=98372636&payment_data____SKV_campaign_ID=$identifier&payment_data____var_symb=$identifier&transaction_type_id=2" +
					"&payment_data____jmeno=${user.name}&payment_data____prijmeni=${user.surname}&payment_data____email=${user.email}"

			val builder = CustomTabsIntent.Builder()
			builder.setToolbarColor(ActivityCompat.getColor(activity, R.color.colorPrimary))
			builder.setShowTitle(true)
			val customTabsIntent = builder.build()
			customTabsIntent.launchUrl(activity, Uri.parse(url))
		}
	}

}

sealed class ChallengeDetailViewModel {

	data class Success(val name: String,
	                   val attributions: String,
	                   val toPay: String,
	                   val unitPrice: String,
	                   val unitName: String,
	                   val activities: List<UserActivityItemViewModel>): ChallengeDetailViewModel() {


		val hasActivities: Boolean
			get() = activities.isNotEmpty()
	}

	data class InProgress(val name: String): ChallengeDetailViewModel()
	data class Error(val message: String): ChallengeDetailViewModel()
}


data class UserActivityItemViewModel(val date: String,
                                     val value: String,
                                     val money: String) {
	companion object Factory {
		fun fromDetail(detail: ChallengeDetail, activity: UserActivity): UserActivityItemViewModel {
			val date = activity.date?.parseAsServerDate()?.formatAsDate() ?: ""
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