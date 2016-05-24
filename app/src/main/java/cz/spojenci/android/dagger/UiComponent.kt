package cz.spojenci.android.dagger

import cz.spojenci.android.Application
import cz.spojenci.android.activity.MainActivity
import dagger.Subcomponent

@Subcomponent
interface UiComponent {

	fun injectActivity(activity: MainActivity): Unit
}

fun MainActivity.injectSelf() = {
	(this.application as Application).uiComponent.injectActivity(this)
}
