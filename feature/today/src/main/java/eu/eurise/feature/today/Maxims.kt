package eu.eurise.feature.today

import java.time.LocalDate

/** Una massima e chi l'ha detta (con l'opera, dove si sa). */
data class Maxim(val text: String, val author: String)

/**
 * La massima del giorno: una raccolta piccola e libera dentro l'APK (autori antichi e italiani di pubblico dominio,
 * traduzioni nostre, proverbi). Ogni giorno la sua, uguale per tutti e senza Internet.
 */
object Maxims {
    val all = listOf(
        Maxim("Non è che abbiamo poco tempo: è che ne perdiamo molto.", "Seneca, La brevità della vita"),
        Maxim("Mentre si rimanda, la vita passa.", "Seneca, Lettere a Lucilio"),
        Maxim("Nessun vento è favorevole per chi non sa a quale porto vuole andare.", "Seneca, Lettere a Lucilio"),
        Maxim("Non è povero chi ha poco, ma chi desidera di più.", "Seneca, Lettere a Lucilio"),
        Maxim("Mentre insegnano, gli uomini imparano.", "Seneca, Lettere a Lucilio"),
        Maxim("Considera ogni giorno come una vita a sé.", "Seneca, Lettere a Lucilio"),
        Maxim("Come sono i tuoi pensieri abituali, così sarà la tua mente.", "Marco Aurelio, Pensieri"),
        Maxim("Fa' ogni cosa come se fosse l'ultima della tua vita.", "Marco Aurelio, Pensieri"),
        Maxim("Il modo migliore di difendersi è non somigliare a chi ti ha fatto torto.", "Marco Aurelio, Pensieri"),
        Maxim("Ciò che non giova all'alveare non giova neppure all'ape.", "Marco Aurelio, Pensieri"),
        Maxim("Non sono le cose a turbarci, ma i giudizi che diamo delle cose.", "Epitteto, Manuale"),
        Maxim("Di alcune cose siamo padroni, di altre no.", "Epitteto, Manuale"),
        Maxim("Cogli il giorno, e fidati il meno possibile del domani.", "Orazio, Odi"),
        Maxim("Chi ha cominciato è a metà dell'opera.", "Orazio, Epistole"),
        Maxim("Finché c'è vita, si dice, c'è speranza.", "Cicerone, Lettere ad Attico"),
        Maxim("Possono, perché credono di potere.", "Virgilio, Eneide"),
        Maxim("La fortuna aiuta chi osa.", "Virgilio, Eneide"),
        Maxim("Il tempo fugge, e non torna.", "Virgilio, Georgiche"),
        Maxim("La goccia scava la pietra.", "Ovidio, Epistole dal Ponto"),
        Maxim("Fatti non foste a viver come bruti, ma per seguir virtute e canoscenza.", "Dante, Inferno XXVI"),
        Maxim("Vien dietro a me, e lascia dir le genti.", "Dante, Purgatorio V"),
        Maxim("Sì come una giornata bene spesa dà lieto dormire, così una vita bene usata dà lieto morire.", "Leonardo da Vinci"),
        Maxim("Chi poco pensa, molto erra.", "Leonardo da Vinci"),
        Maxim("Non si scende due volte nello stesso fiume.", "Eraclito"),
        Maxim("Una vita senza ricerca non è degna di essere vissuta.", "Socrate, nell'Apologia di Platone"),
        Maxim("È cattivo il piano che non si può cambiare.", "Publilio Siro, Sentenze"),
        Maxim("Nessun giorno senza una linea.", "Plinio il Vecchio, sul pittore Apelle"),
        Maxim("Sono un uomo: niente di ciò che è umano mi è estraneo.", "Terenzio"),
        Maxim("Mente sana in corpo sano.", "Giovenale, Satire"),
        Maxim("Abbiamo due orecchie e una bocca sola, per ascoltare più di quanto parliamo.", "Zenone di Cizio"),
        Maxim("Possiedi l'argomento: le parole verranno.", "Catone il Vecchio"),
        Maxim("Chi va piano va sano e va lontano.", "Proverbio"),
        Maxim("Il mattino ha l'oro in bocca.", "Proverbio"),
        Maxim("Non rimandare a domani quello che puoi fare oggi.", "Proverbio"),
    )

    /** La massima di [date]: cambia ogni giorno e ricomincia dopo l'ultima. */
    fun forDay(date: LocalDate): Maxim = all[Math.floorMod(date.toEpochDay(), all.size.toLong()).toInt()]
}
