package eu.eurise.phone.call

import android.Manifest
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import eu.eurise.phone.data.PhoneData
import eu.eurise.phone.data.SpamFilter
import eu.eurise.phone.data.SpamListClient

/**
 * Il filtro antispam: Android chiede al Telefono di Eurise, app predefinita per le chiamate, cosa fare di ogni chiamata
 * in arrivo. Si guarda solo sul telefono (Rubrica, registro delle chiamate, scelte del filtro, elenco Eurise già scaricato
 * dall'app Eurise): niente esce.
 */
class SpamScreening : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val allow = CallResponse.Builder().build()
        if (Build.VERSION.SDK_INT >= 29 && details.callDirection != Call.Details.DIRECTION_INCOMING) { respondToCall(details, allow); return }
        val hidden = details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED
        val number = if (hidden) null else details.handle?.schemeSpecificPart
        val rules = SpamFilter.rules(this)
        val trusted = number != null && SpamFilter.trusted(this).any { PhoneData.same(it, number) }
        val (verdict, reason) = if (trusted) SpamFilter.Verdict.Ring to null else SpamFilter.decide(
            rules, number,
            inContacts = number != null && inContacts(number),
            calledBefore = number != null && calledBefore(number),
            country = SpamFilter.countryCode(this),
            recentRejects = number?.let { n ->
                val since = System.currentTimeMillis() - 15 * 60_000L
                SpamFilter.filtered(this).count { it.at >= since && PhoneData.same(it.number, n) }
            } ?: 0,
            listed = number != null && SpamListClient.match(this, number) != null,
        )
        val response = when (verdict) {
            SpamFilter.Verdict.Ring -> allow
            // Prima di Android 10 non si può far arrivare in silenzio: la chiamata suona, il filtro la segna comunque.
            SpamFilter.Verdict.Silence -> if (Build.VERSION.SDK_INT >= 29) CallResponse.Builder().setSilenceCall(true).build() else allow
            SpamFilter.Verdict.Reject -> CallResponse.Builder().setDisallowCall(true).setRejectCall(true).setSkipNotification(true).build()
        }
        if (reason != null) SpamFilter.record(this, SpamFilter.Filtered(number.orEmpty(), System.currentTimeMillis(), verdict, reason))
        respondToCall(details, response)
    }

    private fun inContacts(number: String): Boolean = runCatching {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return@runCatching false
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    private fun calledBefore(number: String): Boolean = runCatching {
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != android.content.pm.PackageManager.PERMISSION_GRANTED) return@runCatching false
        contentResolver.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER),
            "${CallLog.Calls.TYPE} = ?", arrayOf(CallLog.Calls.OUTGOING_TYPE.toString()), "${CallLog.Calls.DATE} DESC",
        )?.use { c ->
            var n = 0
            var found = false
            while (!found && n++ < 500 && c.moveToNext()) found = PhoneNumberUtils.compare(c.getString(0).orEmpty(), number)
            found
        } ?: false
    }.getOrDefault(false)
}
