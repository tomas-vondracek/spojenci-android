package cz.spojenci.android.utils

import android.app.Activity
import android.support.annotation.LayoutRes
import android.support.design.widget.Snackbar
import android.view.View
import cz.spojenci.android.R

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
	Snackbar.make(findViewById(layoutId) as View, message, length)
			.show();
}