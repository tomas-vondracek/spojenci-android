package cz.spojenci.android.data

import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Status
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.SessionReadRequest
import rx.Observable
import java.util.*
import java.util.concurrent.TimeUnit

data class FitSession(val id: String, val description: String, val timestamp: Long, val distanceValue: Float, val activityType: String)
data class FitReadResult(val status: Status, val sessions: List<FitSession>)

interface IFitRepository {

	fun sessions(apiClient: GoogleApiClient): Observable<FitReadResult>
}

class FitRepository : IFitRepository {

	override fun sessions(apiClient: GoogleApiClient): Observable<FitReadResult> {
		return Observable.fromCallable {
			// Set a start and end time for our query, using a start time of 1 week before this moment.
			val cal = Calendar.getInstance()
			val endTime = cal.timeInMillis
			cal.add(Calendar.MONTH, -1)
			val startTime = cal.timeInMillis

			// Build a session read request
			val readRequest = SessionReadRequest.Builder()
					.setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
					.read(DataType.TYPE_DISTANCE_DELTA)
					.enableServerQueries()
					.readSessionsFromAllApps()
					.build()

			Fitness.SessionsApi.readSession(apiClient, readRequest)
					.await(1, TimeUnit.MINUTES)
		}.map { result ->
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
}