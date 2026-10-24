package com.eobapp.app.network

import com.google.gson.annotations.SerializedName

/**
 * Shapes of eob_scheduler's stats API (eob_stats.py). Every field is nullable on purpose: Gson
 * ignores Kotlin's null-safety, and a missing field must show as "–" rather than crash.
 */

/** GET /api/status */
data class StatusResponse(
    @SerializedName("ok") val ok: Boolean? = null,
    @SerializedName("low_tariff") val lowTariff: Boolean? = null,
    @SerializedName("next_tariff_change") val nextTariffChange: TariffChange? = null,
    /** Unix seconds of the latest reading. */
    @SerializedName("ts") val ts: Long? = null,
    /** Seconds since that reading, as the server saw it. */
    @SerializedName("age") val age: Long? = null,
    @SerializedName("measured") val measured: Double? = null,
    /** Target temperature; 0.0 while the scheduler has the thermostat forced off. */
    @SerializedName("desired") val desired: Double? = null,
    @SerializedName("relay") val relay: Boolean? = null,
    /** "on" or "off" (forced off by the scheduler). */
    @SerializedName("mode") val mode: String? = null,
    /** Unix seconds when the relay last switched. */
    @SerializedName("since") val since: Long? = null,
    /** Absent on schedulers without the control API. */
    @SerializedName("control") val control: ControlInfo? = null,
)

/** What the scheduler allows the app to change (part of /api/status). */
data class ControlInfo(
    @SerializedName("enabled") val enabled: Boolean? = null,
    @SerializedName("min") val min: Double? = null,
    @SerializedName("max") val max: Double? = null,
    @SerializedName("step") val step: Double? = null,
    /** The temperature low-tariff periods run at. */
    @SerializedName("target") val target: Double? = null,
    /** "app" when set from here, otherwise the scheduler's temperature_source ("config"/"device"). */
    @SerializedName("source") val source: String? = null,
)

/** Answer to POST/DELETE /api/target. */
data class TargetResult(
    @SerializedName("ok") val ok: Boolean? = null,
    @SerializedName("target") val target: Double? = null,
    /** false during high tariff: the heater stays off, the target applies at the next low tariff. */
    @SerializedName("applied_now") val appliedNow: Boolean? = null,
)

data class TariffChange(
    @SerializedName("ts") val ts: Long? = null,
    @SerializedName("to_low") val toLow: Boolean? = null,
)

/**
 * GET /api/history?hours=N. Rows are compact arrays rather than objects:
 * [ts, measured, target, relay(0/1), low_tariff(0/1/null)].
 */
data class HistoryResponse(
    @SerializedName("since") val since: Long? = null,
    @SerializedName("interval") val interval: Int? = null,
    @SerializedName("rows") val rows: List<List<Double?>>? = null,
)

/** One parsed history row. */
data class Reading(
    val ts: Long,
    val measured: Double,
    val target: Double,
    val relay: Boolean,
    /** null when the server couldn't tell (tariff not configured). */
    val lowTariff: Boolean?,
)

fun HistoryResponse.readings(): List<Reading> = rows.orEmpty().mapNotNull { r ->
    val ts = r.getOrNull(0) ?: return@mapNotNull null
    val measured = r.getOrNull(1) ?: return@mapNotNull null
    Reading(
        ts = ts.toLong(),
        measured = measured,
        target = r.getOrNull(2) ?: 0.0,
        relay = (r.getOrNull(3) ?: 0.0) != 0.0,
        lowTariff = r.getOrNull(4)?.let { it != 0.0 },
    )
}

/** GET /api/daily?days=N — heater runtime in minutes per local day, split by tariff. */
data class DailyRuntime(
    /** "YYYY-MM-DD" in the scheduler's timezone. */
    @SerializedName("day") val day: String = "",
    @SerializedName("low") val low: Int = 0,
    @SerializedName("high") val high: Int = 0,
    @SerializedName("unknown") val unknown: Int = 0,
) {
    val total: Int get() = low + high + unknown
}
