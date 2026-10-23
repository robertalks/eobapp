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

    private var refreshJob: Job? = null

    private fun api() = EobApi(settings.getHost(), settings.getClientId(), settings.getClientSecret())

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

    fun saveSettings(host: String, clientId: String, clientSecret: String) {
        settings.save(host, clientId, clientSecret)
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
                if (s.ok == true && s.measured != null) {
                    TestResult.Ok(
                        "Connected — %.1f °C, heater %s".format(s.measured, if (s.relay == true) "ON" else "OFF")
                    )
                } else {
                    TestResult.Ok("Connected — no readings stored yet")
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
