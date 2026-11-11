package app.watercup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.watercup.signal.CarSpeedSource
import app.watercup.ui.WaterCupApp

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val speedPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.startSpeed()
            // 권한 창을 겹쳐 띄우지 않도록 속도 권한 답을 받은 뒤에 이어서 묻는다
            askNotificationsOnce()
        }

    /** 거절해도 기록·음성 코칭 서비스는 그대로 동작한다. 상단 알림만 보이지 않는다. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoachService.start(this)
        if (checkSelfPermission(CarSpeedSource.PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startSpeed()
            askNotificationsOnce()
        } else {
            speedPermission.launch(CarSpeedSource.PERMISSION)
        }
        setContent {
            val state by viewModel.ui.collectAsStateWithLifecycle()
            WaterCupApp(
                state,
                onSensitivity = viewModel::selectSensitivity,
                onAsk = viewModel::askByVoice,
                onPassenger = viewModel::passengerFeedback,
                onRestore = viewModel::restoreDefaultRim,
                // 작은 화면 모드를 넓은 화면에서 확인할 때: am start --ez compact true
                forceCompact = intent?.getBooleanExtra("compact", false) == true,
            )
        }
    }

    /** Android 13 이상에서 알림 권한을 정차 중에 한 번만 묻는다. 거절하면 다시 묻지 않는다. */
    private fun askNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        if (viewModel.ui.value.phase == Phase.DRIVING) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)) return
        prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onStart() {
        super.onStart()
        viewModel.setVisible(true)
    }

    override fun onStop() {
        viewModel.setVisible(false)
        super.onStop()
    }

    private companion object {
        const val PREFS = "watercup"
        const val KEY_NOTIFICATION_ASKED = "notification_asked"
    }
}
