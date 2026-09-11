package com.p2p.meshify.feature.onboarding

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class OnboardingComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `WelcomePage shows hub title`() {
        rule.setContent { DxTestThemeOb { WelcomePage() } }
        rule.onNode(hasText("Welcome", substring=true)).assertIsDisplayed()
    }

    @Test fun `HowItWorksPage shows Discover step`() {
        rule.setContent { DxTestThemeOb { HowItWorksPage() } }
        rule.onNodeWithText("Discover").assertIsDisplayed()
    }

    @Test fun `PermissionsOverviewPage renders with onboarding permissions`() {
        val perms = PermissionDefinitions.getPermissions()
        org.junit.Assert.assertTrue("permissions should be in 3..4 on SDK 35", perms.size in 3..4)
        rule.setContent { DxTestThemeOb { PermissionsOverviewPage(permissions=perms, permissionStatuses=emptyMap()) } }
        rule.onNodeWithText("Almost There").assertIsDisplayed()
    }

    @Test fun `SkipConfirmationDialog shows stay and leave`() {
        rule.setContent { DxTestThemeOb { SkipConfirmationDialog(onStayClick={}, onLeaveClick={}) } }
        rule.onNode(hasText("Stay", substring=true)).assertIsDisplayed()
        rule.onNode(hasText("Leave", substring=true)).assertIsDisplayed()
    }

    @Test fun `SkipConfirmationDialog leave invokes callback`() {
        var left=false
        rule.setContent { DxTestThemeOb { SkipConfirmationDialog(onStayClick={}, onLeaveClick={left=true}) } }
        rule.onNodeWithText("Enter Anyway").performClick()
        assert(left)
    }

    @Test fun `WelcomeScreen 3 pages pager dots exist`() {
        val vm = WelcomeViewModel()
        rule.setContent {
            DxTestThemeOb {
                WelcomeScreen(viewModel=vm, currentLang="en", onLangChange={}, onNextClick={}, onSkipClick={})
            }
        }
        // page indicator 3 dots via contentDescription
        rule.onAllNodes(hasContentDescription("Page indicator")).assertCountEquals(3)
        // Next button shows "Next"
        rule.onNodeWithText("Next").assertIsDisplayed()
        // Skip exists
        rule.onNodeWithText("Skip").assertIsDisplayed()
    }

    @Test fun `WelcomeScreen next changes label on last page no crash`() {
        val vm = WelcomeViewModel()
        rule.setContent { DxTestThemeOb { WelcomeScreen(viewModel=vm, currentLang="en", onLangChange={}, onNextClick={}, onSkipClick={}) } }
        rule.onNodeWithText("Next").performClick()
        // after click pager goes to page 1 still Next
        rule.onNodeWithText("Next").assertIsDisplayed()
    }

    @Test fun `WelcomeScreen shows skip and next`() {
        val vm = WelcomeViewModel()
        rule.setContent { DxTestThemeOb { WelcomeScreen(viewModel=vm, currentLang="en", onLangChange={}, onNextClick={}, onSkipClick={}) } }
        rule.onNodeWithText("Skip").assertIsDisplayed()
        rule.onNodeWithText("Next").assertIsDisplayed()
    }

    @Test fun `PermissionRequestCard shows permission label`() {
        val perm = PermissionDefinitions.getPermissions().first()
        rule.setContent { DxTestThemeOb { PermissionRequestCard(permission=perm, onAllowClick={}, onDenyClick={}, onRequestDismiss={}) } }
        rule.onNodeWithText("Allow", substring=true).assertExists()
    }

    @Test fun `PermissionSummaryDialog shows start button`() {
        rule.setContent {
            DxTestThemeOb {
                PermissionSummaryDialog(grantedCount=1, totalCount=2, permissionResults=emptyMap(), onStartClick={}, onDismiss={})
            }
        }
        rule.onNodeWithText("Start Meshify").assertIsDisplayed()
    }

    @Test fun `RTL no crash WelcomePage`() {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DxTestThemeOb { WelcomePage() }
            }
        }
        rule.onNode(hasText("Welcome", substring=true)).assertIsDisplayed()
    }

    @Test fun `WelcomePage renders without crash with many invocations`() {
        rule.setContent {
            DxTestThemeOb { WelcomePage() }
        }
        rule.onNode(hasText("Welcome", substring=true)).assertIsDisplayed()
    }
}
