package cz.spojenci.android.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
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
import cz.spojenci.android.databinding.ActivityMainBinding
import cz.spojenci.android.databinding.ItemChallengeBinding
import cz.spojenci.android.databinding.ItemFitActivityBinding
import cz.spojenci.android.databinding.ItemHeaderBinding
import cz.spojenci.android.pref.AppPreferences
import cz.spojenci.android.utils.RecyclerAdapter
import cz.spojenci.android.utils.snackbar
import cz.spojenci.android.utils.visible
import cz.spojenci.android.utils.withSchedulers
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
						Timber.d("Fit API suspended with reason $reason")
					}

					override fun onConnected(bundle: Bundle?) {
						Timber.d("Fit API connected")
						appPrefs.isFitConnected = true
						onFitAccessAvailable()
					}
				})
				.enableAutoManage(this, 0, { result ->
					Timber.w("Google Play services connection failed. Cause: ${result.toString()}");
					onFitAccessFailed(result)
				})
				.build()
	}

	private lateinit var binding: ActivityMainBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		injectSelf()

		binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

		binding.mainFitList.layoutManager = LinearLayoutManager(this)
		binding.mainConnectFit.setOnClickListener { btn ->
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
			if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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
		binding.mainConnectFit.visible = !isFitConnected
		binding.mainFitList.visible = isFitConnected

		if (isFitConnected) {
			connectFitApiClient()
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
						binding.mainChallengesList.adapter = ChallengesAdapter(this, challenges)
						binding.mainChallengesProgress.visible = false
						binding.mainChallengesList.visible = true
					}, { ex ->
						Timber.e(ex, "Failed to load challenges")
						snackbar("Failed to load challenges " + ex.message)
						binding.mainChallengesProgress.visible = false
					})
		}
	}

	private fun connectFitApiClient() {
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			binding.mainConnectFit.visible = false
			binding.mainFitProgress.visible = true
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
					binding.mainFitProgress.visible = false
					if (!result.status.isSuccess) {
						Timber.i("no data from Fit: " + result.status)
						if (result.status.hasResolution()) {
							result.status.startResolutionForResult(this, REQUEST_FIT_RESOLUTION)
						}
					}
					Timber.d("Fit sessions: " + result.sessions)
					binding.mainFitList.visible = true
					binding.mainFitList.adapter = FitDataAdapter(this, result.sessions)
				}, { throwable ->
					binding.mainFitProgress.visible = false
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

		appPrefs.isFitConnected = false
		binding.mainConnectFit.visible = true
		binding.mainFitProgress.visible = false
		snackbar(message)
	}
}

class FitDataAdapter(context: Context, fitSessions: List<FitSession>) :
		RecyclerAdapter<RecyclerView.ViewHolder, FitSession>(context, fitSessions) {

	override fun getItemViewType(position: Int): Int {
		if (position == 0) {
			return R.layout.item_header
		} else {
			return R.layout.item_fit_activity
		}
	}

	override fun getItemCount(): Int {
		return super.getItemCount() + 1 // + header
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return when (viewType) {
			R.layout.item_fit_activity -> FitViewHolder(bindingForLayout(viewType, parent))
			R.layout.item_header -> TitleViewHolder(bindingForLayout(viewType, parent))
			else -> throw IllegalArgumentException("Unknown viewType " + viewType)
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		if (holder is FitViewHolder) {
			val session = getItem(position - 1)
			holder.binding.setVariable(BR.session, session)
		} else if (holder is TitleViewHolder) {
			holder.binding.itemTitle.setText(R.string.main_activity)
		}
	}

	class FitViewHolder(binding: ItemFitActivityBinding) : BoundViewHolder<ItemFitActivityBinding>(binding)
	class TitleViewHolder(binding: ItemHeaderBinding) : BoundViewHolder<ItemHeaderBinding>(binding)
}


class ChallengesAdapter(context: Context, challenges: List<Challenge>) :
		RecyclerAdapter<ChallengesAdapter.ChallengeViewHolder, Challenge>(context, challenges) {

	override fun onBindViewHolder(holder: ChallengeViewHolder, position: Int) {
		holder.binding.setVariable(BR.challenge, getItem(position))
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChallengeViewHolder {
		return ChallengeViewHolder(bindingForLayout(R.layout.item_challenge, parent))
	}

	class ChallengeViewHolder(binding: ItemChallengeBinding) : BoundViewHolder<ItemChallengeBinding>(binding)
}
