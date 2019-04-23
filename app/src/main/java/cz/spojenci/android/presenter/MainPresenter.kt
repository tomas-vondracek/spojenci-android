package cz.spojenci.android.presenter

import android.app.Activity
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Status
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessActivities
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import cz.spojenci.android.R
import cz.spojenci.android.activity.ChallengeDetailActivity
import cz.spojenci.android.activity.FitDetailActivity
import cz.spojenci.android.data.*
import cz.spojenci.android.data.local.FitActivityDatabase
import cz.spojenci.android.utils.asObservable
import cz.spojenci.android.utils.formatAsDateTime
import cz.spojenci.android.utils.formatAsDistance
import cz.spojenci.android.utils.formatAsPrice
import rx.Emitter
import rx.Observable
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 31/03/17.
 */
@Singleton
class MainPresenter @Inject constructor(context: Context,
										private val challengesRepo: ChallengesRepository,
										private val fitRepo: IFitRepository,
										private val db: FitActivityDatabase,
										private val userService: UserService): Presenter(context) {

	private val fitnessOptions: FitnessOptions = FitnessOptions.builder()
			.addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
			.addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
			.build()

	private var fitDisconnectRequestIssued: Boolean = false

	private val emptyChallenges: Observable<List<Challenge>> =
			Observable.create ({ emitter -> emitter.onNext(emptyList())}, Emitter.BackpressureMode.NONE)

	private var challengesWithCache: Observable<ChallengesViewModel>? = null

	val isUserSignedIn: Boolean
		get() = userService.isSignedIn

	private fun challengesFromRepo(forceRefresh: Boolean): Observable<Pair<User?, List<Challenge>>> = userService.observableUser
			.flatMap { user ->
				val observable: Observable<List<Challenge>> =
						if (user != null) challengesRepo.challengesForUser(user.id, forceRefresh = forceRefresh)
						else emptyChallenges
				observable.map { list -> user to list}
			}


	fun challenges(forceRefresh: Boolean = false): Observable<ChallengesViewModel> {
		synchronized(this) {
			if (forceRefresh) {
				challengesWithCache = null
			}

			if (challengesWithCache == null) {
				challengesWithCache = challengesFromRepo(forceRefresh = forceRefresh)
						.map { (user, list) ->
							val contributions =
									if (list.isNotEmpty()) {
										list.map { it.paid ?: BigDecimal.ZERO }.reduce { paid1, paid2 -> paid1 + paid2 }
									} else BigDecimal.ZERO
							val items = list.map { ChallengeItemModel.fromChallenge(it) }
							ChallengesViewModel(user, items, contributions)
						}
						.replay(1)
						.autoConnect()
			}
			return challengesWithCache!!
		}
	}

	fun fitActivity(activity: Activity): Observable<FitViewModel> {
		if (!hasGoogleFitPermissions(activity)) {
			Timber.w("cannot retrieve fit activity, app doesn't have permissions")
			return Observable.empty()
		}

		val attachedFitActivity = userService.observableUser.flatMap {
			db.fitActivityForUser(it?.id ?: "").map {
				it.map { it.fitActivityId } .toHashSet()
			}
		}

		val viewModels = fitRepo.sessions(activity).map { (status, sessions) ->
			FitViewModel(status, sessions.map { FitItemModel.fromFitSession(it) })
		}

		return Observable.zip(viewModels, attachedFitActivity) { viewModel, activities ->
			viewModel.items.forEach { item -> item.isAttached = activities.contains(item.id) }
			viewModel
		}
	}

	fun hasGoogleFitPermissions(activity: Activity): Boolean {
		if (fitDisconnectRequestIssued) {
			// after call to Fitness.getConfigClient(..).disableFit()
			// GoogleSignIn.hasPermissions(..) still returns true, which is weird
			// and causes errors later on
			return false
		}
		val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(activity)
		return GoogleSignIn.hasPermissions(lastSignedInAccount, fitnessOptions)
	}

	/**
	 * Ask Play Services for Google Fit permissions. Result is delivered to activity with given request code
	 */
	fun requestGoogleFitPermissions(activity: Activity, requestCode: Int) {
		fitDisconnectRequestIssued = false

		val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(activity)
		GoogleSignIn.requestPermissions(activity, requestCode, lastSignedInAccount, fitnessOptions)
	}

	fun disconnectGoogleFit(activity: Activity): Observable<Void> {
		fitDisconnectRequestIssued = true

		val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(activity)
				?: return Observable.empty<Void>()

		return Fitness.getConfigClient(activity, lastSignedInAccount)
				.disableFit()
				.asObservable()
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
                               private val contributions: BigDecimal) {

	fun contributions(currency: String): String = contributions.formatAsPrice(currency)
}

data class FitItemModel(val id: String, val description: String, val time: String, val value: String, val iconId: Int,
                        var isAttached: Boolean = false) : Parcelable {

	companion object {
		fun fromFitSession(session: FitSession): FitItemModel {
			val distanceInKm = session.distanceValue / 1000F
			val value = "${distanceInKm.formatAsDistance()} km"
			val iconId = when (session.activityType) {
				FitnessActivities.RUNNING -> R.drawable.ic_running
				FitnessActivities.BIKING -> R.drawable.ic_biking
				else -> R.drawable.ic_flag
			}
			return FitItemModel(session.id, session.description, session.timestamp.formatAsDateTime(), value, iconId)
		}

		@Suppress("unused")
		@JvmField val CREATOR: Parcelable.Creator<FitItemModel> = object : Parcelable.Creator<FitItemModel> {
			override fun createFromParcel(source: Parcel): FitItemModel = FitItemModel(source)
			override fun newArray(size: Int): Array<FitItemModel?> = arrayOfNulls(size)
		}
	}

	constructor(source: Parcel) :
			this(source.readString(), source.readString(), source.readString(), source.readString(), source.readInt(), source.readInt() == 1)

	override fun describeContents() = 0

	override fun writeToParcel(dest: Parcel?, flags: Int) {
		dest?.writeString(id)
		dest?.writeString(description)
		dest?.writeString(time)
		dest?.writeString(value)
		dest?.writeInt(iconId)
		dest?.writeInt(if (isAttached) 1 else 0)
	}
}

data class FitViewModel(val status: Status, val items: List<FitItemModel>)

data class ChallengeItemModel(val id: String, val name: String, val value: String, val userId: String) {

	companion object Factory {

		fun fromChallenge(item: Challenge): ChallengeItemModel {
			val value = item.activity_amount ?: "0"
			return ChallengeItemModel(item.id, item.name, "$value ${item.unit}", item.owner.user_id)
		}
	}

}