package cz.spojenci.android.presenter

import android.app.Activity
import android.os.Parcel
import android.os.Parcelable
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Status
import cz.spojenci.android.activity.ChallengeDetailActivity
import cz.spojenci.android.activity.FitDetailActivity
import cz.spojenci.android.data.*
import cz.spojenci.android.data.local.FitActivityDatabase
import cz.spojenci.android.utils.formatAsDateTime
import cz.spojenci.android.utils.formatAsDistance
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
                                        private val db: FitActivityDatabase,
                                        private val userService: UserService): Presenter() {

	private val emptyChallenges: Observable<List<Challenge>> =
			Observable.create ({ emitter -> emitter.onNext(emptyList())}, Emitter.BackpressureMode.NONE)

	private var challengesWithCache: Observable<ChallengesViewModel>? = null

	val isUserSignedIn: Boolean
		get() = userService.isSignedIn

	private fun challengesFromRepo(forceRefresh: Boolean): Observable<List<Challenge>> = userService.observableUser
			.flatMap { user ->
				if (user != null) challengesRepo.challengesForUser(user.id, forceRefresh = forceRefresh) else emptyChallenges
			}


	fun challenges(forceRefresh: Boolean = false): Observable<ChallengesViewModel> {
		synchronized(this) {
			if (forceRefresh) {
				challengesWithCache = null
			}

			if (challengesWithCache == null) {
				challengesWithCache = challengesFromRepo(forceRefresh = forceRefresh)
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

	fun fitActivity(apiClient: GoogleApiClient): Observable<FitViewModel> {
		val attachedFitActivity = userService.observableUser.flatMap {
			db.fitActivityForUser(it?.id ?: "").map {
				it.map { it.fitActivityId } .toHashSet()
			}
		}

		val viewModels = fitRepo.sessions(apiClient).map { (status, sessions) ->
			FitViewModel(status, sessions.map { FitItemModel.fromFitSession(it) })
		}

		return Observable.zip(viewModels, attachedFitActivity) { viewModel, activities ->
			viewModel.items.forEach { item -> item.isAttached = activities.contains(item.id) }
			viewModel
		}
	}

	fun openFitDetail(activity: Activity, fitItem: FitItemModel, requestCode: Int) {
		FitDetailActivity.startForResult(activity, fitItem, requestCode)
	}

	fun openChallengeDetail(activity: Activity, challenge: ChallengeItemModel) {
		ChallengeDetailActivity.start(activity, challenge)
	}
}

data class ChallengesViewModel(val user: User?,
                               val challenges: List<ChallengeItemModel>,
                               val contributions: BigDecimal) {

	fun contributions(currency: String): String = contributions.formatAsPrice(currency)
}

data class FitItemModel(val id: String, val description: String, val time: String, val value: String,
                        var isAttached: Boolean = false) : Parcelable {

	companion object {
		fun fromFitSession(session: FitSession): FitItemModel {
			val distanceInKm = session.distanceValue / 1000F
			val value = "${distanceInKm.formatAsDistance()} km"
			return FitItemModel(session.id, session.description, session.timestamp.formatAsDateTime(), value)
		}

		@Suppress("unused")
		@JvmField val CREATOR: Parcelable.Creator<FitItemModel> = object : Parcelable.Creator<FitItemModel> {
			override fun createFromParcel(source: Parcel): FitItemModel = FitItemModel(source)
			override fun newArray(size: Int): Array<FitItemModel?> = arrayOfNulls(size)
		}
	}

	constructor(source: Parcel) : this(source.readString(), source.readString(), source.readString(), source.readString(), source.readInt() == 1)

	override fun describeContents() = 0

	override fun writeToParcel(dest: Parcel?, flags: Int) {
		dest?.writeString(id)
		dest?.writeString(description)
		dest?.writeString(time)
		dest?.writeString(value)
		dest?.writeInt(if (isAttached) 1 else 0)
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