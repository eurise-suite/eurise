package eu.eurise.feature.launcher.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.feature.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** I dati di salute di oggi: battiti più recenti, passi, sonno della notte e calorie. */
data class HealthToday(
    val heartRate: Long?,
    val steps: Long?,
    val sleep: Duration?,
    val kcal: Double?,
    val updatedAt: Instant?,
)

enum class HealthState { Unavailable, NeedsPermission, Ready }

/**
 * Lettura da Health Connect, dove le app di orologi e band (Gadgetbridge sulle versioni recenti, e molte
 * altre) scrivono i loro dati. Si legge soltanto: Eurise non scrive nulla e nulla lascia il telefono.
 */
class HealthRepository(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    private fun client(): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    suspend fun state(): HealthState {
        val c = client() ?: return HealthState.Unavailable
        val granted = runCatching { c.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        return if (granted.containsAll(permissions)) HealthState.Ready else HealthState.NeedsPermission
    }

    suspend fun today(): HealthToday? = withContext(Dispatchers.IO) {
        val c = client() ?: return@withContext null
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant()
        val now = Instant.now()
        runCatching {
            val totals = c.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL, TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, now),
                ),
            )
            val sleep = c.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start.minus(Duration.ofHours(8)), now),
                ),
            )
            val heart = c.readRecords(
                ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(now.minus(Duration.ofHours(12)), now), ascendingOrder = false, pageSize = 1),
            ).records.firstOrNull()
            val lastSample = heart?.samples?.maxByOrNull { it.time }
            HealthToday(
                heartRate = lastSample?.beatsPerMinute,
                steps = totals[StepsRecord.COUNT_TOTAL],
                sleep = sleep[SleepSessionRecord.SLEEP_DURATION_TOTAL],
                kcal = totals[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
                updatedAt = lastSample?.time ?: heart?.endTime,
            )
        }.getOrNull()
    }

    /** Gadgetbridge su F-Droid: sincronizza orologi e band senza account. */
    fun gadgetbridgeIntent(): Intent =
        context.packageManager.getLaunchIntentForPackage("nodomain.freeyourgadget.gadgetbridge")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/nodomain.freeyourgadget.gadgetbridge/"))
}

/** Spiega a Health Connect (e all'utente) perché Eurise legge i dati di salute e cosa ne fa. */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EuriseTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.health_privacy_title), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.health_privacy_text), modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }
}
