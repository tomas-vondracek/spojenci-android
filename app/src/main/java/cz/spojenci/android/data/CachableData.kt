package cz.spojenci.android.data

import rx.Observable
import timber.log.Timber

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 07/04/17.
 */

interface CachableData {

	fun isUpToDate(): Boolean
}

// http://blog.danlew.net/2015/06/22/loading-data-from-multiple-sources-with-rxjava/

/** Simple logging to let us know what each source is returning */
fun <T> logSource(source: String): Observable.Transformer<T, T> {
	return Observable.Transformer { dataObservable: Observable<T> ->
		dataObservable.doOnNext({ data ->
			if (data == null) {
				Timber.d(source + " does not have any data.")
			} else {
				Timber.d(source + " has the data you are looking for!")
			}
		})
	}
}