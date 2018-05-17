package cz.spojenci.android.data

import android.app.Activity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Status
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.SessionReadRequest
import com.google.android.gms.fitness.result.SessionReadResponse
import cz.spojenci.android.utils.asObservable
import rx.Observable
import java.util.*
import java.util.concurrent.TimeUnit

data class FitSession(val id: String, val description: String, val timestamp: Long, val distanceValue: Float, val activityType: String)
data class FitReadResult(val status: Status, val sessions: List<FitSession>)

interface IFitRepository {

	fun sessions(activity: Activity): Observable<FitReadResult>
}

class FitRepository : IFitRepository {

	override fun sessions(activity: Activity): Observable<FitReadResult> {
		return observableFitSession(activity).map { result ->
			val sessions = when {
				result.status.isSuccess ->
					result.sessions.map { session ->
						session to result.getDataSet(session, DataType.TYPE_DISTANCE_DELTA)
					}.filter { (_, dataSet) ->
						dataSet.isNotEmpty() && dataSet.first().dataPoints.isNotEmpty()
					}.map { (session, dataSet) ->
						val description = session.name ?: ""
						val value = dataSet.firstOrNull()?.dataPoints?.firstOrNull()?.getValue(Field.FIELD_DISTANCE)

						val startTime = session.getStartTime(TimeUnit.MILLISECONDS)
						val distanceValue = if (value?.format == Field.FORMAT_FLOAT) value.asFloat() else 0.0F
						FitSession(session.identifier, description, startTime, distanceValue, session.activity)
					}.sortedByDescending(FitSession::timestamp)
				else -> emptyList()
			}
			FitReadResult(result.status, sessions)
		}
	}

	private fun observableFitSession(activity: Activity): Observable<SessionReadResponse> {
		val readRequest = buildReadRequest()

		val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(activity) ?: return Observable.empty()

		return Fitness.getSessionsClient(activity, lastSignedInAccount)
				.readSession(readRequest)
				.asObservable()
	}

	private fun buildReadRequest(): SessionReadRequest {
		// Set a start and end time for our query, using a start time of 1 week before this moment.
		val cal = Calendar.getInstance()
		val endTime = cal.timeInMillis
		cal.add(Calendar.MONTH, -1)
		val startTime = cal.timeInMillis

		// Build a session read request
		return SessionReadRequest.Builder()
				.setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
				.read(DataType.TYPE_DISTANCE_DELTA)
				.enableServerQueries()
				.readSessionsFromAllApps()
				.build()
	}
}