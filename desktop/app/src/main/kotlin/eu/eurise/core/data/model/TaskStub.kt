package eu.eurise.core.data.model

/** Le costanti di `Task` (core/data, Models.kt) che `QuickAdd` usa; sul computer non serve il resto. Stessi valori. */
class Task private constructor() {
    companion object {
        const val PRIORITY_NONE = 0
        const val PRIORITY_HIGH = 1
        const val PRIORITY_MEDIUM = 5
    }
}
