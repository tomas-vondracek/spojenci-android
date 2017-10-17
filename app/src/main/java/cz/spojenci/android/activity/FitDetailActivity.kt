package cz.spojenci.android.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import cz.spojenci.android.R
import cz.spojenci.android.dagger.injectSelf
import cz.spojenci.android.databinding.ActivityFitDetailBinding
import cz.spojenci.android.presenter.ChallengeItemModel
import cz.spojenci.android.presenter.FitAttachAction
import cz.spojenci.android.presenter.FitDetailPresenter
import cz.spojenci.android.presenter.FitItemModel
import cz.spojenci.android.utils.visible
import cz.spojenci.android.utils.withSchedulers
import rx.lang.kotlin.subscribeBy
import timber.log.Timber
import javax.inject.Inject

class FitDetailActivity : BaseActivity() {

	companion object {

		fun startForResult(context: Activity, fitActivity: FitItemModel, requestCode: Int) {

			val intent = Intent(context, FitDetailActivity::class.java)
			intent.putExtra("FIT_ACTIVITY", fitActivity)
			context.startActivityForResult(intent, requestCode)
		}
	}

	@Inject lateinit var presenter: FitDetailPresenter

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		injectSelf()
		val binding = DataBindingUtil.setContentView<ActivityFitDetailBinding>(this, R.layout.activity_fit_detail)
		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		val fitActivityModel = intent.getParcelableExtra<FitItemModel>("FIT_ACTIVITY")
		title = fitActivityModel.description

		binding.fitDetailChallengePicker.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
			override fun onNothingSelected(parent: AdapterView<*>?) { }

			override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
				val model = parent.selectedItem as ChallengeItemModel
				binding.fitDetailAttach.text = "${getString(R.string.fit_detail_attach_to_challenge)} - ${model.name}"
			}
		}
		binding.fitActivity = fitActivityModel
		binding.fitDetailAttach.setOnClickListener {
			val challengeItem = binding.fitDetailChallengePicker.selectedItem as ChallengeItemModel

			val action = FitAttachAction(fitActivityModel.id, fitActivityModel.value, challengeItem.id, fitActivityModel.description, challengeItem.userId)
			presenter.attachFitActivity(action)
					.bindToLifecycle(this)
					.withSchedulers()
					.subscribeBy(
							onNext = { (isAttaching, finished, errorMessage) ->
								binding.fitDetailSendProgress.visible = isAttaching
								binding.fitDetailAttach.isEnabled = !isAttaching

								binding.fitDetailError.visible = errorMessage.isNotEmpty()
								binding.fitDetailError.text = errorMessage

								if (finished) {
									setResult(Activity.RESULT_OK)
									finish()
								}
							},
							onError = { ex ->
								Timber.e(ex, "Failed to update challenge")
							})
		}

		presenter.viewModel(fitActivityModel.id)
				.bindToLifecycle(this)
				.withSchedulers()
				.subscribeBy(onNext = { (items, isLoadingChallenges, isActivityAttached, attachedChallenge, error) ->
					val adapter = ChallengeSpinnerAdapter(this, items)
					adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

					binding.fitDetailChallengePicker.adapter = adapter
					binding.fitDetailChallengePicker.isEnabled = !isLoadingChallenges
					binding.fitDetailChallengePicker.visible = !isActivityAttached
					binding.fitDetailChallengePickerLabel.visible = !isActivityAttached
					binding.fitDetailAttach.isEnabled = items.count() > 0
					binding.fitDetailAttach.visible = !isActivityAttached
					binding.fitDetailAttachedChallenge.text =
							if (isActivityAttached) getString(R.string.fit_detail_attached_to_challenge, attachedChallenge)
							else ""
					binding.fitDetailAttachedChallenge.visible = isActivityAttached
				}, onError = { ex ->
					binding.fitDetailChallengePicker.isEnabled = false
					binding.fitDetailAttach.isEnabled = false
					Timber.e(ex, "Failed to load challenges")
				})

	}

	class ChallengeSpinnerAdapter(context: Context, items: List<ChallengeItemModel>):
			ArrayAdapter<ChallengeItemModel>(context, android.R.layout.simple_spinner_item, items) {

		override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
			val view = super.getView(position, convertView, parent)
			(view as TextView).text = getItem(position).name
			return view
		}

		override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
			val view = super.getDropDownView(position, convertView, parent)
			(view as TextView).text = getItem(position).name
			return view
		}
	}


}
