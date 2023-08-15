package pl.skolimowski.autoclicker.ui.first

import android.provider.Settings
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.skolimowski.autoclicker.test_util.HiltTestUtil

// simple test to check if testing works correctly - running from AndroidStudio, with coverage, with jacoco report
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FirstFragmentTest {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @BindValue
    val viewModel: FirstFragmentViewModel = mockk(relaxed = true)

    @Before
    fun setUp() {
        Intents.init()
    }

    @Test
    fun actionCollector_OpenAccessibilitySettings() = runTest {
        val mutableSharedFlow = MutableSharedFlow<FirstFragmentActions>()
        every { viewModel.actionsSharedFlow } returns mutableSharedFlow

        HiltTestUtil.launchFragmentInHiltContainer<FirstFragment> { }

        mutableSharedFlow.emit(FirstFragmentActions.OpenAccessibilitySettings)

        intended(hasAction(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}