package cz.spojenci.android.dagger

import cz.spojenci.android.BuildConfig
import cz.spojenci.android.data.remote.IChallengesEndpoint
import cz.spojenci.android.data.remote.IUserEndpoint
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import javax.inject.Singleton

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 31/07/16.
 */
@Module
class ServerModule() {

	val log: HttpLoggingInterceptor = HttpLoggingInterceptor(HttpLoggingInterceptor.Logger {
		Timber.d(it)
	}).setLevel(
			if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
			else HttpLoggingInterceptor.Level.BASIC
	)

	@Provides
	@Singleton
	fun provideRetrofit(): Retrofit {

		val httpClient = OkHttpClient.Builder()
				.addInterceptor(log)
				.build()

		val retrofit = Retrofit.Builder()
				.client(httpClient)
				.addCallAdapterFactory(RxJavaCallAdapterFactory.create())
				.addConverterFactory(GsonConverterFactory.create())
				.baseUrl(BuildConfig.SERVER_URL)
				.build()

		return retrofit
	}

	@Provides
	@Singleton
	fun provideUserEndpoint(retrofit: Retrofit): IUserEndpoint {
		return retrofit.create(IUserEndpoint::class.java)
	}

	@Provides
	@Singleton
	fun provideChallengesEndpoint(retrofit: Retrofit): IChallengesEndpoint {
		return retrofit.create(IChallengesEndpoint::class.java)
	}
}