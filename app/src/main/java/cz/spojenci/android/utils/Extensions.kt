package cz.spojenci.android.utils

import android.app.Activity
import android.support.annotation.LayoutRes
import android.support.design.widget.Snackbar
import android.view.View
import cz.spojenci.android.R
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.math.BigDecimal
import java.text.DateFormat
import java.text.NumberFormat
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

fun Long.formatAsDateTime(): String {
	return dateTimeFormatter.format(Date(this))
}