package cz.spojenci.android.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.fitness.FitnessStatusCodes
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crash.FirebaseCrash
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.LibsBuilder
import com.squareup.picasso.Picasso
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import cz.spojenci.android.BR
import cz.spojenci.android.R
import cz.spojenci.android.dagger.injectSelf
import cz.spojenci.android.data.User
import cz.spojenci.android.databinding.*
import cz.spojenci.android.pref.AppPreferences
import cz.spojenci.android.presenter.ChallengeItemModel
import cz.spojenci.android.presenter.FitItemModel
import cz.spojenci.android.presenter.MainPresenter
import cz.spojenci.android.presenter.Presenter
import cz.spojenci.android.utils.BoundViewHolder
import cz.spojenci.android.utils.snackbar
import cz.spojenci.android.utils.visible
import cz.spojenci.android.utils.withSchedulers
import rx.lang.kotlin.subscribeBy
import rx.subjects.PublishSubject
import timber.log.Timber
import javax.inject.Inject

class MainActivity : BaseActivity() {

	companion object {
		private const val REQUEST_PERMISSION_LOCATION = 1
		private const val REQUEST_FIT_RESOLUTION = 2
		private const val REQUEST_FIT_DETAIL = 3
		private const val REQUEST_GOOGLE_FIT_PERMISSIONS = 4
	}

	@Inject lateinit var appPrefs: AppPreferences
	@Inject lateinit var presenter: MainPresenter

	private val isGoogleFitConnected get() = appPrefs.isLegacyFitConnected && presenter.hasGoogleFitPermissions(this)

	private lateinit var binding: ActivityMainBinding
	private lateinit var adapter: CombinedDataAdapter

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		injectSelf()

		adapter = CombinedDataAdapter(this)
		binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
		setSupportActionBar(binding.mainToolbar?.toolbar)

		binding.mainChallengesList.layoutManager = LinearLayoutManager(this)
		binding.mainChallengesList.adapter = adapter
		binding.mainChallengesList.itemAnimator = DefaultItemAnimator()
		binding.mainFitConnect?.fitConnect?.setOnClickListener {
			connectFitApi()
		}

		adapter.challengeItemsClicks.bindToLifecycle(this).subscribe { challenge ->
			presenter.openChallengeDetail(this, challenge)
		}
		adapter.fitItemsClicks.bindToLifecycle(this).subscribe { fitItem ->
			presenter.openFitDetail(this, fitItem, REQUEST_FIT_DETAIL)
		}

		binding.mainConnectAccount.setOnClickListener { LoginActivity.start(this) }
		binding.mainUser.setOnClickListener { LoginActivity.start(this) }
		binding.emptyRetry.setOnClickListener {
			loadChallenges(forceRefresh = true)
		}

		if (appPrefs.isFirstRun) {
			appPrefs.isFirstRun = false
			LoginActivity.start(this)
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		Timber.d("Activity result: $resultCode for request $requestCode")

		if (resultCode == RESULT_OK) {
			when (requestCode) {
				REQUEST_FIT_RESOLUTION -> loadFitSessions()
				REQUEST_GOOGLE_FIT_PERMISSIONS -> {
					Timber.i("Google Fit permissions have been granted")
					FirebaseAnalytics.getInstance(this).logEvent("fit_connected", Bundle())
					appPrefs.isLegacyFitConnected = true
					connectFitApi()
				}
				REQUEST_FIT_DETAIL -> {
					loadChallenges(forceRefresh = true)
					loadFitSessions()
				}
			}
		} else {
			Timber.w("request $requestCode resulted with $resultCode")
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		if (requestCode == REQUEST_PERMISSION_LOCATION) {
			if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				Timber.d("location permission has been granted, connecting the Google Fit")
				connectFitApi()
			} else {
				snackbar(getString(R.string.main_permission_location_denied))
			}
		} else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		}
	}

	override fun onResume() {
		super.onResume()
		val isFitConnected = isGoogleFitConnected
		binding.mainFitConnect?.fitContainer?.visible = !isFitConnected && presenter.isUserSignedIn

		if (isFitConnected) {
			connectFitApi()
		} else if (appPrefs.isLegacyFitConnected) {
			// migrate from old Google Fit sign in to new API, should be done without user input
			connectFitApi()
		} else if (presenter.isUserSignedIn) {
			binding.mainFitConnect?.fitConnect?.visible = true
			// slide in
			val height = resources.getDimension(R.dimen.bottom_bar_height)
			binding.mainFitConnect?.apply {
				fitContainer.translationY = height
				fitContainer.animate()
						.setStartDelay(200)
						.translationY(0f)
						.withLayer()
			}
		}

		loadChallenges()
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.main, menu)
		return super.onCreateOptionsMenu(menu)
	}

	override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
		val disconnectFitItem = menu?.findItem(R.id.menu_main_disconnect_fit)
		disconnectFitItem?.isVisible = isGoogleFitConnected
		return super.onPrepareOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem?): Boolean {
		when (item?.itemId) {
			R.id.menu_main_reload -> {
				loadChallenges(forceRefresh = true)
				return true
			}
			R.id.menu_main_about_project -> {
				val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.spojenci.cz"))
				startActivity(intent)
				return true
			}
			R.id.menu_main_about -> {
				LibsBuilder()
						.withAboutDescription("www.spojenci.cz")
						.withAboutVersionShown(true)
						.withAboutIconShown(true)
						.withActivityTitle(getString(R.string.title_about))
						.withActivityStyle(Libs.ActivityStyle.LIGHT_DARK_TOOLBAR)
						.start(this)
				return true
			}
			R.id.menu_main_disconnect_fit -> {
				disconnectFitApi()

				return true
			}
		}
		return super.onOptionsItemSelected(item)
	}

	private fun loadChallenges(forceRefresh: Boolean = false) {
		presenter.challenges(forceRefresh)
				.withSchedulers()
				.bindToLifecycle(this)
				.doOnSubscribe {
					binding.mainChallengesProgress.visible = true
					binding.mainChallengesList.visible = false
				}
				.subscribeBy(onNext = { viewModel ->
					val user = viewModel.user
					updateUserUi(user)

					binding.mainUserContributions.text = viewModel.contributions("CZK")

					adapter.challenges = viewModel.challenges
					binding.mainChallengesProgress.visible = false
					binding.mainChallengesList.visible = viewModel.challenges.isNotEmpty() && user != null
					binding.emptyContainer.visible = viewModel.challenges.isEmpty() && user != null
					binding.emptyMessage.text = getString(R.string.main_challenges_empty)
					binding.mainChallengesLogo.visible = user == null

				}, onError = { ex ->
					Timber.e(ex, "Failed to load challenges")
					FirebaseCrash.report(Exception("failed to load challenges data", ex))

					snackbar(Presenter.translateApiRequestError(this, ex))
					binding.mainChallengesProgress.visible = false
					binding.mainChallengesList.visible = false
					binding.emptyMessage.text = Presenter.translateApiRequestError(this, ex)
					binding.emptyContainer.visible = true
				})
	}

	private fun updateUserUi(user: User?) {
		val isUserAvailable = user != null

		binding.mainUser.visible = isUserAvailable
		binding.mainConnectAccount.visible = !isUserAvailable

		binding.user = user

		val size = resources.getDimensionPixelSize(R.dimen.profile_photo_size)
		user?.photo_url?.let { url ->
			Picasso.with(this)
					.load(url)
					.noFade()
					.resize(size, size)
					.placeholder(R.drawable.user_silhouette)
					.into(binding.mainUserPhoto)
		}

	}

	private fun disconnectFitApi() {
		binding.mainFitConnect?.fitContainer?.visible = true
		binding.mainFitConnect?.fitProgress?.visible = true
		binding.mainFitConnect?.fitConnect?.visible = false
		adapter.fitItems = listOf()
		adapter.notifyDataSetChanged()

		presenter.disconnectGoogleFit(this)
				.bindToLifecycle(this)
				.subscribe({
					Timber.d("Disconnected Google Fit")
					FirebaseAnalytics.getInstance(this).logEvent("fit_disconnect", Bundle())
					appPrefs.isLegacyFitConnected = false
					binding.mainFitConnect?.fitConnect?.visible = true
					binding.mainFitConnect?.fitProgress?.visible = false
				}, { ex ->
					val message = "failed to disconnect from google fit"
					Timber.e(ex, message)
					FirebaseCrash.report(Exception(message, ex))

					appPrefs.isLegacyFitConnected = false
					binding.mainFitConnect?.fitConnect?.visible = true
					binding.mainFitConnect?.fitProgress?.visible = false
				})
	}

	private fun connectFitApi() {
		if (!checkLocationPermission()) {
			Timber.i("app doesn't have location permission")
			appPrefs.isLegacyFitConnected = false
			return
		}

		if (!presenter.hasGoogleFitPermissions(this)) {
			appPrefs.isLegacyFitConnected = false

			binding.mainFitConnect?.apply {
				fitConnect.visible = false
				fitProgress.visible = true
			}

			Timber.i("requesting google fit permission")
			FirebaseAnalytics.getInstance(this).logEvent("fit_request_permissions", Bundle())
			presenter.requestGoogleFitPermissions(this, REQUEST_GOOGLE_FIT_PERMISSIONS)
		} else {
			appPrefs.isLegacyFitConnected = true

			loadFitSessions()
		}

	}

	private fun checkLocationPermission(): Boolean {
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			binding.mainFitConnect?.apply {
				fitConnect.visible = false
				fitProgress.visible = true
			}
			return true
		} else {
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
				Toast.makeText(this, R.string.main_permission_location_rationale, Toast.LENGTH_LONG)
						.show()
			}
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_PERMISSION_LOCATION)
			return false
		}
	}

	private fun loadFitSessions() {
		presenter.fitActivity(this)
				.bindToLifecycle(this)
				.withSchedulers()
				.subscribe({ (status, items) ->
					binding.mainFitConnect?.apply {
						fitProgress.visible = false
						fitContainer.visible = false
					}
					if (!status.isSuccess) {
						Timber.i("no data from Fit: " + status)
						if (status.hasResolution()) {
							status.startResolutionForResult(this, REQUEST_FIT_RESOLUTION)
						}
					}
					Timber.d("Fit items: " + items)
					adapter.fitItems = items
					adapter.notifyDataSetChanged()
				}, { throwable ->
					binding.mainFitConnect?.fitProgress?.visible = false
					Timber.e(throwable, "failed to read from google fit")

					if (throwable is ResolvableApiException && throwable.statusCode == FitnessStatusCodes.NEEDS_OAUTH_PERMISSIONS) {
						try {
							throwable.startResolutionForResult(this, REQUEST_FIT_RESOLUTION)
						} catch (intentException: IntentSender.SendIntentException) {
							Timber.e(intentException, "failed to start Google Fit resolution")
							FirebaseCrash.report(Exception("failed to start Google Fit resolution", intentException))
						}
					} else if (throwable is ApiException && throwable.statusCode == CommonStatusCodes.SIGN_IN_REQUIRED) {
						FirebaseCrash.report(Exception("Sign in for Google Fit required", throwable))

						snackbar("You need to sign in to Google Fit")
					} else {
						FirebaseCrash.report(Exception("failed to fetch Google Fit data", throwable))

						snackbar("Failed to read data from Google Fit")
					}
				})
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

	private val fitClickSubject = PublishSubject.create<FitItemModel>()
	private val challengeClickSubject = PublishSubject.create<ChallengeItemModel>()

	var fitItems: List<FitItemModel> = emptyList()
	var challenges: List<ChallengeItemModel> = emptyList()

	val fitItemsClicks: rx.Observable<FitItemModel> = fitClickSubject.asObservable()
	val challengeItemsClicks: rx.Observable<ChallengeItemModel> = challengeClickSubject.asObservable()

	private val inflater = LayoutInflater.from(context)

	private val fitViewsCount: Int
		get() = Math.max(fitItems.size, 1) + 1

	private val challengesViewCount: Int
		get() = Math.max(challenges.size, 1) + 1

	private fun <B : ViewDataBinding> bindingForLayout(layoutId: Int, parent: ViewGroup): B {
		return DataBindingUtil.inflate(inflater, layoutId, parent, false)
	}

	override fun getItemCount(): Int = fitViewsCount + challengesViewCount

	override fun getItemViewType(position: Int): Int {
		return when {
			position == 0 ->
				// challenges header
				R.layout.item_header
			position < challengesViewCount ->
				if (challenges.isEmpty()) R.layout.item_empty else R.layout.item_challenge
			position == challengesViewCount ->
				// fit header
				R.layout.item_header
			fitItems.isEmpty() ->
				R.layout.item_empty
			else ->
				R.layout.item_fit_activity
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (holder) {
			is FitViewHolder -> {
				val fitItem = fitItems[position - 1 - challengesViewCount]
				holder.binding.item = fitItem
				holder.binding.itemActivityIcon.setImageResource(fitItem.iconId)
				holder.binding.itemActivityContainer.setOnClickListener {
					fitClickSubject.onNext(fitItem)
				}
			}
			is ChallengeViewHolder -> {
				val challenge = challenges[position - 1]
				holder.binding.setVariable(BR.challenge, challenge)
				holder.binding.itemChallengeContainer.setOnClickListener {
					challengeClickSubject.onNext(challenge)
				}
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