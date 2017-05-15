package cz.spojenci.android.dagger

import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
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

	@Provides @Singleton internal fun provideOpenHelper(context: Context): SQLiteOpenHelper {
		return FitActivitySqlHelper(context)
	}

	@Provides @Singleton internal fun provideSqlBrite(): SqlBrite {
		return SqlBrite.Builder()
				.logger { message -> Timber.tag("Database").v(message) }
				.build()
	}

	@Provides @Singleton internal fun provideBriteDatabase(sqlBrite: SqlBrite, helper: SQLiteOpenHelper): BriteDatabase {
		val db = sqlBrite.wrapDatabaseHelper(helper, Schedulers.io())
		db.setLoggingEnabled(true)
		return db
	}
}