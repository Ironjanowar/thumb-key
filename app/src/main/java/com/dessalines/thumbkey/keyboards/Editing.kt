@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.dessalines.thumbkey.keyboards

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardBackspace
import androidx.compose.material.icons.outlined.Abc
import androidx.compose.material.icons.outlined.Inventory
import com.dessalines.thumbkey.R
import com.dessalines.thumbkey.editing.EditingCommand
import com.dessalines.thumbkey.utils.*

val TOGGLE_EDITING_TRUE_KEYC =
    KeyC(
        action = KeyAction.ToggleEditingMode(true),
        display = KeyDisplay.TextDisplay("✎"),
        size = FontSizeVariant.LARGE,
        color = ColorVariant.MUTED,
        contentDescriptionRes = R.string.editing_enter,
    )

val NUMERIC_EDITING_KEY_ITEM = NUMERIC_KEY_ITEM.copy(left = TOGGLE_EDITING_TRUE_KEYC)

private val EDITING_EXIT_KEY =
    KeyItemC(
        center =
            KeyC(
                action = KeyAction.ToggleEditingMode(false),
                display = KeyDisplay.IconDisplay(Icons.Outlined.Abc),
                size = FontSizeVariant.LARGE,
                contentDescriptionRes = R.string.editing_exit,
            ),
        backgroundColor = ColorVariant.SURFACE_VARIANT,
    )

fun editingKeyboard(
    markActive: Boolean,
    markLabel: String,
    selectingLabel: String,
): KeyboardC =
    KeyboardC(
        listOf(
            listOf(
                KeyItemC(
                    center = editingCenterKey(EditingCommand.MoveLineStart, "⇤", R.string.editing_line_start),
                    top = editingTextKey(EditingCommand.MoveTextStart, "⤒", R.string.editing_text_start),
                    swipeType = SwipeNWay.TWO_WAY_VERTICAL,
                ),
                editingItem(EditingCommand.MoveVisualUp, "↑", R.string.editing_visual_up),
                KeyItemC(
                    center = editingCenterKey(EditingCommand.MoveLineEnd, "⇥", R.string.editing_line_end),
                    bottom = editingTextKey(EditingCommand.MoveTextEnd, "⤓", R.string.editing_text_end),
                    swipeType = SwipeNWay.TWO_WAY_VERTICAL,
                ),
                emptyEditingItem(),
            ),
            listOf(
                KeyItemC(
                    center = editingCenterKey(EditingCommand.MoveCharacterBackward, "←", R.string.editing_character_left),
                    left = editingTextKey(EditingCommand.MoveWordBackward, "↞", R.string.editing_previous_word),
                    swipeType = SwipeNWay.TWO_WAY_HORIZONTAL,
                ),
                KeyItemC(
                    center =
                        KeyC(
                            action = KeyAction.Editing(EditingCommand.ToggleMark),
                            display = KeyDisplay.TextDisplay(if (markActive) selectingLabel else markLabel),
                            size = FontSizeVariant.SMALLEST,
                            color = if (markActive) ColorVariant.PRIMARY else ColorVariant.MUTED,
                            contentDescriptionRes = if (markActive) R.string.editing_selecting else R.string.editing_mark,
                            emphasized = markActive,
                        ),
                    top = editingTextKey(EditingCommand.SelectAll, "▣", R.string.editing_select_all),
                    bottom = editingTextKey(EditingCommand.SwapEnds, "⇄", R.string.editing_swap_ends),
                    swipeType = SwipeNWay.TWO_WAY_VERTICAL,
                ),
                KeyItemC(
                    center = editingCenterKey(EditingCommand.MoveCharacterForward, "→", R.string.editing_character_right),
                    right = editingTextKey(EditingCommand.MoveWordForward, "↠", R.string.editing_next_word),
                    swipeType = SwipeNWay.TWO_WAY_HORIZONTAL,
                ),
                EDITING_EXIT_KEY,
            ),
            listOf(
                KeyItemC(
                    center =
                        KeyC(
                            action = KeyAction.Noop,
                            display = KeyDisplay.IconDisplay(Icons.Outlined.Inventory),
                            color = ColorVariant.MUTED,
                            contentDescriptionRes = R.string.editing_clipboard,
                        ),
                    top = editingTextKey(EditingCommand.Copy, "⧉", R.string.editing_copy),
                    topRight = editingTextKey(EditingCommand.Cut, "✂", R.string.editing_cut),
                    bottom = editingTextKey(EditingCommand.Paste, "▤", R.string.editing_paste),
                    left = editingTextKey(EditingCommand.Undo, "↶", R.string.editing_undo),
                    right = editingTextKey(EditingCommand.Redo, "↷", R.string.editing_redo),
                    swipeType = SwipeNWay.EIGHT_WAY,
                ),
                editingItem(EditingCommand.MoveVisualDown, "↓", R.string.editing_visual_down),
                KeyItemC(
                    center =
                        KeyC(
                            action = KeyAction.Editing(EditingCommand.DeletePreviousGrapheme),
                            display = KeyDisplay.IconDisplay(Icons.AutoMirrored.Outlined.KeyboardBackspace),
                            size = FontSizeVariant.LARGE,
                            contentDescriptionRes = R.string.editing_delete_previous_character,
                        ),
                    left = editingTextKey(EditingCommand.DeletePreviousWord, "⌫w", R.string.editing_delete_previous_word),
                    right = editingTextKey(EditingCommand.DeleteNextWord, "w⌦", R.string.editing_delete_next_word),
                    top = editingTextKey(EditingCommand.DeleteNextGrapheme, "⌦", R.string.editing_delete_next_character),
                    swipeType = SwipeNWay.EIGHT_WAY,
                    backgroundColor = ColorVariant.SURFACE_VARIANT,
                ),
                emptyEditingItem(),
            ),
            listOf(
                emptyEditingItem(widthMultiplier = 3),
                emptyEditingItem(),
            ),
        ),
    )

private fun editingItem(
    command: EditingCommand,
    symbol: String,
    @StringRes description: Int,
): KeyItemC = KeyItemC(center = editingCenterKey(command, symbol, description))

private fun editingCenterKey(
    command: EditingCommand,
    symbol: String,
    @StringRes description: Int,
): KeyC = editingTextKey(command, symbol, description, FontSizeVariant.LARGE)

private fun editingTextKey(
    command: EditingCommand,
    symbol: String,
    @StringRes description: Int,
    size: FontSizeVariant = FontSizeVariant.SMALL,
): KeyC =
    KeyC(
        action = KeyAction.Editing(command),
        display = KeyDisplay.TextDisplay(symbol),
        size = size,
        contentDescriptionRes = description,
    )

private fun emptyEditingItem(widthMultiplier: Int = 1): KeyItemC =
    KeyItemC(
        center = KeyC(action = KeyAction.Noop, display = null),
        widthMultiplier = widthMultiplier,
        backgroundColor = ColorVariant.SURFACE_VARIANT,
    )
