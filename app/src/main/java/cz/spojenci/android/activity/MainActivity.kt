package cz.spojenci.android.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Scope
import com.google.android.gms.fitness.Fitness
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import cz.spojenci.android.BR
import cz.spojenci.android.R
import cz.spojenci.android.dagger.injectSelf
import cz.spojenci.android.data.*
import cz.spojenci.android.databinding.*
import cz.spojenci.android.pref.AppPreferences
import cz.spojenci.android.utils.*
import timber.log.Timber
import javax.inject.Inject

class MainActivity : BaseActivity() {

	companion object {
		const val REQUEST_PERMISSION_LOCATION = 1
		const val REQUEST_FIT_RESOLUTION = 2
	}

	@Inject lateinit var fitRepo: IFitRepository
	@Inject lateinit var challengesRepo: ChallengesRepository
	@Inject lateinit var appPrefs: AppPreferences
	@Inject lateinit var userService: UserService

	private val apiClient: GoogleApiClient by lazy {
		GoogleApiClient.Builder(this)
				.addApi(Fitness.SESSIONS_API)
				.addScope(Scope(Scopes.FITNESS_ACTIVITY_READ))
				.addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
					override fun onConnectionSuspended(reason: Int) {
						appPrefs.isFitConnected = false
						Timber.d("Fit API suspended with reason $reason. FIT disconnected.")
					}

					override fun onConnected(bundle: Bundle?) {
						Timber.d("Fit API connected")
						appPrefs.isFitConnected = true
						onFitAccessAvailable()
					}
				})
				.enableAutoManage(this, 0, { result ->
					Timber.w("Google Play services connection failed. FIT disconnected. Cause: $result")
					appPrefs.isFitConnected = false
					onFitAccessFailed(result)
				})
				.build()
	}

	private lateinit var binding: ActivityMainBinding
	private lateinit var adapter: CombinedDataAdapter

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		injectSelf()

		adapter = CombinedDataAdapter(this)
		binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

		binding.mainChallengesList.layoutManager = LinearLayoutManager(this)
		binding.mainChallengesList.adapter = adapter
		binding.mainFitConnect.fitConnect.setOnClickListener { btn ->
			connectFitApiClient()
		}

		binding.mainConnectAccount.setOnClickListener { LoginActivity.start(this) }
		binding.mainUser.setOnClickListener { LoginActivity.start(this) }
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		Timber.d("Activity result: $resultCode for request $requestCode")

		if (requestCode == REQUEST_FIT_RESOLUTION && resultCode == RESULT_OK) {
			onFitAccessAvailable()
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		if (requestCode == REQUEST_PERMISSION_LOCATION) {
			if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				Timber.d("location permission has been granted, connecting the Google Fit")
				connectFitApiClient()
			} else {
				snackbar(getString(R.string.main_permission_location_denied))
			}
		} else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		}
	}

	override fun onStart() {
		super.onStart()
		val isFitConnected = appPrefs.isFitConnected
		binding.mainFitConnect.fitContainer.visible = !isFitConnected

		if (isFitConnected) {
			connectFitApiClient()
		} else {
			binding.mainFitConnect.fitConnect.visible = true
			// slide in
			val height = resources.getDimension(R.dimen.bottom_bar_height)
			binding.mainFitConnect.fitContainer.translationY = height
			binding.mainFitConnect.fitContainer.animate()
					.setStartDelay(200)
					.translationY(0f)
					.withLayer()
		}
	}

	override fun onResume() {
		super.onResume()
		updateUserUi()
	}

	override fun onStop() {
		super.onStop()
	}

	private fun updateUserUi() {
		val user = userService.user
		val isUserAvailable = user != null

		binding.mainUser.visible = isUserAvailable
		binding.mainConnectAccount.visible = !isUserAvailable
		if (isUserAvailable) {
			binding.setVariable(BR.user, user)

			binding.mainChallengesProgress.visible = true
			binding.mainChallengesList.visible = false
			challengesRepo.challengesForUser(user!!.id)
					.withSchedulers()
					.bindToLifecycle(this)
					.subscribe({ challenges ->
						val contributions = challenges.map { it.paid }
								.reduce { paid1, paid2 -> paid1 + paid2 }
						binding.mainUserContributions.text = contributions.formatAsPrice()

						adapter.challenges = challenges
						binding.mainChallengesProgress.visible = false
						binding.mainChallengesList.visible = true
					}, { ex ->
						Timber.e(ex, "Failed to load challenges")
						snackbar("Failed to load challenges " + ex.message)
						binding.mainChallengesProgress.visible = false
					})
		} else {
			// no user = no challenges, but we can show fit sessions
			binding.mainChallengesProgress.visible = false
			binding.mainChallengesList.visible = true
		}
	}

	private fun connectFitApiClient() {
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			binding.mainFitConnect.fitConnect.visible = false
			binding.mainFitConnect.fitProgress.visible = true
			apiClient.connect()
		} else {
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
				Toast.makeText(this, R.string.main_permission_location_rationale, Toast.LENGTH_LONG)
						.show()
			}
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_PERMISSION_LOCATION);
		}
	}

	private fun onFitAccessAvailable() {
		fitRepo.sessions(apiClient)
				.bindToLifecycle(this)
				.subscribe({ result ->
					binding.mainFitConnect.fitProgress.visible = false
					if (!result.status.isSuccess) {
						Timber.i("no data from Fit: " + result.status)
						if (result.status.hasResolution()) {
							result.status.startResolutionForResult(this, REQUEST_FIT_RESOLUTION)
						}
					}
					Timber.d("Fit sessions: " + result.sessions)
					adapter.fitItems = result.sessions
				}, { throwable ->
					binding.mainFitConnect.fitProgress.visible = false
					Timber.e(throwable, "failed to read from google fit")
					snackbar("Failed to read data from Google Fit")
				})
	}

	private fun onFitAccessFailed(result: ConnectionResult) {
		val message: String
		if (result.errorCode == ConnectionResult.CANCELED) {
			message = "Access to Google Fit canceled"
		} else {
			message = "Failed to access Google Fit: ${result.errorMessage}"
		}

		binding.mainFitConnect.fitConnect.visible = true
		binding.mainFitConnect.fitProgress.visible = false
		snackbar(message)
	}
}

class TitleViewHolder(binding: ItemHeaderBinding) : BoundViewHolder<ItemHeaderBinding>(binding)
class EmptyViewHolder(binding: ItemEmptyBinding) : BoundViewHolder<ItemEmptyBinding>(binding)
class FitViewHolder(binding: ItemFitActivityBinding) : BoundViewHolder<ItemFitActivityBinding>(binding)
class ChallengeViewHolder(binding: ItemChallengeBinding) : BoundViewHolder<ItemChallengeBinding>(binding)

/**
 * Adapter with challenges and fit data.
 */
class CombinedDataAdapter(context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	var fitItems: List<FitSession> = emptyList()
	var challenges: List<Challenge> = emptyList()

	private val inflater = LayoutInflater.from(context)

	private val fitViewsCount: Int
		get() {
			return Math.max(fitItems.size, 1) + 1
		}

	private val challengesViewCount: Int
		get() {
			return Math.max(challenges.size, 1) + 1
		}


	private fun <B : ViewDataBinding> bindingForLayout(layoutId: Int, parent: ViewGroup): B {
		return DataBindingUtil.inflate(inflater, layoutId, parent, false)
	}

	override fun getItemCount(): Int {
		return fitViewsCount + challengesViewCount
	}

	override fun getItemViewType(position: Int): Int {
		when {
			position == 0 ->
				// challenges header
				return R.layout.item_header
			position < challengesViewCount ->
				return if (challenges.isEmpty()) R.layout.item_empty else R.layout.item_challenge
			position == challengesViewCount ->
				// fit header
				return R.layout.item_header
			else ->
				return if (fitItems.isEmpty()) R.layout.item_empty else R.layout.item_fit_activity
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
		when (holder) {
            is FitViewHolder -> {
				val session = fitItems[position - 1 - challengesViewCount]
				holder.binding.setVariable(BR.session, session)
			}
			is ChallengeViewHolder -> {
				val challenge = challenges[position - 1]
				holder.binding.setVariable(BR.challenge, challenge)
			}
			is TitleViewHolder -> {
				val textId =
						if (position >= challengesViewCount) R.string.main_activity
						else R.string.main_challenges
				holder.binding.itemTitle.setText(textId)
			}
			is EmptyViewHolder -> {
				val textId =
						if (position > challengesViewCount) R.string.main_activity_empty
						else R.string.main_challenges_empty
				holder.binding.itemTitle.setText(textId)
			}
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return when (viewType) {
			R.layout.item_challenge -> ChallengeViewHolder(bindingForLayout(viewType, parent))
			R.layout.item_fit_activity -> FitViewHolder(bindingForLayout(viewType, parent))
			R.layout.item_header -> TitleViewHolder(bindingForLayout(viewType, parent))
			R.layout.item_empty -> EmptyViewHolder(bindingForLayout(viewType, parent))
			else -> throw IllegalArgumentException("Unknown viewType " + viewType)
		}
	}
}