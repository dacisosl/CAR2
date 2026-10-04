package app.car.parking

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import app.car.parking.platform.autolaunch.AutoLauncher
import app.car.parking.ui.AppViewModel
import app.car.parking.ui.CarRoot

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 시스템 다크 모드와 관계없이 시작은 어두운 아이콘. 어두운 UHD 테마에서는 CarRoot가 밝은 아이콘으로 바꾼다
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleEntry(intent)
        setContent { CarRoot(viewModel, onUnlockThen = ::unlockThen) }
        // 잠금 화면 위 표시는 자동 진입을 여는 중이거나 자동 진입 패널이 떠 있는 동안만 유지한다.
        // 후보가 이미 처리돼 패널이 열리지 않으면 바로 꺼진다
        lifecycleScope.launch {
            viewModel.lockScreenEntry.collect(::setLockScreenEntry)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEntry(intent)
    }

    override fun onResume() {
        super.onResume()
        // 권한·설정 재확인 + 실제 차량 연결 상태 보정 + 아직 보여 주지 않은 하차 후보 표시
        viewModel.onForeground()
    }

    private fun handleEntry(intent: Intent) {
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        val auto = !fromHistory && intent.getBooleanExtra(AutoLauncher.EXTRA_AUTO_ENTRY, false)
        if (fromHistory) return
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
