package eu.eurise.feature.clock.logic

import kotlin.random.Random

/** Un piccolo calcolo per spegnere la sveglia: abbastanza per svegliarsi, mai troppo difficile. */
data class Challenge(val text: String, val answer: Int) {
    companion object {
        fun make(random: Random = Random.Default): Challenge {
            val a = random.nextInt(12, 60)
            val b = random.nextInt(3, 30)
            return if (random.nextBoolean()) Challenge("$a + $b", a + b) else Challenge("$a − $b", a - b)
        }
    }
}
