package cz.spojenci.android.presenter

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import cz.spojenci.android.R
import retrofit2.adapter.rxjava.HttpException
import java.io.IOException

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 27/05/17.
 */
open class Presenter(protected val context: Context) {

	protected val firebaseAnalytics: FirebaseAnalytics by lazy {
		FirebaseAnalytics.getInstance(context)
	}

	companion object {

		fun translateApiRequestError(context: Context, ex: Throwable): String {
			val stringId =
					when (ex) {
						is HttpException -> {
							translateHttp(ex)
						}
						is UserException -> {
							R.string.error_invalid_user
						}
						is IOException -> {
							R.string.error_internet
						}
						else -> {
							R.string.error_general
						}
					}
			return context.getString(stringId)
		}

		private fun translateHttp(ex: HttpException): Int {
			val httpCode = ex.code()
			return when (httpCode) {
				401, 403 -> {
					R.string.error_auth
				}
				503, 500 -> {
					R.string.error_server_error
				}
				else -> {
					R.string.error_bad_request
				}
			}
		}

		fun isAuthError(ex: Throwable) = ex is HttpException && ex.code() == 401

		fun extractErrorMessage(ex: Throwable): String? = (ex as? HttpException)?.response()?.errorBody()?.string()
	}
}

class UserException(message: String): Exception(message)