package eu.eurise.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.eurise.desktop.Device
import eu.eurise.desktop.Server
import eu.eurise.desktop.Store
import eu.eurise.desktop.dataDir
import kotlin.concurrent.thread

/**
 * Il Ponte dentro l'app: lo stesso server di `eurise-ponte` (desktop/ponte) in un thread di sfondo. Se non c'è ancora
 * nessun telefono abbinato parte già pronto all'abbinamento e il codice da inquadrare compare nella finestra.
 * Se il Ponte da riga di comando è già acceso, quello resta il server e qui si mostra solo lo stato.
 */
class PonteState {
    private val store = Store(dataDir())
    private var server: Server? = null

    var devices by mutableStateOf(store.devices())
        private set

    /** Il codice da mostrare come QR mentre si aspetta il telefono; null quando non serve. */
    var pairingCode by mutableStateOf<String?>(null)
        private set

    var connected by mutableStateOf<Set<String>>(emptySet())
        private set

    fun start() {
        val pairing = devices.isEmpty()
        val s = Server(store)
        server = s
        thread(isDaemon = true, name = "ponte") {
            runCatching {
                s.run(pairing, show = { code -> pairingCode = code }, paired = {
                    pairingCode = null
                    devices = store.devices()
                })
            }
        }
        thread(isDaemon = true, name = "ponte-stato") {
            while (true) {
                Thread.sleep(3_000)
                connected = s.connected()
                val now = store.devices()
                if (now.map(Device::id) != devices.map(Device::id)) devices = now
            }
        }
    }

}
