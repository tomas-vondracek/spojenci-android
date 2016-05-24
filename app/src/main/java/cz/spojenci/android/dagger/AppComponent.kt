package cz.spojenci.android.dagger

import dagger.Component
import javax.inject.Singleton

@Component(modules = arrayOf(AppModule::class))
@Singleton
interface AppComponent {

	fun uiComponent(): UiComponent
}


