package com.filesecuritytool.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldStartShowsFileEncryptionAndPrimaryNavigation() {
        compose.onNodeWithText(compose.activity.getString(R.string.file_encrypt))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.nav_contacts))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.nav_keys))
            .assertIsDisplayed()
    }
}
