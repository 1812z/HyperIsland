package io.github.hyperisland.compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import io.github.hyperisland.MainActivity
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.navigation.HyperIslandApp
import io.github.hyperisland.compose.theme.HyperIslandTheme

class ComposeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = FlutterPrefsRepository(this)
        if (!prefs.getBoolean("pref_onboarding_completed", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            val repository = remember { FlutterPrefsRepository(this) }
            HyperIslandTheme(repository) {
                HyperIslandApp(repository)
            }
        }
    }
}
