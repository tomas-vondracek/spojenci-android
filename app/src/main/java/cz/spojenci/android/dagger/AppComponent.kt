package cz.spojenci.android.dagger

import android.content.Context
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton



@Component(modules = arrayOf(AppModule::class, ServerModule::class, DbModule::class))
@Singleton
interface AppComponent {

	fun uiComponent(): UiComponent

	@Component.Builder
	interface Builder {

		@BindsInstance
		fun appContext(context: Context): Builder

		fun build(): AppComponent
	}
}


