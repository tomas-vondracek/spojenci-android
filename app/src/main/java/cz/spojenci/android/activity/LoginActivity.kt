package cz.spojenci.android.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInResult
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.GoogleApiClient
import cz.spojenci.android.R
import cz.spojenci.android.dagger.injectSelf
import cz.spojenci.android.data.LoginType
import cz.spojenci.android.data.User
import cz.spojenci.android.data.UserService
import cz.spojenci.android.databinding.ActivityLoginBinding
import cz.spojenci.android.utils.snackbar
import cz.spojenci.android.utils.visible
import cz.spojenci.android.utils.withSchedulers
import rx.Observable
import timber.log.Timber
import javax.inject.Inject

class LoginActivity : AppCompatActivity(), GoogleApiClient.OnConnectionFailedListener {

	companion object {
		private const val RC_GOOGLE_SIGN_IN: Int = 1

		fun start(context: Context) {
			val intent = Intent(context, LoginActivity::class.java)
			context.startActivity(intent)
		}
	}

	@Inject lateinit var service: UserService

	private lateinit var googleApiClient: GoogleApiClient
	private lateinit var binding: ActivityLoginBinding

	private lateinit var callbackManager: CallbackManager

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		injectSelf()

		FacebookSdk.sdkInitialize(applicationContext)

		binding = DataBindingUtil.setContentView(this, R.layout.activity_login)
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
		signInButton.setSize(SignInButton.SIZE_STANDARD)
		signInButton.setOnClickListener { signInWithGoogle() }

		binding.loginSignOut.setOnClickListener { signOut() }

		callbackManager = com.facebook.CallbackManager.Factory.create()
		binding.loginFacebook.setReadPermissions("public_profile", "email")
		binding.loginFacebook.registerCallback(callbackManager, object: FacebookCallback<LoginResult> {
			override fun onError(error: FacebookException?) {
				Timber.w(error, "Facebook login failed")
				snackbar(error?.message ?: "Facebook error")
				updateUI(false)
			}

			override fun onCancel() {
				Timber.d("Facebook login canceled")
				snackbar("Facebook canceled")
				updateUI(false)
			}

			override fun onSuccess(result: LoginResult) {
				val accessToken = result.accessToken
				Timber.d("Facebook login result: $accessToken for user id ${accessToken.userId} " +
						"from source ${accessToken.source} with expiration ${accessToken.expires}")
				showProgress(true, { binding.loginContainer.visible = false })
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

		updateUI(service.isSignedIn)
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

	override fun onConnectionFailed(result: ConnectionResult) {
		Timber.i("Google Play services connection failed. Cause: %s", result.toString())
		Snackbar.make(
				findViewById(R.id.activity_container) as View,
				"Exception while connecting to Google Play services: " + result.errorMessage,
				Snackbar.LENGTH_LONG).show()
	}

	private fun signInWithGoogle() {
		val signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient)
		startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)

		showProgress(false, { binding.loginContainer.visible = true })
	}

	private fun handleGoogleSignInResult(result: GoogleSignInResult) {
		Timber.d("handleSignInResult:" + result.status)
		val idToken = result.signInAccount?.idToken
		if (result.isSuccess && idToken != null) {
			// Signed in successfully, show authenticated UI.
			val account = result.signInAccount
			Timber.d("signed in with google account %s", account)
			showProgress(true, { binding.loginContainer.visible = false })
			singInOnServer(idToken, LoginType.GOOGLE)
		} else {
			if (result.status.isCanceled) {
				snackbar("Google sign in request canceled")
			}
			// Signed out, show unauthenticated UI.
			updateUI(false)
		}
	}

	private fun signOut() {
		val loginType = service.userLoginType
		if (loginType != null) {
			signOutFromProvider(loginType)

			service.signOut().withSchedulers()
				.subscribe({
					updateUI(false)
				}, { ex ->
					Timber.e(ex, "Sign Out failed")
					snackbar("Failed to sign out - " + ex.message)

					updateUI(true)
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
			showProgress(true, { binding.loginContainer.visible = false })

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
				.withSchedulers()
				.subscribe({ user ->
					Timber.i("Successfully signed in as user %s", user)

					updateUI(true)
				}, { ex ->
					Timber.e(ex, "Login failed")
					snackbar("Failed to sign in - " + ex.message)

					signOutFromProvider(loginType)
					updateUI(false)
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

	private fun updateUI(signedIn: Boolean) {
		showProgress(false)
		if (signedIn) {
			binding.loginContainer.visible = false
			binding.loginSignOutContainer.visible = true
		} else {
			binding.loginContainer.visible = true
			binding.loginSignOutContainer.visible = false
		}
		binding.loginStatus.text = getString(R.string.login_status_connected, service.user?.email ?: "")
	}

	/**
	 * Shows the progress UI and hides the login form.
	 */
	private fun showProgress(showProgress: Boolean,
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
