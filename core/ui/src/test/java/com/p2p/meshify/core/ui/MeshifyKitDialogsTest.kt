package com.p2p.meshify.core.ui

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.p2p.meshify.core.ui.components.DeleteConfirmationDialog
import com.p2p.meshify.core.ui.components.FullImageViewer
import com.p2p.meshify.core.ui.components.MeshifySelectionDialog
import com.p2p.meshify.core.ui.components.MeshifyTextInputDialog
import com.p2p.meshify.core.ui.components.SeedColorPickerGrid
import com.p2p.meshify.core.ui.components.ThemeSelectionBottomSheet
import com.p2p.meshify.domain.repository.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class MeshifyKitDialogsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `DeleteConfirmationDialog shows title and confirm button`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                DeleteConfirmationDialog(
                    title="Delete item?",
                    text="This cannot be undone.",
                    onConfirm={},
                    onDismiss={}
                )
            }
        }
        rule.onNodeWithText("Delete item?").assertIsDisplayed()
        rule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test fun `MeshifyTextInputDialog shows title and placeholder`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                MeshifyTextInputDialog(
                    title="Rename",
                    value="",
                    onValueChange={},
                    onConfirm={},
                    onDismiss={},
                    placeholder="Enter name"
                )
            }
        }
        rule.onNodeWithText("Rename").assertIsDisplayed()
        rule.onNodeWithText("Enter name").assertIsDisplayed()
    }

    @Test fun `MeshifyTextInputDialog shows error text when present`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                MeshifyTextInputDialog(
                    title="Rename",
                    value="",
                    onValueChange={},
                    onConfirm={},
                    onDismiss={},
                    errorText="Too short"
                )
            }
        }
        rule.onNodeWithText("Too short").assertIsDisplayed()
    }

    @Test fun `MeshifySelectionDialog renders selected option`() {
        val options = listOf("A", "B", "C")
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                MeshifySelectionDialog(
                    title="Pick",
                    options=options,
                    selectedOption="B",
                    onOptionSelected={},
                    onDismiss={},
                    optionLabel={ it }
                )
            }
        }
        rule.onNodeWithText("Pick").assertIsDisplayed()
        rule.onNodeWithText("B").assertIsDisplayed()
    }

    @Test fun `ThemeSelectionBottomSheet renders title`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                ThemeSelectionBottomSheet(
                    currentTheme=ThemeMode.SYSTEM,
                    onThemeSelected={},
                    onDismiss={}
                )
            }
        }
        rule.onNodeWithText("Choose Theme").assertIsDisplayed()
    }

    @Test fun `SeedColorPickerGrid renders without crash`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                SeedColorPickerGrid(selectedColor = Color.Red, onColorSelected = {})
            }
        }
    }

    @Test fun `FullImageViewer mounts without crash`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                FullImageViewer(imagePath="/fake/path.jpg", onDismiss={})
            }
        }
    }
}
