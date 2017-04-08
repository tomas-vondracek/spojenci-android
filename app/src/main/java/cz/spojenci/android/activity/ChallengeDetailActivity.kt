package cz.spojenci.android.activity

import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.ViewGroup
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import cz.spojenci.android.R
import cz.spojenci.android.dagger.injectSelf
import cz.spojenci.android.data.Challenge
import cz.spojenci.android.data.UserActivity
import cz.spojenci.android.databinding.ActivityChallengeDetailBinding
import cz.spojenci.android.databinding.ContentChallengeDetailBinding
import cz.spojenci.android.databinding.ItemChallengeActivityBinding
import cz.spojenci.android.presenter.ChallengeDetailPresenter
import cz.spojenci.android.presenter.ChallengeDetailViewModel
import cz.spojenci.android.utils.*
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

	@Inject lateinit var presenter: ChallengeDetailPresenter
	private lateinit var observableChallengeDetail: Observable<ChallengeDetailViewModel>
	private lateinit var binding: ActivityChallengeDetailBinding

	private val contentBinding: ContentChallengeDetailBinding
		get() = binding.challengeDetailContent

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		injectSelf()

		binding = DataBindingUtil.setContentView(this, R.layout.activity_challenge_detail)
		contentBinding.challengeDetailList.layoutManager = LinearLayoutManager(this)
		contentBinding.challengeDetailList.adapter = ChallengeActivityAdapter(this, emptyList())
		contentBinding.hasActivities = true

		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		val challengeId = intent.getStringExtra("CHALLENGE_ID")
		binding.fab.setOnClickListener {
			presenter.createChallengeActivity(context = this, challengeId = challengeId)
		}

		observableChallengeDetail = presenter.challengeDetailFor(challengeId)
				.withSchedulers()
				.doOnSubscribe { contentBinding.challengeDetailProgress.visible = true }
				.doAfterTerminate { contentBinding.challengeDetailProgress.visible = false }
				.bindToLifecycle(this)
	}

	override fun onStart() {
		super.onStart()

		loadChallengeDetail()
	}

	private fun loadChallengeDetail() {
		observableChallengeDetail
				.subscribe({ viewModel ->
					Timber.d("Loaded challenge detail: $viewModel on thread ${Thread.currentThread().name}")
					val items = viewModel.activities

					val list = contentBinding.challengeDetailList
					val adapter = ChallengeActivityAdapter(this, items)
					list.adapter = adapter

					contentBinding.hasActivities = viewModel.hasActivities
					contentBinding.challenge = viewModel
					contentBinding.challengeDetailLabelPrice.text = getString(R.string.challenge_detail_unit_price, viewModel.unitName)
					contentBinding.challengeDetailEmptyContainer.visible = !viewModel.hasActivities
				}, { ex ->
					Timber.e(ex, "Failed to load challenge detail")
					snackbar("Failed to load challenge detail " + ex.message)
				})
	}

}

class ActivityViewHolder(binding: ItemChallengeActivityBinding) : BoundViewHolder<ItemChallengeActivityBinding>(binding)

class ChallengeActivityAdapter(context: Context, items: List<UserActivity>): RecyclerAdapter<ActivityViewHolder, UserActivity>(context, items) {

	init {
		setHasStableIds(true)
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
		val binding = bindingForLayout<ItemChallengeActivityBinding>(R.layout.item_challenge_activity, parent)

		return ActivityViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
		holder.binding.activity = items[position]
//		holder.binding.setVariable(BR.activity, items[position])
	}

	override fun getItemId(position: Int): Long {
		return position.toLong()
	}

	override fun getItemCount(): Int {
		return super.getItemCount()
	}
}
