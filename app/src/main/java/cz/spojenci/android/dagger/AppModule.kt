package cz.spojenci.android.dagger

import android.content.Context
import cz.spojenci.android.data.FitRepository
import cz.spojenci.android.data.IFitRepository
import cz.spojenci.android.data.IUserService
import cz.spojenci.android.data.UserService
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class AppModule(private val context: Context) {

	@Provides
	@Singleton
	fun provideContext(): Context {
		return this.context
	}

	@Provides
	@Singleton
	fun provideFitRepo(): IFitRepository {
		return FitRepository()
	}

	@Provides
	@Singleton
	fun provideUserService(): IUserService {
		return UserService()
	}
}