package eu.eurise.hub

import eu.eurise.feature.launcher.aod.AodBaseActivity

/** Il display a riposo: sta qui solo perché il modulo app conosce i dati della suite. */
class AodActivity : AodBaseActivity() {
    override val data get() = (application as EuriseApplication).data
}
