package cz.spojenci.android.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.avast.android.dialogs.fragment.SimpleDialogFragment
import com.avast.android.dialogs.iface.IPositiveButtonDialogListener
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.franmontiel.persistentcookiejar.persistence.CookiePersistor
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInResult
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crash.FirebaseCrash
import com.squareup.picasso.Picasso
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import cz.spojenci.android.R
import cz.spojenci.android.dagger.injectSelf
import cz.spojenci.android.data.LoginType
import cz.spojenci.android.data.User
import cz.spojenci.android.data.UserService
import cz.spojenci.android.databinding.ActivityLoginBinding
import cz.spojenci.android.presenter.Presenter
import cz.spojenci.android.utils.findCookie
import cz.spojenci.android.utils.snackbar
import cz.spojenci.android.utils.visible
import cz.spojenci.android.utils.withSchedulers
import rx.Observable
import rx.lang.kotlin.subscribeBy
import timber.log.Timber
import javax.inject.Inject

class LoginActivity : BaseActivity(), GoogleApiClient.OnConnectionFailedListener, IPositiveButtonDialogListener {

	companion object {
		private const val RC_GOOGLE_SIGN_IN: Int = 1
		private const val RC_LOGIN_EXPIRED: Int = 2
		private const val RC_ASK_SIGN_OUT: Int = 3
		private const val RC_GOOGLE_RESOLUTION: Int = 4

		fun start(context: Context) {
			val intent = Intent(context, LoginActivity::class.java)
			context.startActivity(intent)
		}
	}

	@Inject lateinit var service: UserService
	@Inject lateinit var cookieStorage: CookiePersistor

	private lateinit var googleApiClient: GoogleApiClient
	private lateinit var binding: ActivityLoginBinding

	private lateinit var callbackManager: CallbackManager

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		injectSelf()

		FacebookSdk.sdkInitialize(applicationContext)

		binding = DataBindingUtil.setContentView(this, R.layout.activity_login)
		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		// Configure sign-in to request the user's ID, email address, and basic
		// profile. ID and basic profile are included in DEFAULT_SIGN_IN.
		val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
				.requestEmail()
				.requestProfile()
				.requestIdToken(getString(R.string.google_client_id))
				.build()

		googleApiClient = GoogleApiClient.Builder(this)
				.enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
				.addApi(Auth.GOOGLE_SIGN_IN_API, gso)
				.build()

		val signInButton = binding.loginGoogle
		signInButton.setOnClickListener { signInWithGoogle() }

		binding.loginSignOut.setOnClickListener {
			SimpleDialogFragment.createBuilder(this, supportFragmentManager)
					.setRequestCode(RC_ASK_SIGN_OUT)
					.setMessage(R.string.login_disconnect_ask)
					.setPositiveButtonText(R.string.login_disconnect)
					.setNegativeButtonText(R.string.close)
					.show()
		}

		callbackManager = com.facebook.CallbackManager.Factory.create()
		val loginManager = LoginManager.getInstance()
		binding.loginFacebook.setOnClickListener {
			loginManager.logInWithReadPermissions(this, listOf("public_profile", "email"))
		}
		loginManager.registerCallback(callbackManager, object: FacebookCallback<LoginResult> {
			override fun onError(error: FacebookException?) {
				Timber.w(error, "Facebook login failed")
				snackbar(error?.message ?: "Facebook error")
				FirebaseCrash.report(Exception("Facebook login failed", error))
				updateUI(null)
			}

			override fun onCancel() {
				Timber.d("Facebook login canceled")
				snackbar("Facebook canceled")
				updateUI(null)
			}

			override fun onSuccess(result: LoginResult) {
				val accessToken = result.accessToken
				Timber.d("Facebook login result: $accessToken for user id ${accessToken.userId} " +
						"from source ${accessToken.source} with expiration ${accessToken.expires}")
				showLoginProgress(true, { binding.loginContainer.visible = false })
				singInOnServer(accessToken.token, LoginType.FACEBOOK)
			}
		})

		binding.password.setOnEditorActionListener(TextView.OnEditorActionListener { textView, id, keyEvent ->
			if (id == R.id.email_login || id == EditorInfo.IME_NULL) {
				signInWithEmail()
				return@OnEditorActionListener true
			}
			false
		})

		binding.loginEmailSignInButton.setOnClickListener { signInWithEmail() }

		updateUI(service.user)

		if (service.isSignedIn) {
			// refresh user profile
			loadUserProfile()
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		// Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...)
		if (requestCode == RC_GOOGLE_SIGN_IN) {
			val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
			handleGoogleSignInResult(result)
		} else {
			callbackManager.onActivityResult(requestCode, resultCode, data)
		}
	}

	override fun onPositiveButtonClicked(requestCode: Int) {
		if (requestCode == RC_LOGIN_EXPIRED || requestCode == RC_ASK_SIGN_OUT) {
			signOut()
		}
	}

	override fun onConnectionFailed(result: ConnectionResult) {
		FirebaseCrash.report(Exception("Google Play services connection failed. Cause: $result"))
		Timber.i("Google Play services connection failed. Cause: %s", result.toString())
		Snackbar.make(
				findViewById<View>(R.id.activity_container),
				"Exception while connecting to Google Play services: " + result.errorMessage,
				Snackbar.LENGTH_LONG).show()
	}

	private fun loadUserProfile() {
		service.updateUserProfile()
				.withSchedulers()
				.bindToLifecycle(this)
				.doOnSubscribe { binding.loginProfileProgress.visible = true }
				.doOnTerminate { binding.loginProfileProgress.visible = false }
				.subscribeBy(onNext = { user ->
					Timber.d("updated user: $user")
					updateUI(user)
				}, onError = { ex ->
					Timber.e(ex, "failed to update user profile")
					updateUI(service.user)

					if (Presenter.isAuthError(ex)) {
						val serverMessage = Presenter.extractErrorMessage(ex) ?: ""
						val sessionCookie = cookieStorage.findCookie("session_id")
						Timber.e("Message: $serverMessage\nCookie: $sessionCookie")
						FirebaseCrash.report(Exception("user login expired - $serverMessage for $sessionCookie", ex))
						SimpleDialogFragment.createBuilder(this, supportFragmentManager)
								.setRequestCode(RC_LOGIN_EXPIRED)
								.setMessage(R.string.login_expired)
								.setTitle(R.string.login_user_profile_failed)
								.setPositiveButtonText(R.string.login_again)
								.setNegativeButtonText(R.string.close)
								.show()
					} else {
						SimpleDialogFragment.createBuilder(this, supportFragmentManager)
								.setMessage(Presenter.translateApiRequestError(this, ex))
								.setTitle(R.string.login_user_profile_failed)
								.setPositiveButtonText(R.string.ok)
								.show()
					}
				})
	}

	private fun signInWithGoogle() {
		val signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient)
		startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)

		showLoginProgress(false, { binding.loginContainer.visible = true })
	}

	private fun handleGoogleSignInResult(result: GoogleSignInResult) {
		val status = result.status
		Timber.d("handleSignInResult:" + status)
		val idToken = result.signInAccount?.idToken
		if (result.isSuccess && idToken != null) {
			// Signed in successfully, show authenticated UI.
			val account = result.signInAccount
			Timber.d("signed in with google account %s", account)
			showLoginProgress(true, { binding.loginContainer.visible = false })
			singInOnServer(idToken, LoginType.GOOGLE)
		} else {
			when {
				status.hasResolution() -> status.startResolutionForResult(this, RC_GOOGLE_RESOLUTION)
				status.isCanceled -> snackbar("Google sign in request canceled")
				status.isInterrupted -> snackbar("Google sign in request interrupted")
				else -> {
					snackbar(status.statusMessage ?: "Google sign in failed; error code: ${status.statusCode}")
					FirebaseCrash.report(Exception("Google sign in failed; error message:${status.statusMessage};error code: ${status.statusCode}"))
				}
			}
			// Signed out, show unauthenticated UI.
			updateUI(null)
		}
	}

	private fun signOut() {
		val loginType = service.userLoginType
		if (loginType != null) {
			signOutFromProvider(loginType)

			service.signOut().withSchedulers()
				.subscribe({
					firebaseAnalytics.setUserProperty("session_id", null)
					updateUI(null)
				}, { ex ->
					Timber.e(ex, "Sign Out failed")
					snackbar("Failed to sign out - " + ex.message)

					updateUI(service.user)
				})
		}
	}

	private fun signOutFromProvider(loginType: LoginType) {
		Timber.d("signing out from provider with login type $loginType")

		if (loginType == LoginType.FACEBOOK) {
			LoginManager.getInstance().logOut()
		} else if (loginType == LoginType.GOOGLE) {
			Auth.GoogleSignInApi.signOut(googleApiClient)
		}
	}

	/**
	 * Attempts to sign in with the account specified by the login form.
	 * If there are form errors (invalid email, missing fields, etc.), the
	 * errors are presented and no actual login attempt is made.
	 */
	private fun signInWithEmail() {
		val editEmail = binding.email
		val editPassword = binding.password
		// Reset errors.
		editEmail.error = null
		editPassword.error = null

		// Store values at the time of the login attempt.
		val email = editEmail.text.toString()
		val password = editPassword.text.toString()

		var cancel = false
		var focusView: View? = null

		// Check for a valid password, if the user entered one.
		if (password.isNullOrEmpty()) {
			editPassword.error = getString(R.string.error_field_required)
			focusView = editPassword
			cancel = true
		} else if (!isPasswordValid(password)) {
			editPassword.error = getString(R.string.error_invalid_password)
			focusView = editPassword
			cancel = true
		}

		// Check for a valid email address.
		if (email.isNullOrEmpty()) {
			editEmail.error = getString(R.string.error_field_required)
			focusView = editEmail
			cancel = true
		} else if (!isEmailValid(email)) {
			editEmail.error = getString(R.string.error_invalid_email)
			focusView = editEmail
			cancel = true
		}

		if (cancel) {
			// There was an error don't attempt login and focus the first
			// form field with an error.
			focusView?.requestFocus()
		} else {
			showLoginProgress(true, { binding.loginContainer.visible = false })

			singInOnServer(email, password)

		}
	}

	private fun singInOnServer(email: String, password: String) {
		val signInObservable = service.signInWithEmail(email, password)
		doSignInOnServer(signInObservable, LoginType.EMAIL)
	}

	private fun singInOnServer(token: String, loginType: LoginType) {
		val signInObservable = service.signInWithSocial(token, loginType)
		doSignInOnServer(signInObservable, loginType)
	}

	private fun doSignInOnServer(signInObservable: Observable<User>, loginType: LoginType) {
		signInObservable
				.doOnNext {
					val sessionCookie = cookieStorage.findCookie("session_id")
					Timber.d("session cookie: $sessionCookie")
					sessionCookie?.apply {
						FirebaseAnalytics.getInstance(this@LoginActivity).setUserProperty("session_id", value())
					}
				}
				.withSchedulers()
				.subscribe({ user ->
					Timber.i("Successfully signed in as user %s", user)
					firebaseAnalytics.setUserId(user.id)
					firebaseAnalytics.setUserProperty("loginType", user.loginType)

					updateUI(user)
				}, { ex ->
					Timber.e(ex, "Login failed")
					if (Presenter.isAuthError(ex)) {
						val serverMessage = Presenter.extractErrorMessage(ex) ?: ""
						FirebaseCrash.report(Exception("user login failed - " + serverMessage, ex))

						SimpleDialogFragment.createBuilder(this, supportFragmentManager)
								.setMessage(getString(R.string.error_login, serverMessage))
								.setPositiveButtonText(R.string.ok)
								.show()
					} else {
						FirebaseCrash.report(Exception("user login failed", ex))
						snackbar(Presenter.translateApiRequestError(this, ex))
					}

					signOutFromProvider(loginType)
					updateUI(null)
				})
	}

	private fun isEmailValid(email: String): Boolean {
		//TODO: Replace this with your own logic
		return email.contains("@")
	}

	private fun isPasswordValid(password: String): Boolean {
		//TODO: Replace this with your own logic
		return password.length >= 4
	}

	private fun updateUI(user: User?) {
		val signedIn = user != null

		showLoginProgress(false)
		binding.loginContainer.visible = !signedIn
		binding.loginSignOutContainer.visible = signedIn

		binding.loginUserPhoto.visible = signedIn
		binding.loginUserName.visible = signedIn

		user?.apply {
			binding.loginUserName.text = "$name $surname"
			binding.loginStatus.text = email
			val loginProvider = when (service.userLoginType ?: LoginType.EMAIL) {
				LoginType.FACEBOOK -> "Facebook"
				LoginType.GOOGLE -> "Google"
				else -> "E-mail"
			}
			binding.loginProvider.text = getString(R.string.login_status_connected, loginProvider)

			val size = resources.getDimensionPixelSize(R.dimen.profile_photo_size)
			photo_url?.let { url ->
				Picasso.with(this@LoginActivity)
						.load(url)
						.noFade()
						.resize(size, size)
						.placeholder(R.drawable.user_silhouette)
						.into(binding.loginUserPhoto)
			}
		}
	}

	/**
	 * Shows the progress UI and hides the login form.
	 */
	private fun showLoginProgress(showProgress: Boolean,
	                              animationCallback: () -> Unit = { }) {
		if (binding.loginProgress.visible == showProgress) {
			return
		}

		val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime)

		binding.loginContainer.visible = !showProgress
		binding.loginContainer.animate()
				.setDuration(shortAnimTime.toLong())
				.alpha((if (showProgress) 0 else 1).toFloat())
				.setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						animationCallback()
					}
				})

		binding.loginProgress.visible = showProgress
		binding.loginProgress.animate()
				.setDuration(shortAnimTime.toLong())
				.alpha((if (showProgress) 1 else 0).toFloat())
				.setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						binding.loginProgress.visible = showProgress
					}
				})
	}
}
