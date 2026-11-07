package app.unlimitedmessaging.android

import android.app.Application
import app.unlimitedmessaging.android.service.GatewayNotifications
import app.unlimitedmessaging.android.work.WatchdogWorker

class GatewayApp : Application() {

    /** Every long-lived object of the app. See [AppContainer]. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        GatewayNotifications.createChannels(this)
        WatchdogWorker.schedule(this)
    }
}
