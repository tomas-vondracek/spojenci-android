package cz.spojenci.android.dagger

import cz.spojenci.android.data.FitRepository
import cz.spojenci.android.data.IFitRepository
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class AppModule {

	@Provides
	@Singleton
	fun provideFitRepo(): IFitRepository {
		return FitRepository()
//		return MockFitRepository()
	}
}