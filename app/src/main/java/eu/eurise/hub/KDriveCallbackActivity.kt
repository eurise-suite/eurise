package eu.eurise.hub

import android.app.Activity
import android.app.ActivityManager
import android.os.Bundle
import eu.eurise.core.cloud.KDriveLogin

/** Riceve il ritorno dal browser dopo l'accesso a kDrive e riporta in primo piano la schermata che lo aspetta. */
class KDriveCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KDriveLogin.complete(intent?.data)
        getSystemService(ActivityManager::class.java).appTasks.firstOrNull { it.taskInfo.id != taskId }?.moveToFront()
        finish()
    }
}
