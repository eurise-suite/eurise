package eu.eurise.hub.alfred

import android.content.Context
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.weather.MeteoRepository
import eu.eurise.core.data.weather.MeteoText
import eu.eurise.core.data.alfred.AlfredPrefs
import eu.eurise.feature.assistant.BriefEvent
import eu.eurise.feature.assistant.BriefFacts
import eu.eurise.feature.assistant.BriefQuote
import eu.eurise.feature.assistant.BriefingSource
import eu.eurise.core.data.alfred.addressSuffix
import eu.eurise.feature.launcher.markets.MarketsRepository
import eu.eurise.feature.launcher.news.BuiltInFeeds
import eu.eurise.feature.launcher.news.FeedGroup
import eu.eurise.feature.launcher.news.NewsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

/**
 * I fatti per il punto di Alfred, dalle stesse fonti della Scrivania: calendario del telefono, meteo della suite,
 * Borse e notizie delle schede (con le scelte e gli interruttori del centro privacy di sempre). Ogni fonte ha pochi
 * secondi: se non risponde, Alfred ne fa a meno e lo dice in meno parole.
 */
class SuiteBriefingSource(private val context: Context) : BriefingSource {
    override suspend fun facts(now: LocalDateTime): BriefFacts = coroutineScope {
        val data = EuriseData.create(context)
        val today = now.toLocalDate()
        val eventsToday = async { withTimeoutOrNull(4_000) { data.calendar.observeEvents(today, today).first() }.orEmpty() }
        val eventsTomorrow = async { withTimeoutOrNull(4_000) { data.calendar.observeEvents(today.plusDays(1), today.plusDays(1)).first() }.orEmpty() }
        val meteo = async {
            withTimeoutOrNull(6_000) {
                val repo = MeteoRepository(context)
                repo.cached()?.takeIf { it.forecast != null } ?: runCatching { repo.load() }.getOrNull()
            }
        }
        val markets = async { withTimeoutOrNull(6_000) { runCatching { MarketsRepository(context).quotes() }.getOrNull() }.orEmpty() }
        val news = async { withTimeoutOrNull(6_000) { runCatching { NewsRepository(context).latest() }.getOrNull() }.orEmpty() }

        val story = meteo.await()?.let { m -> m.forecast?.let { f -> MeteoText.story(f, m.alerts, now) } }
        val foreign = BuiltInFeeds.filter { it.group == FeedGroup.Foreign }.map { it.name }.toSet()
        val items = news.await()
        BriefFacts(
            now = now,
            address = addressSuffix(AlfredPrefs(context)),
            today = eventsToday.await().map(::brief),
            tomorrow = eventsTomorrow.await().map(::brief),
            weatherTitle = story?.title,
            weatherBody = story?.body,
            markets = markets.await().map { BriefQuote(it.name, it.changePercent) },
            italy = items.filter { it.source !in foreign }.map { it.title },
            world = items.filter { it.source in foreign }.map { it.title },
        )
    }

    private fun brief(e: CalendarEvent) = BriefEvent(e.title, e.start, e.allDay, e.location)
}
