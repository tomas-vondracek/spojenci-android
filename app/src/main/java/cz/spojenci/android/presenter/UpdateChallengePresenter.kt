package cz.spojenci.android.presenter

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import cz.spojenci.android.data.ChallengesRepository
import rx.Observable
import rx.Subscription
import timber.log.Timber
import javax.inject.Inject

interface UpdateChallengePresentable {

	val valueText: Observable<CharSequence>
	val commentText: Observable<CharSequence>

	fun updateActivityForm(form: CreateActivityForm)
	fun showValidationMessage()
}

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 08/04/17.
 */
class UpdateChallengePresenter @Inject constructor(context: Context,
                                                   private val repository: ChallengesRepository): Presenter(context) {

	private val form: CreateActivityForm = CreateActivityForm("", "", "")

	private lateinit var challengeId: String
	private lateinit var view: UpdateChallengePresentable

	private var valueTextSubscription: Subscription? = null
	private var commentTextSubscription: Subscription? = null

	fun startFrom(view: UpdateChallengePresentable, challengeId: String, unit: String) {
		this.challengeId = challengeId
		this.view = view

		this.form.unit = unit
		this.view.updateActivityForm(form)
		this.valueTextSubscription = this.view.valueText.subscribe { form.activityValue = it.toString() }
		this.commentTextSubscription = this.view.commentText.subscribe { form.comment = it.toString() }
	}

	fun stop() {
		this.valueTextSubscription?.unsubscribe()
		this.commentTextSubscription?.unsubscribe()
	}

	fun sendActivity(): Observable<SendActivityViewModel> {
		if (! canSendActivity()) {
			view.showValidationMessage()

			return Observable.empty()
		}
		return repository.postChallengeActivity(challengeId, form.activityValue, form.comment)
				.map<SendActivityViewModel> {
					SendActivityViewModel.Success(challengeId, form.activityValue.isNotEmpty(), form.comment.isNotEmpty())
				}
				.doOnError { Timber.e(it, "failed to send activity") }
				.onErrorReturn { SendActivityViewModel.Error(translateApiRequestError(context, it), it) }
				.startWith(SendActivityViewModel.InProgress())
	}

	private fun canSendActivity(): Boolean = form.activityValue.isNotEmpty() || form.comment.isNotEmpty()

}

sealed class SendActivityViewModel {

	class Success(val challengeId: String, val containsValue: Boolean, val containsComment: Boolean) : SendActivityViewModel()
	class InProgress : SendActivityViewModel()
	data class Error(val message: String, val cause: Throwable): SendActivityViewModel()
}

data class CreateActivityForm(var activityValue: String, var unit: String, var comment: String) : Parcelable {

	@Suppress("unused")
	companion object {
		@JvmField val CREATOR: Parcelable.Creator<CreateActivityForm> = object : Parcelable.Creator<CreateActivityForm> {
			override fun createFromParcel(source: Parcel): CreateActivityForm = CreateActivityForm(source)
			override fun newArray(size: Int): Array<CreateActivityForm?> = arrayOfNulls(size)
		}
	}

	constructor(source: Parcel) : this(source.readString(), source.readString(), source.readString())

	override fun describeContents() = 0

	override fun writeToParcel(dest: Parcel?, flags: Int) {
		dest?.writeString(activityValue)
		dest?.writeString(unit)
		dest?.writeString(comment)
	}
}