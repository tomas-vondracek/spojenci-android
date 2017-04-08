package cz.spojenci.android.presenter

import javax.inject.Inject

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 08/04/17.
 */
class UpdateChallengePresenter @Inject constructor() {

	val form: CreateActivityForm = CreateActivityForm("")
}

data class CreateActivityForm(var activityValue: String)