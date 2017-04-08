package cz.spojenci.android.activity

import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import cz.spojenci.android.R
import cz.spojenci.android.dagger.injectSelf
import cz.spojenci.android.databinding.ActivityUpdateChallengeBinding
import cz.spojenci.android.presenter.UpdateChallengePresenter
import javax.inject.Inject

class UpdateChallengeActivity : BaseActivity() {

	companion object {

		fun startFromChallenge(context: Context, challengeId: String) {

			val intent = Intent(context, UpdateChallengeActivity::class.java)
			intent.putExtra("CHALLENGE_ID", challengeId)
			context.startActivity(intent)
		}
	}

	@Inject lateinit var presenter: UpdateChallengePresenter

	private lateinit var binding: ActivityUpdateChallengeBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		injectSelf()

		binding = DataBindingUtil.setContentView(this, R.layout.activity_update_challenge)
	}
}
