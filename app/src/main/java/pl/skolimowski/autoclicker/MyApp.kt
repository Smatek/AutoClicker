package pl.skolimowski.autoclicker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class MyApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob())
}