package cz.spojenci.android.data

import rx.Observable
import timber.log.Timber

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 07/04/17.
 */

interface CachableData {

	fun isUpToDate(): Boolean
}

// Simple logging to let us know what each source is returning
fun <T: CachableData?> logSource(source: String): Observable.Transformer<T, T> {
	return Observable.Transformer { dataObservable: Observable<T> ->
		dataObservable.doOnNext({ data ->
			if (data == null) {
				Timber.d(source + " does not have any data.")
			} else if (!data.isUpToDate()) {
				Timber.d(source + " has stale data.")
			} else {
				Timber.d(source + " has the data you are looking for!")
			}
		})
	}
}