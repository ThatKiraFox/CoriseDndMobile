package net.kira.corisednd

import android.app.Application
import net.kira.corisednd.data.AppContainer

class CoriseApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}