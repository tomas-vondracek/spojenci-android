package cz.spojenci.android.dagger

import cz.spojenci.android.Application
import cz.spojenci.android.activity.LoginActivity
import cz.spojenci.android.activity.MainActivity
import dagger.Subcomponent

@Subcomponent
interface UiComponent {

	fun injectActivity(activity: MainActivity): Unit
	fun injectActivity(activity: LoginActivity): Unit
}

fun MainActivity.injectSelf() {
	val uiComponent = (this.application as Application).uiComponent
	uiComponent.injectActivity(this)
}

fun LoginActivity.injectSelf() {
	val uiComponent = (this.application as Application).uiComponent
	uiComponent.injectActivity(this)
}
