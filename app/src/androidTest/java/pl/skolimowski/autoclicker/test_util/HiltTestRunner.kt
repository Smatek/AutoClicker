package pl.skolimowski.autoclicker.test_util

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

// https://developer.android.com/training/dependency-injection/hilt-testing#instrumented-tests
// Android Studio shows as unused but it is used in app/build.gradle
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}