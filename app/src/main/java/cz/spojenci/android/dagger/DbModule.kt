package cz.spojenci.android.dagger

import android.content.Context
import com.squareup.sqlbrite.BriteDatabase
import com.squareup.sqlbrite.SqlBrite
import cz.spojenci.android.data.local.FitActivitySqlHelper
import dagger.Module
import dagger.Provides
import rx.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Singleton



/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 03/05/17.
 */
@Module
class DbModule {

	@Provides @Singleton internal fun provideBriteDatabase(context: Context): BriteDatabase {
		val sqlBrite = SqlBrite.Builder()
				.logger { message -> Timber.tag("Database").v(message) }
				.build()
		val helper = FitActivitySqlHelper(context)
		val db = sqlBrite.wrapDatabaseHelper(helper, Schedulers.io())
		db.setLoggingEnabled(true)
		return db
	}
}