package cz.spojenci.android.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import cz.spojenci.android.R
import cz.spojenci.android.dagger.injectSelf
import cz.spojenci.android.databinding.ActivityChallengeDetailBinding
import cz.spojenci.android.databinding.ItemChallengeActivityBinding
import cz.spojenci.android.presenter.*
import cz.spojenci.android.utils.*
import rx.Observable
import timber.log.Timber
import javax.inject.Inject

class ChallengeDetailActivity : BaseActivity(), ChallengeDetailPresentable {

	companion object {

		val REQUEST_CREATE_ACTIVITY = 1

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

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		injectSelf()

		binding = DataBindingUtil.setContentView(this, R.layout.activity_challenge_detail)
		binding.challengeDetailList.layoutManager = LinearLayoutManager(this)
		binding.challengeDetailList.adapter = ChallengeActivityAdapter(this, emptyList())
		binding.hasActivities = true

		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		val challengeId = intent.getStringExtra("CHALLENGE_ID")
		val challengeName = intent.getStringExtra("CHALLENGE_NAME")
		binding.fab.setOnClickListener {
			presenter.createChallengeActivity(context = this, requestCode = REQUEST_CREATE_ACTIVITY)
		}

		binding.challengeDetailRetry.setOnClickListener {
			loadChallengeDetail()
		}

		presenter.startFrom(this)
		observableChallengeDetail = presenter.challengeDetailFor(challengeId, challengeName)
				.withSchedulers()
				.bindToLifecycle(this)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == REQUEST_CREATE_ACTIVITY && resultCode == Activity.RESULT_OK) {
			loadChallengeDetail()
		}
	}

	override fun onStart() {
		super.onStart()

		loadChallengeDetail()
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.challenge_detail, menu)
		return super.onCreateOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem?): Boolean {
		when (item?.itemId) {
			R.id.menu_challenge_detail_pay -> presenter.openPayment(this)
		}
		return super.onOptionsItemSelected(item)
	}
	private fun loadChallengeDetail() {
		observableChallengeDetail
				.subscribe({ viewModel ->
					Timber.d("Loaded challenge detail: $viewModel on thread ${Thread.currentThread().name}")

					val isLoading = viewModel is ChallengeDetailViewModel.InProgress
					binding.challengeDetailProgress.visible = isLoading
					binding.fab.isEnabled = !isLoading

					when (viewModel) {
						is ChallengeDetailViewModel.Success -> {
							val items = viewModel.activities

							val list = binding.challengeDetailList
							val adapter = ChallengeActivityAdapter(this, items)
							list.adapter = adapter

							binding.challengeDetailLabelPrice.visible = true
							binding.challengeDetailLabelPrice.text = getString(R.string.challenge_detail_unit_price, viewModel.unitName)
							binding.challenge = viewModel
							binding.hasActivities = viewModel.hasActivities
							binding.challengeDetailEmptyContainer.visible = !viewModel.hasActivities
						}
						is ChallengeDetailViewModel.Error -> {
							snackbar(viewModel.message)
							binding.challengeDetailEmptyContainer.visible = true
						}
						is ChallengeDetailViewModel.InProgress -> {
							binding.toolbar.title = viewModel.name
							binding.challengeDetailEmptyContainer.visible = false
						}
					}

				}, { ex ->
					snackbar("Failed to process challenge detail " + ex.message)
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
