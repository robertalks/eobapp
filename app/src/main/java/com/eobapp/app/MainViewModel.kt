package com.eobapp.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eobapp.app.network.ApiException
import com.eobapp.app.network.DailyRuntime
import com.eobapp.app.network.EobApi
import com.eobapp.app.network.Reading
import com.eobapp.app.network.StatusResponse
import com.eobapp.app.network.readings
import com.eobapp.app.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Chart ranges offered on Home, in hours. */
val RANGES = listOf(6, 24, 168)

data class HomeState(
    val status: StatusResponse? = null,
    val readings: List<Reading> = emptyList(),
    /** Seconds between readings, from the server; used to bridge or break chart lines. */
    val interval: Int = 60,
    val daily: List<DailyRuntime> = emptyList(),
    val rangeHours: Int = 24,
    val loading: Boolean = false,
    /** Last refresh failed; earlier data (if any) is still shown underneath. */
    val error: String? = null,
    /** Phone clock (ms) of the last successful refresh. */
    val refreshedAt: Long? = null,
)

/** Progress of a target-temperature change from the app. */
sealed class TargetEdit {
    data object Saving : TargetEdit()
    data class Done(val message: String) : TargetEdit()
    data class Failed(val message: String) : TargetEdit()
}

sealed class TestResult {
    data object Running : TestResult()
    data class Ok(val summary: String) : TestResult()
    data class Failed(val message: String) : TestResult()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsRepository(application)

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _test = MutableStateFlow<TestResult?>(null)
    val test: StateFlow<TestResult?> = _test.asStateFlow()

    private val _targetEdit = MutableStateFlow<TargetEdit?>(null)
    val targetEdit: StateFlow<TargetEdit?> = _targetEdit.asStateFlow()

    private var refreshJob: Job? = null

    private fun api() = EobApi(
        settings.getHost(), settings.getClientId(), settings.getClientSecret(), settings.getControlToken()
    )

    /** Changes the low-tariff target (null = back to the scheduler's own setting). */
    fun setTarget(celsius: Double?) {
        if (_targetEdit.value is TargetEdit.Saving) return
        _targetEdit.value = TargetEdit.Saving
        viewModelScope.launch {
            try {
                val r = if (celsius == null) api().clearTarget() else api().setTarget(celsius)
                val t = r.target
                // Show the new value straight away; the scheduler re-reads the thermostat a few
                // seconds later, and the refresh below picks that up.
                _home.update { st ->
                    val status = st.status
                    val control = status?.control
                    if (status == null || control == null) st
                    else st.copy(
                        status = status.copy(
                            control = control.copy(target = t, source = if (celsius == null) control.source else "app")
                        )
                    )
                }
                val value = if (t != null) "%.1f °C".format(t) else "the scheduler default"
                _targetEdit.value = TargetEdit.Done(
                    if (r.appliedNow == true) "Target set to $value."
                    else "Saved $value — the heater stays off during high tariff and uses it from the next low tariff."
                )
                delay(4_000)
                refreshJob?.cancel()
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _targetEdit.value = TargetEdit.Failed(message(e))
            }
        }
    }

    fun clearTargetEdit() {
        if (_targetEdit.value !is TargetEdit.Saving) _targetEdit.value = null
    }

    /** Loads status, the chart range and the 14-day runtime together. */
    fun refresh() {
        if (!settings.isConfigured()) return
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _home.update { it.copy(loading = true) }
            val api = api()
            val hours = _home.value.rangeHours
            try {
                // coroutineScope, not bare async in launch: a failed request then surfaces
                // here as an ordinary exception instead of cancelling the ViewModel's job and
                // reaching the uncaught-exception handler (which would crash the app).
                val (status, history, daily) = coroutineScope {
                    val s = async { api.status() }
                    val h = async { api.history(hours) }
                    val d = async { api.daily(14) }
                    Triple(s.await(), h.await(), d.await())
                }
                _home.update {
                    it.copy(
                        status = status,
                        readings = history.readings(),
                        interval = history.interval ?: 60,
                        daily = daily,
                        loading = false,
                        error = null,
                        refreshedAt = System.currentTimeMillis(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _home.update { it.copy(loading = false, error = message(e)) }
            }
        }
    }

    fun setRange(hours: Int) {
        if (hours == _home.value.rangeHours) return
        _home.update { it.copy(rangeHours = hours, readings = emptyList()) }
        // A refresh already in flight is for the old range; restart it.
        refreshJob?.cancel()
        refresh()
    }

    fun saveSettings(host: String, clientId: String, clientSecret: String, controlToken: String) {
        settings.save(host, clientId, clientSecret, controlToken)
        // Data from a previous server must not linger under the new address.
        _home.value = HomeState(rangeHours = _home.value.rangeHours)
        refreshJob?.cancel()
        refresh()
    }

    /** Tries the values in the form, before they're saved. */
    fun testConnection(host: String, clientId: String, clientSecret: String) {
        _test.value = TestResult.Running
        viewModelScope.launch {
            _test.value = try {
                val s = EobApi(host, clientId.trim(), clientSecret.trim()).status()
                val ctl = s.control
                val control = when {
                    ctl == null -> " · this scheduler can't change the temperature yet"
                    ctl.enabled != true -> " · temperature control is off in config.yaml"
                    else -> " · temperature control available"
                }
                if (s.ok == true && s.measured != null) {
                    TestResult.Ok(
                        "Connected — %.1f °C, heater %s".format(s.measured, if (s.relay == true) "ON" else "OFF") + control
                    )
                } else {
                    TestResult.Ok("Connected — no readings stored yet$control")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TestResult.Failed(message(e))
            }
        }
    }

    fun clearTest() {
        _test.value = null
    }

    private fun message(e: Exception): String =
        if (e is ApiException) e.message.orEmpty() else (e.message ?: e.javaClass.simpleName)
}
