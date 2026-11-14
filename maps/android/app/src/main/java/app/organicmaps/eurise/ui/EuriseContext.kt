package app.organicmaps.eurise.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** L'attività dietro un contesto (Compose dà un ContextWrapper): serve ai pannelli della suite per aprire e toccare. */
internal tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
