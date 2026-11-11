package app.watercup

import android.content.pm.PackageManager
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
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(CarSpeedSource.PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startSpeed()
        } else {
            speedPermission.launch(CarSpeedSource.PERMISSION)
        }
        setContent {
            val state by viewModel.ui.collectAsStateWithLifecycle()
            WaterCupApp(state, onSensitivity = viewModel::selectSensitivity, onAsk = viewModel::askByVoice)
        }
    }
}
