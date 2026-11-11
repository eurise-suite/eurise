package eu.eurise.phone.call

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Le chiamate in corso, come le passa il sistema a [CallService]. Le schermate le leggono da qui (flussi) e chiedono
 * qui le azioni: rispondere, chiudere, attesa, muto, vivavoce, toni. Vive finché il processo è vivo.
 */
object CallManager {
    /** Una chiamata vista dall'interfaccia: lo [state] è quello di Android ([Call.STATE_RINGING]…). */
    data class Item(val call: Call, val number: String, val state: Int, val connectedAt: Long, val conference: Boolean)

    private val _calls = MutableStateFlow<List<Item>>(emptyList())
    val calls: StateFlow<List<Item>> = _calls.asStateFlow()

    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = _audio.asStateFlow()

    internal var service: InCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = refresh()
        override fun onDetailsChanged(call: Call, details: Call.Details) = refresh()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = refresh()
    }

    private val tracked = LinkedHashSet<Call>()

    internal fun add(call: Call) {
        if (tracked.add(call)) call.registerCallback(callback)
        refresh()
    }

    internal fun remove(call: Call) {
        call.unregisterCallback(callback)
        tracked.remove(call)
        refresh()
    }

    internal fun setAudio(state: CallAudioState?) {
        _audio.value = state
    }

    private fun refresh() {
        _calls.value = tracked.filter { it.parent == null }.map { c ->
            Item(
                call = c,
                number = c.details?.handle?.schemeSpecificPart.orEmpty(),
                state = stateOf(c),
                connectedAt = c.details?.connectTimeMillis ?: 0L,
                conference = c.children.isNotEmpty(),
            )
        }
    }

    @Suppress("DEPRECATION")
    fun stateOf(call: Call): Int = if (android.os.Build.VERSION.SDK_INT >= 31) call.details.state else call.state

    /** La chiamata da mostrare in grande: quella che squilla, se c'è, altrimenti quella attiva o in composizione. */
    fun primary(list: List<Item> = calls.value): Item? =
        list.firstOrNull { it.state == Call.STATE_RINGING }
            ?: list.firstOrNull { it.state == Call.STATE_ACTIVE || it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
            ?: list.firstOrNull { it.state != Call.STATE_DISCONNECTED }

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)

    fun reject(call: Call) = call.reject(false, null)

    fun rejectWithMessage(call: Call, text: String) = call.reject(true, text)

    fun hangup(call: Call) {
        if (stateOf(call) == Call.STATE_RINGING) call.reject(false, null) else call.disconnect()
    }

    fun toggleHold(call: Call) {
        if (stateOf(call) == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    /** Passa all'altra chiamata (quella in attesa diventa attiva e viceversa). */
    fun swap() {
        val list = calls.value
        list.firstOrNull { it.state == Call.STATE_HOLDING }?.call?.unhold()
            ?: list.firstOrNull { it.state == Call.STATE_ACTIVE }?.call?.hold()
    }

    fun merge() {
        val active = calls.value.firstOrNull { it.state == Call.STATE_ACTIVE }?.call ?: return
        val other = calls.value.firstOrNull { it.state == Call.STATE_HOLDING }?.call ?: return
        active.conference(other)
    }

    fun setMuted(muted: Boolean) = service?.setMuted(muted)

    fun setSpeaker(on: Boolean) = service?.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)

    fun tone(call: Call, digit: Char) {
        call.playDtmfTone(digit)
        call.stopDtmfTone()
    }
}
