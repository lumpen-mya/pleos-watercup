package app.watercup.signal

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

enum class GearPos { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

data class SignalSnapshot(
    val gear: GearPos = GearPos.UNKNOWN,
    val brakePressed: Boolean = false,
    val steeringDeg: Float = 0f,
    val rearOccupied: Boolean = false,
    val turnSignal: String? = null,
    /** 값을 한 번이라도 받은 신호 이름 */
    val live: Set<String> = emptySet(),
    /** 신호 이름 → 마지막 오류 */
    val errors: Map<String, String> = emptyMap(),
)

/** Pleos Vehicle SDK에서 승차감 계산에 쓰는 신호를 구독한다. */
class VehicleSignals(context: Context) {
    private val vehicle = Vehicle(context.applicationContext)
    private var started = false

    @Volatile private var gear = GearPos.UNKNOWN
    @Volatile private var brake = false
    @Volatile private var steering = 0f
    @Volatile private var turnSignal: String? = null
    private val rearSeats = ConcurrentHashMap<SeatValue, Boolean>()
    private val live = ConcurrentHashMap.newKeySet<String>()
    private val errors = ConcurrentHashMap<String, String>()

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(value: VehicleGear?) {
            gear = value.toGearPos()
            live.add(GEAR)
        }

        override fun onFailed(e: Exception) = fail(GEAR, e)
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(value: Boolean?) {
            brake = value == true
            live.add(BRAKE)
        }

        override fun onFailed(e: Exception) = fail(BRAKE, e)
    }

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(value: Float?) {
            steering = value ?: 0f
            live.add(STEERING)
        }

        override fun onFailed(e: Exception) = fail(STEERING, e)
    }

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
            turnSignal = value?.name
            live.add(TURN_SIGNAL)
        }

        override fun onFailed(e: Exception) = fail(TURN_SIGNAL, e)
    }

    private val seatListeners = REAR_SEATS.associateWith { seat ->
        object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(value: Boolean?) {
                rearSeats[seat] = value == true
                live.add(SEAT)
            }

            override fun onFailed(e: Exception) = fail(SEAT, e)
        }
    }

    fun start() {
        if (started) return
        started = true
        guard("sdk") { vehicle.initialize() }

        guard(GEAR) {
            val api = vehicle.getGear()
            api.registerGearSelection(gearListener)
            api.getCurrentGearSelectionState({ gear = it.toGearPos(); live.add(GEAR) }, { fail(GEAR, it) })
        }
        guard(BRAKE) {
            val api = vehicle.getBrake()
            api.registerBrakePedalStatus(brakeListener)
            api.getCurrentBrakePedalStatus({ brake = it == true; live.add(BRAKE) }, { fail(BRAKE, it) })
        }
        guard(STEERING) {
            val api = vehicle.getSteeringWheel()
            api.registerSteeringWheelAngle(steeringListener)
            api.getCurrentSteeringWheelAngle({ steering = it ?: 0f; live.add(STEERING) }, { fail(STEERING, it) })
        }
        guard(TURN_SIGNAL) { vehicle.getTurnSignal().registerTurnSignalState(turnSignalListener) }
        guard(SEAT) {
            val api = vehicle.getSeat()
            seatListeners.forEach { (seat, listener) -> api.registerSeatOccupancy(SeatArea(seat), listener) }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        guard(GEAR) { vehicle.getGear().unregisterGearSelection(gearListener) }
        guard(BRAKE) { vehicle.getBrake().unregisterBrakePedalStatus(brakeListener) }
        guard(STEERING) { vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
        guard(TURN_SIGNAL) { vehicle.getTurnSignal().unregisterTurnSignalState(turnSignalListener) }
        guard(SEAT) {
            val api = vehicle.getSeat()
            seatListeners.forEach { (seat, listener) -> api.unregisterSeatOccupancy(SeatArea(seat), listener) }
        }
        guard("sdk") { vehicle.release() }
    }

    fun snapshot() = SignalSnapshot(
        gear = gear,
        brakePressed = brake,
        steeringDeg = steering,
        rearOccupied = rearSeats.values.any { it },
        turnSignal = turnSignal,
        live = live.toSet(),
        errors = errors.toMap(),
    )

    private fun VehicleGear?.toGearPos() = when (this) {
        VehicleGear.GEAR_PARK -> GearPos.PARK
        VehicleGear.GEAR_REVERSE -> GearPos.REVERSE
        VehicleGear.GEAR_NEUTRAL -> GearPos.NEUTRAL
        null, VehicleGear.GEAR_UNKNOWN -> GearPos.UNKNOWN
        else -> GearPos.DRIVE
    }

    private fun fail(name: String, e: Throwable) {
        errors[name] = e.message ?: e.javaClass.simpleName
        Log.w(TAG, "$name failed", e)
    }

    private inline fun guard(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            fail(name, e)
        }
    }

    companion object {
        const val GEAR = "기어"
        const val BRAKE = "브레이크"
        const val STEERING = "핸들"
        const val TURN_SIGNAL = "방향지시등"
        const val SEAT = "뒷좌석"
        private const val TAG = "WaterCup"
        private val REAR_SEATS = listOf(SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
    }
}
