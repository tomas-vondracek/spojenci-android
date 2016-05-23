package cz.spojenci.android.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.TextView
import butterknife.bindView
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInResult
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Scope
import cz.spojenci.android.R
import timber.log.Timber

class SocialLoginActivity : AppCompatActivity(), GoogleApiClient.OnConnectionFailedListener {

	companion object {
		private const val RC_SIGN_IN: Int = 1

		fun start(context: Context) {
			val intent = Intent(context, SocialLoginActivity::class.java)
			context.startActivity(intent)
		}
	}

	private lateinit var googleApiClient: GoogleApiClient

	private val viewStatus: TextView by bindView(R.id.account_status)
	private val btnSignIn: View by bindView(R.id.account_sign_in_button)
	private val viewDisconnect: View by bindView(R.id.account_sign_out_and_disconnect)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_social_login)

		// Configure sign-in to request the user's ID, email address, and basic
		// profile. ID and basic profile are included in DEFAULT_SIGN_IN.
		val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
				.requestEmail()
				.requestProfile()
				.requestScopes(Scope(Scopes.FITNESS_LOCATION_READ))
				.build();

		googleApiClient = GoogleApiClient.Builder(this)
				.enableAutoManage(this, this /* OnConnectionFailedListener */)
				.addApi(Auth.GOOGLE_SIGN_IN_API, gso)
				.build();

		val signInButton = btnSignIn as SignInButton;
		signInButton.setSize(SignInButton.SIZE_STANDARD);
		signInButton.setScopes(gso.scopeArray);
		signInButton.setOnClickListener { signIn() }
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		// Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
		if (requestCode == Companion.RC_SIGN_IN) {
			val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
			handleSignInResult(result)
		}
	}

	override fun onConnectionFailed(result: ConnectionResult) {
		Timber.i("Google Play services connection failed. Cause: " + result.toString());
		Snackbar.make(
				findViewById(R.id.activity_container) as View,
				"Exception while connecting to Google Play services: " + result.errorMessage,
				Snackbar.LENGTH_INDEFINITE).show();
	}

	private fun signIn() {
		val signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient);
		startActivityForResult(signInIntent, Companion.RC_SIGN_IN);
	}

	private fun handleSignInResult(result: GoogleSignInResult) {
		Timber.d("handleSignInResult:" + result.isSuccess);
		if (result.isSuccess) {
			// Signed in successfully, show authenticated UI.
			val acct = result.signInAccount;
			updateUI(true);
		} else {
			// Signed out, show unauthenticated UI.
			updateUI(false);
		}
	}

	private fun updateUI(signedIn: Boolean) {
		if (signedIn) {
			viewStatus.setText(R.string.account_status_connected)
//			val drawable = ContextCompat.getDrawable(this, R.drawable.ic_file_cloud_done)
//			viewStatus.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null)
			btnSignIn.visibility = View.GONE
			viewDisconnect.visibility = View.VISIBLE
		} else {
			viewStatus.setText(R.string.account_status_disconnected)
//			val drawable = ContextCompat.getDrawable(this, R.drawable.ic_file_cloud_off)
//			viewStatus.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null)

			btnSignIn.visibility = View.VISIBLE
			viewDisconnect.visibility = View.GONE
		}
	}

}
