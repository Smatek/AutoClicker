package pl.skolimowski.autoclicker.ui.main

import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.skolimowski.autoclicker.R

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @BindValue
    val viewModel: MainActivityViewModel = mockk(relaxed = true)

    @Test
    fun testEvent() {
        every { viewModel.testMethod() } returns "testText"

        launchActivity<MainActivity>().use { scenario ->
            onView(withId(R.id.fab)).perform(click())

            onView(withId(com.google.android.material.R.id.snackbar_text))
                .check(matches(withText("testText")))
        }
    }
}