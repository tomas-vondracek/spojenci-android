package cz.spojenci.android.activity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Scope
import com.google.android.gms.fitness.Fitness
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
		const val REQUEST_PERMISSION_LOCATION = 1
		const val REQUEST_FIT_RESOLUTION = 2
		const val REQUEST_FIT_DETAIL = 3
	}

	@Inject lateinit var appPrefs: AppPreferences
	@Inject lateinit var presenter: MainPresenter

	private val apiClient: GoogleApiClient by lazy {
		GoogleApiClient.Builder(this)
				.addApi(Fitness.SESSIONS_API)
				.addScope(Scope(Scopes.FITNESS_ACTIVITY_READ))
				.addScope(Scope(Scopes.FITNESS_LOCATION_READ))
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
		setSupportActionBar(binding.mainToolbar?.toolbar)

		binding.mainChallengesList.layoutManager = LinearLayoutManager(this)
		binding.mainChallengesList.adapter = adapter
		binding.mainChallengesList.itemAnimator = DefaultItemAnimator()
		binding.mainFitConnect?.fitConnect?.setOnClickListener {
			connectFitApiClient()
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

		if (requestCode == REQUEST_FIT_RESOLUTION && resultCode == RESULT_OK) {
			onFitAccessAvailable()
		} else if (requestCode == REQUEST_FIT_DETAIL && resultCode == Activity.RESULT_OK) {
			loadChallenges(forceRefresh = true)
			loadFitSessions()
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

	override fun onResume() {
		super.onResume()
		val isFitConnected = appPrefs.isFitConnected
		binding.mainFitConnect?.fitContainer?.visible = !isFitConnected && presenter.isUserSignedIn

		if (isFitConnected) {
			connectFitApiClient()
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

	override fun onOptionsItemSelected(item: MenuItem?): Boolean {
		when (item?.itemId) {
			R.id.menu_main_reload -> {
				loadChallenges(forceRefresh = true)
				return true
			}
			R.id.menu_main_about_project -> {
				val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.spojenci.cz"))
				startActivity(intent)
			}
			R.id.menu_main_about -> {
				LibsBuilder()
						.withAboutDescription("www.spojenci.cz")
						.withAboutVersionShown(true)
						.withAboutIconShown(true)
						.withActivityTitle(getString(R.string.title_about))
						.withActivityStyle(Libs.ActivityStyle.LIGHT_DARK_TOOLBAR)
						.start(this)
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

	private fun connectFitApiClient() {
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			binding.mainFitConnect?.apply {
				fitConnect.visible = false
				fitProgress.visible = true
			}
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
		loadFitSessions()
	}

	private fun loadFitSessions() {
		presenter.fitActivity(apiClient)
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
					snackbar("Failed to read data from Google Fit")
				})
	}

	private fun onFitAccessFailed(result: ConnectionResult) {
		val message: String = when {
			result.errorCode == ConnectionResult.CANCELED -> "Access to Google Fit canceled"
			else -> "Failed to access Google Fit: ${result.errorMessage}"
		}
		if (result.errorCode != ConnectionResult.CANCELED) {
			FirebaseCrash.report(Exception("failed to access Google Fit with result $result"))
		}

		binding.mainFitConnect?.apply {
			fitConnect.visible = true
			fitProgress.visible = false
		}
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

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
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