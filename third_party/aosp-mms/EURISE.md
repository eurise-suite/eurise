# AOSP MMS (pdu): lettura e scrittura dei messaggi MMS

Codice di Android Open Source Project (licenza Apache 2.0), nella versione mantenuta in `FossifyOrg/mmslib`
(che a sua volta viene da `klinker41/android-smsmms`): solo i pacchetti `com.google.android.mms.pdu_alt`
(PduParser, PduComposer, PduPersister e le classi dei messaggi), `util_alt` (cache e accesso al database degli MMS)
e tre classi di base. Serve a Eurise Messaggi per scaricare, salvare e mandare gli MMS con le API di sistema
(SmsManager.downloadMultimediaMessage / sendMultimediaMessage): niente rete propria, niente librerie compilate.

Modifiche di Eurise:
- `com.klinker.android.logger.Log` sostituito da `android.util.Log`, `ExternalLogger` da `Log.w`;
- PduPersister: niente `SubscriptionIdChecker` (si salva la SIM se indicata, -1 = predefinita);
- SendReq: il numero proprio non si legge (servirebbe un permesso in più);
- DrmConvertSession: i codici di `android.provider.Downloads.Impl` (API nascosta) sono copiati come costanti.
Sorgente: https://github.com/FossifyOrg/mmslib (library/src/main/java/com/google/android/mms).
