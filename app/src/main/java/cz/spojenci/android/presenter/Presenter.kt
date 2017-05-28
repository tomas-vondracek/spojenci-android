package cz.spojenci.android.presenter

import android.content.Context
import cz.spojenci.android.R
import retrofit2.adapter.rxjava.HttpException
import java.io.IOException

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 27/05/17.
 */
open class Presenter {

	companion object {

		fun translateApiRequestError(context: Context, ex: Throwable): String {
			val stringId =
					when (ex) {
						is HttpException -> {
							translateHttp(ex)
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
			when (ex.code()) {
				401, 403 -> {
					return R.string.error_auth
				}
				503, 500 -> {
					return R.string.error_server_error
				}
				else -> {
					return R.string.error_bad_request
				}
			}
		}

		fun isAuthError(ex: Throwable) = ex is HttpException && ex.code() == 401
	}
}