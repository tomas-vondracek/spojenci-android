package cz.spojenci.android.activity

import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.widget.Toast
import com.avast.android.dialogs.fragment.SimpleDialogFragment
import com.jakewharton.rxbinding.widget.textChanges
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import cz.spojenci.android.R
import cz.spojenci.android.dagger.injectSelf
import cz.spojenci.android.databinding.ActivityUpdateChallengeBinding
import cz.spojenci.android.presenter.CreateActivityForm
import cz.spojenci.android.presenter.UpdateChallengePresentable
import cz.spojenci.android.presenter.UpdateChallengePresenter
import cz.spojenci.android.utils.visible
import cz.spojenci.android.utils.withSchedulers
import rx.Observable
import rx.lang.kotlin.subscribeBy
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class UpdateChallengeActivity : BaseActivity(), UpdateChallengePresentable {

	companion object {

		fun startFromChallenge(context: Context, challengeId: String, challengeUnit: String) {

			val intent = Intent(context, UpdateChallengeActivity::class.java)
			intent.putExtra("CHALLENGE_ID", challengeId)
			intent.putExtra("CHALLENGE_UNIT", challengeUnit)
			context.startActivity(intent)
		}
	}

	override val valueText: Observable<CharSequence>
		get() = binding.updateValue.textChanges()


	@Inject lateinit var presenter: UpdateChallengePresenter

	private lateinit var binding: ActivityUpdateChallengeBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		injectSelf()

		binding = DataBindingUtil.setContentView(this, R.layout.activity_update_challenge)
		binding.updateSend.setOnClickListener {
			sendUpdate()
		}

		val challengeId = intent.getStringExtra("CHALLENGE_ID")
		val unit = intent.getStringExtra("CHALLENGE_UNIT")
		presenter.startFrom(view = this, challengeId = challengeId, unit = unit)
	}

	private fun sendUpdate() {
		presenter.sendActivity()
				.withSchedulers()
				.doOnSubscribe {
					binding.updateSend.isEnabled = false
					binding.updateProgress.visible = true
				}
				.doAfterTerminate {
					binding.updateSend.isEnabled = true
					binding.updateProgress.visible = false
				}
				.bindToLifecycle(this)
				.subscribeBy(
						onNext = {
							Toast.makeText(this, R.string.update_challenge_sent, Toast.LENGTH_LONG).show()
							finish()
						},
						onError = { ex ->
							Timber.e(ex, "failed to post the challenge update")

							val message = if (ex is IOException) R.string.error_internet else R.string.error_general
							SimpleDialogFragment.createBuilder(this, supportFragmentManager)
									.setMessage(message)
									.setPositiveButtonText(R.string.ok)
									.show()
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
