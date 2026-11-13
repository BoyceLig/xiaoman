package dev.xiaoman.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.xiaoman.app.AppGraph
import dev.xiaoman.app.settings.AppSettings
import dev.xiaoman.app.settings.ModelSettings
import dev.xiaoman.app.settings.SettingsStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The 测试连接 button under each model: an unreachable endpoint must print a reason under the button
 * and leave the app running (an exception thrown here used to reach the composition coroutine).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun store(name: String, model: ModelSettings): SettingsStore {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        return SettingsStore(prefs, prefs).apply { save(AppSettings(chat = model, operator = model)) }
    }

    @Test
    fun `an unreachable model prints the reason instead of crashing`() {
        val store = store("settings-screen-test", ModelSettings("http://127.0.0.1:1/api/v3", "m", "sk-test"))
        compose.setContent {
            SettingsScreen(AppGraph(RuntimeEnvironment.getApplication(), givenSettings = store))
        }

        compose.onAllNodesWithText("测试连接")[0].performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("网络连不上", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("连接失败", substring = true)[0].assertExists()
    }

    @Test
    fun `the button stays disabled until both base url and model are filled`() {
        val store = store("settings-screen-test-blank", ModelSettings(ModelSettings.DEFAULT_BASE_URL, "", ""))
        compose.setContent {
            SettingsScreen(AppGraph(RuntimeEnvironment.getApplication(), givenSettings = store))
        }

        compose.onAllNodesWithText("测试连接")[0].assertIsNotEnabled()
        compose.onAllNodesWithText("先填 Base URL 和模型名称", substring = true)[0].assertExists()
    }
}
