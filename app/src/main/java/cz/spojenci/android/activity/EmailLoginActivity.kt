package cz.spojenci.android.activity

import android.Manifest.permission.READ_CONTACTS
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.LoaderManager.LoaderCallbacks
import android.content.Context
import android.content.CursorLoader
import android.content.Intent
import android.content.Loader
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import butterknife.bindView
import cz.spojenci.android.R
import java.util.*

/**
 * A login screen that offers login via email/password.
 */
class EmailLoginActivity : AppCompatActivity(), LoaderCallbacks<Cursor> {

	companion object {

		/** Id to identity READ_CONTACTS permission request. */
		private const val REQUEST_READ_CONTACTS = 0

		fun start(context: Context) {
			val intent = Intent(context, EmailLoginActivity::class.java)
			context.startActivity(intent)
		}
	}

	/**
	 * Keep track of the login task to ensure we can cancel it if requested.
	 */
	private var mAuthTask: UserLoginTask? = null

	private val mEmailView: AutoCompleteTextView by bindView(R.id.email)
	private val mPasswordView: EditText by bindView(R.id.password)
	private val mProgressView: View by bindView(R.id.login_progress)
	private val mLoginFormView: View by bindView(R.id.login_form)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_login)
		populateAutoComplete()

		mPasswordView.setOnEditorActionListener(TextView.OnEditorActionListener { textView, id, keyEvent ->
			if (id == R.id.login || id == EditorInfo.IME_NULL) {
				attemptLogin()
				return@OnEditorActionListener true
			}
			false
		})

		val btnEmailSignIn = findViewById(R.id.email_sign_in_button)
		btnEmailSignIn?.setOnClickListener { attemptLogin() }
	}

	private fun populateAutoComplete() {
		if (!mayRequestContacts()) {
			return
		}

		loaderManager.initLoader(0, null, this)
	}

	private fun mayRequestContacts(): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			return true
		}
		if (checkSelfPermission(READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
			return true
		}
		if (shouldShowRequestPermissionRationale(READ_CONTACTS)) {
			Snackbar.make(mEmailView, R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE).setAction(android.R.string.ok) { requestPermissions(arrayOf(READ_CONTACTS), REQUEST_READ_CONTACTS) }
		} else {
			requestPermissions(arrayOf(READ_CONTACTS), REQUEST_READ_CONTACTS)
		}
		return false
	}

	/**
	 * Callback received when a permissions request has been completed.
	 */
	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
	                                        grantResults: IntArray) {
		if (requestCode == REQUEST_READ_CONTACTS) {
			if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				populateAutoComplete()
			}
		}
	}


	/**
	 * Attempts to sign in or register the account specified by the login form.
	 * If there are form errors (invalid email, missing fields, etc.), the
	 * errors are presented and no actual login attempt is made.
	 */
	private fun attemptLogin() {
		if (mAuthTask != null) {
			return
		}

		// Reset errors.
		mEmailView.error = null
		mPasswordView.error = null

		// Store values at the time of the login attempt.
		val email = mEmailView.text.toString()
		val password = mPasswordView.text.toString()

		var cancel = false
		var focusView: View? = null

		// Check for a valid password, if the user entered one.
		if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
			mPasswordView.error = getString(R.string.error_invalid_password)
			focusView = mPasswordView
			cancel = true
		}

		// Check for a valid email address.
		if (TextUtils.isEmpty(email)) {
			mEmailView.error = getString(R.string.error_field_required)
			focusView = mEmailView
			cancel = true
		} else if (!isEmailValid(email)) {
			mEmailView.error = getString(R.string.error_invalid_email)
			focusView = mEmailView
			cancel = true
		}

		if (cancel) {
			// There was an error; don't attempt login and focus the first
			// form field with an error.
			focusView?.requestFocus()
		} else {
			// Show a progress spinner, and kick off a background task to
			// perform the user login attempt.
			showProgress(true)
			mAuthTask = UserLoginTask(email, password)
			mAuthTask?.execute()
		}
	}

	private fun isEmailValid(email: String): Boolean {
		//TODO: Replace this with your own logic
		return email.contains("@")
	}

	private fun isPasswordValid(password: String): Boolean {
		//TODO: Replace this with your own logic
		return password.length > 4
	}

	/**
	 * Shows the progress UI and hides the login form.
	 */
	private fun showProgress(show: Boolean) {
		// On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
		// for very easy animations. If available, use these APIs to fade-in
		// the progress spinner.
		val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime)

		mLoginFormView.visibility = if (show) View.GONE else View.VISIBLE
		mLoginFormView.animate().setDuration(shortAnimTime.toLong()).alpha(
				(if (show) 0 else 1).toFloat()).setListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				mLoginFormView.visibility = if (show) View.GONE else View.VISIBLE
			}
		})

		mProgressView.visibility = if (show) View.VISIBLE else View.GONE
		mProgressView.animate().setDuration(shortAnimTime.toLong()).alpha(
				(if (show) 1 else 0).toFloat()).setListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				mProgressView.visibility = if (show) View.VISIBLE else View.GONE
			}
		})
	}

	override fun onCreateLoader(i: Int, bundle: Bundle): Loader<Cursor> {
		return CursorLoader(this,
				// Retrieve data rows for the device user's 'profile' contact.
				Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI,
						ContactsContract.Contacts.Data.CONTENT_DIRECTORY), ProfileQuery.PROJECTION,

				// Select only email addresses.
				ContactsContract.Contacts.Data.MIMETYPE + " = ?", arrayOf(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE),

				// Show primary email addresses first. Note that there won't be
				// a primary email address if the user hasn't specified one.
				ContactsContract.Contacts.Data.IS_PRIMARY + " DESC")
	}

	override fun onLoadFinished(cursorLoader: Loader<Cursor>, cursor: Cursor) {
		val emails = ArrayList<String>()
		cursor.moveToFirst()
		while (!cursor.isAfterLast) {
			emails.add(cursor.getString(ProfileQuery.ADDRESS))
			cursor.moveToNext()
		}

		addEmailsToAutoComplete(emails)
	}

	override fun onLoaderReset(cursorLoader: Loader<Cursor>) {

	}

	private interface ProfileQuery {
		companion object {
			val PROJECTION = arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS, ContactsContract.CommonDataKinds.Email.IS_PRIMARY)

			val ADDRESS = 0
			val IS_PRIMARY = 1
		}
	}


	private fun addEmailsToAutoComplete(emailAddressCollection: List<String>) {
		//Create adapter to tell the AutoCompleteTextView what to show in its dropdown list.
		val adapter = ArrayAdapter(this@EmailLoginActivity,
				android.R.layout.simple_dropdown_item_1line, emailAddressCollection)

		mEmailView.setAdapter(adapter)
	}

	/**
	 * Represents an asynchronous login/registration task used to authenticate
	 * the user.
	 */
	inner class UserLoginTask internal constructor(private val mEmail: String, private val mPassword: String) : AsyncTask<Void, Void, Boolean>() {

		override fun doInBackground(vararg params: Void): Boolean {
			// TODO: attempt authentication against a network service.

			try {
				// Simulate network access.
				Thread.sleep(2000)
			} catch (e: InterruptedException) {
				return false
			}


			// TODO: register the new account here.
			return true
		}

		override fun onPostExecute(success: Boolean) {
			mAuthTask = null
			showProgress(false)

			if (success) {
				finish()
			} else {
				mPasswordView.error = getString(R.string.error_incorrect_password)
				mPasswordView.requestFocus()
			}
		}

		override fun onCancelled() {
			mAuthTask = null
			showProgress(false)
		}
	}

}

