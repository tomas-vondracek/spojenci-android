package cz.spojenci.android.utils

import android.app.Activity
import androidx.annotation.LayoutRes
import com.google.android.material.snackbar.Snackbar
import android.view.View
import cz.spojenci.android.R
import rx.Emitter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.math.BigDecimal
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

var View.visible: Boolean
	get() = visibility == View.VISIBLE
	set(value) {
		this.visibility = when {
			value -> View.VISIBLE
			else -> View.GONE
		}
	}

fun Activity.snackbar(message: String,
                      @LayoutRes layoutId: Int = R.id.activity_container,
                      length: Int = Snackbar.LENGTH_LONG) {
	Snackbar.make(findViewById(layoutId), message, length)
			.show()
}

fun <T> Observable<T>.withSchedulers(): Observable<T> {
	return observeOn(AndroidSchedulers.mainThread())
			.subscribeOn(Schedulers.io())
}

val formatter: NumberFormat = NumberFormat.getCurrencyInstance().let {
	it.currency = Currency.getInstance("CZK")
	it
}


fun BigDecimal.formatAsPrice(currency: String): String {
	synchronized (formatter) {
		if (formatter.currency.currencyCode != currency) {
			formatter.currency = Currency.getInstance(currency)
		}
		return formatter.format(this)
	}
}

val dateTimeFormatter: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
val dateFormatter: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)

fun Long.formatAsDateTime(): String {
	return dateTimeFormatter.format(Date(this))
}

fun Date.formatAsDateTime(): String {
	return dateTimeFormatter.format(this)
}
fun Date.formatAsDate(): String {
	return dateFormatter.format(this)
}

//2017-04-16T02:00:00+0200
private val serverDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)

fun String.parseAsServerDate(): Date {
	return serverDateFormat.parse(this)
}


private val distanceNumberFormat = NumberFormat.getNumberInstance()

fun Float.formatAsDistance(): String {
	return distanceNumberFormat.format(this)
}

fun <T> com.google.android.gms.tasks.Task<T>.asObservable(): Observable<T> {
	return Observable.create({ emitter ->

		this.addOnSuccessListener { response ->
			emitter.onNext(response)
		}
		this.addOnFailureListener { ex ->
			emitter.onError(ex)
		}
		this.addOnCompleteListener {
			emitter.onCompleted()
		}
	}, Emitter.BackpressureMode.NONE)
}