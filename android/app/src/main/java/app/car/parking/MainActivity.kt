package app.car.parking

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.car.parking.platform.autolaunch.AutoLauncher
import app.car.parking.ui.AppViewModel
import app.car.parking.ui.CarRoot

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleEntry(intent)
        setContent { CarRoot(viewModel, onUnlockThen = ::unlockThen) }
        // 자동 진입 패널을 저장하거나 닫으면 잠금 화면 위 표시를 해제한다
        lifecycleScope.launch {
            viewModel.drawer.map { it.open }.distinctUntilChanged().drop(1).collect { open ->
                if (!open) setLockScreenEntry(false)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEntry(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshChecks()
    }

    private fun handleEntry(intent: Intent) {
        val auto = intent.getBooleanExtra(AutoLauncher.EXTRA_AUTO_ENTRY, false)
        // 자동 진입일 때만 잠금 화면 위 표시·화면 켜기. 보안 잠금 해제는 우회하지 않는다
        setLockScreenEntry(auto)
        viewModel.handleEntry(
            candidateId = intent.getStringExtra(AutoLauncher.EXTRA_CANDIDATE_ID),
            openPanel = intent.getBooleanExtra(AutoLauncher.EXTRA_OPEN_PANEL, false),
            autoEntry = auto,
        )
    }

    @Suppress("DEPRECATION")
    fun setLockScreenEntry(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(enabled)
            setTurnScreenOn(enabled)
        } else {
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (enabled) window.addFlags(flags) else window.clearFlags(flags)
        }
    }

    /**
     * 사진 선택·촬영처럼 인증이 필요한 동작 전에 정상 시스템 잠금 해제를 요청한다.
     * 잠겨 있지 않으면 바로 실행한다.
     */
    private fun unlockThen(action: () -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            action()
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = action()
        })
    }
}
