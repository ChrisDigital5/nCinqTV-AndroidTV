package app.ncinq.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.ncinq.tv.ui.NCinqTheme
import app.ncinq.tv.update.UpdateInstaller

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()
    private lateinit var updateInstaller: UpdateInstaller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        updateInstaller = UpdateInstaller(this)

        setContent {
            NCinqTheme {
                NCinqTvApp(
                    viewModel = viewModel,
                    onInstallUpdate = updateInstaller::download,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::updateInstaller.isInitialized) updateInstaller.onResume()
    }

    override fun onDestroy() {
        if (::updateInstaller.isInitialized) updateInstaller.dispose()
        super.onDestroy()
    }
}
