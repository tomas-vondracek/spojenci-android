package cz.spojenci.android.data

import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Status
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.SessionReadRequest
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit

data class FitSession(val id: String, val description: String, val timestamp: Long)
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
			cal.add(Calendar.WEEK_OF_YEAR, -1)
			val startTime = cal.timeInMillis

			// Build a session read request
			val readRequest = SessionReadRequest.Builder()
					.setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
					.read(DataType.TYPE_DISTANCE_DELTA)
					.readSessionsFromAllApps()
					.build()

			Fitness.SessionsApi.readSession(apiClient, readRequest)
					.await(1, TimeUnit.MINUTES)
		}.map { result ->
			val sessions = when {
				result.status.isSuccess ->
					result.sessions.map { s ->
						val description = "${s.activity}, ${s.description}, ${s.name}"
						FitSession(s.identifier, description, s.getStartTime(TimeUnit.MILLISECONDS))
					}.sortedByDescending { session -> session.timestamp }
				else -> emptyList()
			}
			FitReadResult(result.status, sessions)
		}.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
	}
}