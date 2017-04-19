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
import cz.spojenci.android.databinding.ActivityChallengeDetailBinding
import cz.spojenci.android.databinding.ContentChallengeDetailBinding
import cz.spojenci.android.databinding.ItemChallengeActivityBinding
import cz.spojenci.android.presenter.*
import cz.spojenci.android.utils.*
import rx.Observable
import timber.log.Timber
import javax.inject.Inject

class ChallengeDetailActivity : BaseActivity(), ChallengeDetailPresentable {

	companion object {
		fun start(context: Context, challenge: ChallengeItemModel) {

			val intent = Intent(context, ChallengeDetailActivity::class.java)
			intent.putExtra("CHALLENGE_ID", challenge.id)
			intent.putExtra("CHALLENGE_NAME", challenge.name)
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
		val challengeName = intent.getStringExtra("CHALLENGE_NAME")
		binding.fab.setOnClickListener {
			presenter.createChallengeActivity(context = this)
		}

		contentBinding.challengeDetailRetry.setOnClickListener {
			loadChallengeDetail()
		}

		presenter.startFrom(this)
		observableChallengeDetail = presenter.challengeDetailFor(challengeId, challengeName)
				.withSchedulers()
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

					val isLoading = viewModel.isLoading
					contentBinding.challengeDetailProgress.visible = isLoading
					contentBinding.challengeDetailEmptyContainer.visible = !isLoading && !viewModel.hasActivities
					binding.fab.isEnabled = !isLoading

					if (!isLoading) {
						val items = viewModel.activities

						val list = contentBinding.challengeDetailList
						val adapter = ChallengeActivityAdapter(this, items)
						list.adapter = adapter

						binding.challengeDetailLabelPrice.visible = true
						binding.challengeDetailLabelPrice.text = getString(R.string.challenge_detail_unit_price, viewModel.unitName)
						binding.challenge = viewModel
						contentBinding.challenge = viewModel
						contentBinding.hasActivities = viewModel.hasActivities
					} else {
						binding.toolbar.title = viewModel.name
					}
				}, { ex ->
					Timber.e(ex, "Failed to load challenge detail")
					snackbar("Failed to load challenge detail " + ex.message)
				})
	}

}

class ActivityViewHolder(binding: ItemChallengeActivityBinding) : BoundViewHolder<ItemChallengeActivityBinding>(binding)

class ChallengeActivityAdapter(context: Context, items: List<UserActivityItemViewModel>): RecyclerAdapter<ActivityViewHolder, UserActivityItemViewModel>(context, items) {

	init {
		setHasStableIds(true)
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
		val binding = bindingForLayout<ItemChallengeActivityBinding>(R.layout.item_challenge_activity, parent)

		return ActivityViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
		holder.binding.activity = items[position]
		holder.binding.itemChallengeActivityContainer.setOnClickListener {
			// TODO
		}
	}

	override fun getItemId(position: Int): Long {
		return position.toLong()
	}

	override fun getItemCount(): Int {
		return super.getItemCount()
	}
}
