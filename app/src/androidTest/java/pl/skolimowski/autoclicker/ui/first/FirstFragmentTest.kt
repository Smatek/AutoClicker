package pl.skolimowski.autoclicker.ui.first

import androidx.test.espresso.Espresso.onView
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
import pl.skolimowski.autoclicker.test_util.HiltTestUtil

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FirstFragmentTest {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @BindValue
    val viewModel: FirstFragmentViewModel = mockk(relaxed = true)

    @Test
    fun verifyText() {
        every { viewModel.testMethod() } returns "testText"

        HiltTestUtil.launchFragmentInHiltContainer<FirstFragment> { }

        onView(withId(R.id.textview_first)).check(matches(withText("testText")))
    }
}