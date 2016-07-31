package cz.spojenci.android.data.remote

import cz.spojenci.android.data.LoginRequest
import cz.spojenci.android.data.LoginResponse
import cz.spojenci.android.data.UserResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import rx.Observable

/**
 * @author Tomáš Vondráček (tomas.vondracek@gmail.com) on 31/07/16.
 */
interface IUserEndpoint {

	@Headers("Content-Type: application/json")
	@POST("user/login")
	fun login(@Body request: LoginRequest): Observable<LoginResponse>

	@GET("user/me")
	fun me(): Observable<UserResponse>

	@POST("user/logout")
	fun logout(): Observable<Void>
}