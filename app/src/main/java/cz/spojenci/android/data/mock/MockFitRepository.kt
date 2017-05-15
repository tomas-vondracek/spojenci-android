package cz.spojenci.android.data.mock

import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Status
import cz.spojenci.android.data.FitReadResult
import cz.spojenci.android.data.FitSession
import cz.spojenci.android.data.IFitRepository
import rx.Observable
import java.util.*

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 25/04/17.
 */
class MockFitRepository: IFitRepository {

	private val sessions: List<FitSession> = listOf(
		FitSession("id1", "Mock session1", Date().time, 3101.1F, "jumping"),
		FitSession("id2", "Mock session2", Date().time, 6230.1F, "running")
	)

	override fun sessions(apiClient: GoogleApiClient): Observable<FitReadResult> {
		return Observable.just(FitReadResult(Status(0), sessions))
	}
}