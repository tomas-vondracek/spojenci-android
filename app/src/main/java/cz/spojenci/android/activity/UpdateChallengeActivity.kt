package cz.spojenci.android.activity

import android.app.Activity
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import android.widget.Toast
import com.avast.android.dialogs.fragment.SimpleDialogFragment
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crash.FirebaseCrash
import com.jakewharton.rxbinding.widget.textChanges
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import cz.spojenci.android.R
import cz.spojenci.android.dagger.injectSelf
import cz.spojenci.android.databinding.ActivityUpdateChallengeBinding
import cz.spojenci.android.presenter.CreateActivityForm
import cz.spojenci.android.presenter.SendActivityViewModel
import cz.spojenci.android.presenter.UpdateChallengePresentable
import cz.spojenci.android.presenter.UpdateChallengePresenter
import cz.spojenci.android.utils.visible
import cz.spojenci.android.utils.withSchedulers
import rx.Observable
import rx.lang.kotlin.subscribeBy
import timber.log.Timber
import javax.inject.Inject

class UpdateChallengeActivity : BaseActivity(), UpdateChallengePresentable {

	companion object {

		fun startFromChallengeForResult(context: Activity, challengeId: String, challengeUnit: String, challengeName: String, requestCode: Int) {

			val intent = Intent(context, UpdateChallengeActivity::class.java)
			intent.putExtra("CHALLENGE_ID", challengeId)
			intent.putExtra("CHALLENGE_UNIT", challengeUnit)
			intent.putExtra("CHALLENGE_NAME", challengeName)
			context.startActivityForResult(intent, requestCode)
		}
	}

	override val valueText: Observable<CharSequence>
		get() = binding.updateValue.textChanges()

	override val commentText: Observable<CharSequence>
		get() = binding.updateComment.textChanges()

	@Inject lateinit var presenter: UpdateChallengePresenter

	private lateinit var binding: ActivityUpdateChallengeBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		injectSelf()

		binding = DataBindingUtil.setContentView(this, R.layout.activity_update_challenge)
		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		binding.updateSend.setOnClickListener {
			sendUpdate()
		}

		val challengeId = intent.getStringExtra("CHALLENGE_ID")
		val unit = intent.getStringExtra("CHALLENGE_UNIT")
		val name = intent.getStringExtra("CHALLENGE_NAME")
		presenter.startFrom(view = this, challengeId = challengeId, unit = unit)
		supportActionBar?.title = getString(R.string.title_activity_update_challenge_with_name, name)
	}

	private fun sendUpdate() {
		presenter.sendActivity()
				.withSchedulers()
				.bindToLifecycle(this)
				.subscribeBy(
						onNext = { vm ->
							when (vm) {
								is SendActivityViewModel.Success -> {

									val bundle = Bundle()
									bundle.putString(FirebaseAnalytics.Param.ITEM_ID, vm.challengeId)
									bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, if (vm.containsValue) "value" else "comment")
									firebaseAnalytics.logEvent("update_challenge", bundle)

									Toast.makeText(this, R.string.update_challenge_sent, Toast.LENGTH_LONG).show()
									setResult(Activity.RESULT_OK)
									finish()
								}
								is SendActivityViewModel.InProgress -> {
									binding.updateErrorMessage.visible = false
									binding.updateSend.isEnabled = false
									binding.updateProgress.visible = true
									binding.updateValue.isEnabled = false
								}
								is SendActivityViewModel.Error -> {
									binding.updateSend.isEnabled = true
									binding.updateProgress.visible = false
									binding.updateValue.isEnabled = true

									binding.updateErrorMessage.visible = true
									binding.updateErrorMessage.text = vm.message

									FirebaseCrash.report(vm.cause)
								}
							}
						},
						onError = { ex ->
							Timber.e(ex, "failed to process the challenge update")
							FirebaseCrash.report(ex)
						})
	}

	override fun onDestroy() {
		super.onDestroy()
		presenter.stop()
	}


	override fun updateActivityForm(form: CreateActivityForm) {
		binding.form = form
	}

	override fun showValidationMessage() {
		SimpleDialogFragment.createBuilder(this, supportFragmentManager)
				.setMessage(R.string.update_challenge_activity_validation)
				.setPositiveButtonText(R.string.ok)
				.show()
	}
}
