package cz.spojenci.android.activity

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import cz.spojenci.android.R

class MainActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		findViewById(R.id.main_email_login)?.setOnClickListener { EmailLoginActivity.start(this) }
		findViewById(R.id.main_social_login)?.setOnClickListener { SocialLoginActivity.start(this) }
	}
}
