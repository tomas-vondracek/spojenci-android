package cz.spojenci.android.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.WorkerThread
import com.squareup.sqlbrite.BriteDatabase
import cz.spojenci.android.data.local.FitActivitySqlHelper.Companion.tableNameActivity
import cz.spojenci.android.presenter.FitAttachAction
import rx.Observable
import javax.inject.Inject

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 27/04/17.
 */
class FitActivitySqlHelper(context: Context) : SQLiteOpenHelper(context, "fit_activity", null, 1) {

	companion object {

		const val tableNameActivity = "fit_activity"
	}
	private val createTableActivity =
			"CREATE TABLE IF NOT EXISTS $tableNameActivity (" +
			"_id integer PRIMARY KEY AUTOINCREMENT," +
			"challenge_id varchar(255)," +
			"fit_activity_id varchar(255)," +
			"user_id varchar(255)," +
			"timestamp integer" +
			")"

	override fun onCreate(db: SQLiteDatabase) {
		db.execSQL(createTableActivity)
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
	}
}

class FitActivityDatabase @Inject constructor(val db: BriteDatabase) {

	@WorkerThread
	fun storeAttachedActivity(action: FitAttachAction, userId: String): Long {
		val values = ContentValues(1)
		values.put("challenge_id", action.challengeId)
		values.put("fit_activity_id", action.fitActivityId)
		values.put("user_id", userId)
		values.put("timestamp", System.currentTimeMillis())

		return db.insert(tableNameActivity, values)
	}

	fun fitActivityById(fitActivityId: String): Observable<List<FitActivityRecord>> {
		val query = "SELECT * FROM $tableNameActivity WHERE fit_activity_id == \"$fitActivityId\" LIMIT 1"
		return db.createQuery(tableNameActivity, query)
				.mapToList { c ->
					FitActivityRecord.fromCursor(c)
				}
	}

	fun fitActivityForUser(userId: String): Observable<List<FitActivityRecord>> {
		val query = "SELECT * FROM $tableNameActivity WHERE user_id == \"$userId\""
		return db.createQuery(tableNameActivity, query)
				.mapToList { c ->
					FitActivityRecord.fromCursor(c)
				}
	}
}

data class FitActivityRecord(val challengeId: String, val fitActivityId: String, val userId: String, val timeStamp: Long) {

	companion object {
		fun fromCursor(c: Cursor): FitActivityRecord {
			return FitActivityRecord(c.getString(c.getColumnIndex("challenge_id")),
					c.getString(c.getColumnIndex("fit_activity_id")),
					c.getString(c.getColumnIndex("user_id")),
					c.getLong(c.getColumnIndex("timestamp")))
		}
	}
}