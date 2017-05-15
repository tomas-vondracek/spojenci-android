package cz.spojenci.android.dagger

import cz.spojenci.android.Application
import cz.spojenci.android.activity.*
import dagger.Subcomponent

@Subcomponent
interface UiComponent {

	fun injectActivity(activity: MainActivity): Unit
	fun injectActivity(activity: LoginActivity): Unit
	fun injectActivity(activity: ChallengeDetailActivity): Unit
	fun injectActivity(activity: UpdateChallengeActivity): Unit
	fun injectActivity(activity: FitDetailActivity): Unit
}

fun MainActivity.injectSelf() {
	val uiComponent = (this.application as Application).uiComponent
	uiComponent.injectActivity(this)
}

fun LoginActivity.injectSelf() {
	val uiComponent = (this.application as Application).uiComponent
	uiComponent.injectActivity(this)
}
fun ChallengeDetailActivity.injectSelf() {
	val uiComponent = (this.application as Application).uiComponent
	uiComponent.injectActivity(this)
}
fun UpdateChallengeActivity.injectSelf() {
	val uiComponent = (this.application as Application).uiComponent
	uiComponent.injectActivity(this)
}
fun FitDetailActivity.injectSelf() {
	val uiComponent = (this.application as Application).uiComponent
	uiComponent.injectActivity(this)
}
