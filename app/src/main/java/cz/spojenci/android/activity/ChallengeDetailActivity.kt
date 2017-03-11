package cz.spojenci.android.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v7.widget.Toolbar
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import cz.spojenci.android.R
import cz.spojenci.android.dagger.injectSelf
import cz.spojenci.android.data.Challenge
import cz.spojenci.android.data.ChallengeDetail
import cz.spojenci.android.data.ChallengesRepository
import cz.spojenci.android.utils.snackbar
import cz.spojenci.android.utils.withSchedulers
import rx.Observable
import timber.log.Timber
import javax.inject.Inject

class ChallengeDetailActivity : BaseActivity() {

	companion object {
		fun start(context: Context, challenge: Challenge) {

			val intent = Intent(context, ChallengeDetailActivity::class.java)
			intent.putExtra("CHALLENGE_ID", challenge.id)
			context.startActivity(intent)
		}
	}

	@Inject lateinit var challengesRepo: ChallengesRepository
	private lateinit var observableChallengeDetail: Observable<ChallengeDetail>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		injectSelf()

		setContentView(R.layout.activity_challenge_detail)
		val toolbar = findViewById(R.id.toolbar) as Toolbar
		setSupportActionBar(toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		val fab = findViewById(R.id.fab) as FloatingActionButton
		fab.setOnClickListener { view ->
			Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
					.setAction("Action", null).show()
		}

		val challengeId = intent.getStringExtra("CHALLENGE_ID")
		observableChallengeDetail = challengesRepo.challengeDetail(challengeId)
				.withSchedulers()
				.bindToLifecycle(this)
	}

	override fun onResume() {
		super.onResume()

		observableChallengeDetail
				.subscribe ({ challenge ->
					Timber.d("Loaded challenge detail: $challenge")
				}, { ex ->
					Timber.e(ex, "Failed to load challenge detail")
					snackbar("Failed to load challenge detail " + ex.message)
				})
	}
}
