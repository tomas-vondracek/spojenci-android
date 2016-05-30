package cz.spojenci.android.activity

import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Scope
import com.google.android.gms.fitness.Fitness
import cz.spojenci.android.BR
import cz.spojenci.android.R
import cz.spojenci.android.dagger.injectSelf
import cz.spojenci.android.data.FitSession
import cz.spojenci.android.data.IFitRepository
import cz.spojenci.android.databinding.ActivityMainBinding
import cz.spojenci.android.databinding.ItemFitActivityBinding
import cz.spojenci.android.databinding.ItemHeaderBinding
import cz.spojenci.android.pref.AppPreferences
import cz.spojenci.android.pref.UserPreferences
import cz.spojenci.android.utils.snackbar
import cz.spojenci.android.utils.visible
import timber.log.Timber
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

	@Inject lateinit var fitRepo: IFitRepository
	@Inject lateinit var appPrefs: AppPreferences
	@Inject lateinit var userPrefs: UserPreferences

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
						readFitData()
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
			connectFit()
		}
		binding.mainConnectAccount.setOnClickListener { LoginActivity.start(this)}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		Timber.d("Activity result: $resultCode for request $requestCode")
	}

	override fun onStart() {
		super.onStart()
		val isFitConnected = appPrefs.isFitConnected
		binding.mainConnectFit.visible = !isFitConnected
		binding.mainFitList.visible = isFitConnected

		if (isFitConnected) {
			connectFit()
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
		val user = userPrefs.user
		val isUserAvailable = user != null

		binding.mainUser.visible = isUserAvailable
		binding.mainConnectAccount.visible = ! isUserAvailable
		if (isUserAvailable) {
			binding.setVariable(BR.user, user)
		}
	}

	private fun connectFit() {
		binding.mainConnectFit.visible = false
		binding.mainFitProgress.visible = true

		apiClient.connect()
	}

	private fun readFitData() {
		fitRepo.sessions(apiClient)
				.subscribe({ result ->
					binding.mainFitProgress.visible = false
					if (!result.status.isSuccess) {
						Timber.i("no data from Fit: " + result.status)
						if (result.status.hasResolution()) {
							result.status.startResolutionForResult(this, 0)
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

class FitDataAdapter(private val context: Context,
                     private val fitSessions: List<FitSession>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	private val inflater = LayoutInflater.from(context)

	override fun getItemViewType(position: Int): Int {
		if (position == 0) {
			return R.layout.item_header
		} else {
			return R.layout.item_fit_activity
		}
	}

	override fun getItemCount(): Int {
		return fitSessions.size + 1 // + header
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val holder: RecyclerView.ViewHolder
		if (viewType == R.layout.item_fit_activity) {
			val binding = DataBindingUtil.inflate<ItemFitActivityBinding>(inflater, viewType, parent, false)
			holder = FitViewHolder(binding)
		} else if (viewType == R.layout.item_header){
			val binding = DataBindingUtil.inflate<ItemHeaderBinding>(inflater, viewType, parent, false)
			holder = TitleViewHolder(binding)
		} else {
			throw IllegalArgumentException("Unknown viewType " + viewType)
		}
		return holder
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		if (holder is FitViewHolder) {
			val session = fitSessions[position - 1]
			holder.binding.setVariable(BR.session, session)
		} else if (holder is TitleViewHolder) {
			holder.binding.itemTitle.setText(R.string.main_activity)
		}
	}

	class FitViewHolder(val binding: ItemFitActivityBinding) : RecyclerView.ViewHolder(binding.root)
	class TitleViewHolder(val binding: ItemHeaderBinding) : RecyclerView.ViewHolder(binding.root)
}
