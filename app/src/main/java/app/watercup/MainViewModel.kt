package app.watercup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

/** 화면과 코치를 잇는다. 주행 상태는 Coach 가 갖고 있어 화면이 닫혀도 유지된다. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val coach = Coach.get(app)

    val ui: StateFlow<UiState> = coach.ui

    fun startSpeed() = coach.startSpeed()
    fun askByVoice() = coach.askByVoice()
    fun selectSensitivity(value: Sensitivity) = coach.selectSensitivity(value)
    fun passengerFeedback(comfortable: Boolean) = coach.passengerFeedback(comfortable)
    fun restoreDefaultRim() = coach.restoreDefaultRim()
    fun setVisible(visible: Boolean) {
        coach.visible = visible
    }
}
