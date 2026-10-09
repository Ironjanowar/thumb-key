@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.dessalines.thumbkey.keyboards

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardBackspace
import androidx.compose.material.icons.outlined.Abc
import com.dessalines.thumbkey.R
import com.dessalines.thumbkey.editing.EditingCommand
import com.dessalines.thumbkey.utils.*

val TOGGLE_EDITING_TRUE_KEYC =
    KeyC(
        action = KeyAction.ToggleEditingMode(true),
        display = KeyDisplay.TextDisplay("✎"),
        color = ColorVariant.MUTED,
        contentDescriptionRes = R.string.editing_enter,
    )

/**
 * Leaving the panel, on the cell the letter keyboards keep their numeric key on: the clipboard
 * cluster keeps the exact directions it has there, so the hand does not have to relearn them.
 */
private val EDITING_EXIT_KEY =
    KeyItemC(
        center =
            KeyC(
                action = KeyAction.ToggleEditingMode(false),
                display = KeyDisplay.IconDisplay(Icons.Outlined.Abc),
                size = FontSizeVariant.LARGE,
                contentDescriptionRes = R.string.editing_exit,
            ),
        top = editingTextKey(EditingCommand.Copy, "⧉", R.string.editing_copy),
        topLeft = editingTextKey(EditingCommand.SelectAll, "▣", R.string.editing_select_all),
        topRight = editingTextKey(EditingCommand.Cut, "✂", R.string.editing_cut),
        bottomLeft = editingTextKey(EditingCommand.Undo, "↶", R.string.editing_undo),
        bottomRight = editingTextKey(EditingCommand.Redo, "↷", R.string.editing_redo),
        bottom = editingTextKey(EditingCommand.Paste, "▤", R.string.editing_paste),
        swipeType = SwipeNWay.EIGHT_WAY,
        backgroundColor = ColorVariant.SURFACE_VARIANT,
    )

/**
 * Backwards deletion, on the cell the letter keyboards keep their backspace on, so it is where it
 * always was. Deleting whole words lives on the key to its left.
 */
private val EDITING_BACKSPACE_KEY =
    KeyItemC(
        center =
            KeyC(
                action = KeyAction.Editing(EditingCommand.DeletePreviousGrapheme),
                display = KeyDisplay.IconDisplay(Icons.AutoMirrored.Outlined.KeyboardBackspace),
                size = FontSizeVariant.LARGE,
                color = ColorVariant.SECONDARY,
                contentDescriptionRes = R.string.editing_delete_previous_character,
            ),
        backgroundColor = ColorVariant.SURFACE_VARIANT,
    )

/** Word deletion either side of the cursor, with the tap deleting the character to its right. */
private val EDITING_FORWARD_DELETE_KEY =
    KeyItemC(
        center =
            editingTextKey(
                EditingCommand.DeleteNextGrapheme,
                "⌦",
                R.string.editing_delete_next_character,
                FontSizeVariant.SMALL,
            ),
        left = editingTextKey(EditingCommand.DeletePreviousWord, "⌫w", R.string.editing_delete_previous_word),
        right = editingTextKey(EditingCommand.DeleteNextWord, "w⌦", R.string.editing_delete_next_word),
        swipeType = SwipeNWay.TWO_WAY_HORIZONTAL,
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
                ),
                KeyItemC(
                    center = editingCenterKey(EditingCommand.MoveVisualUp, "↑", R.string.editing_visual_up),
                    top = editingTextKey(EditingCommand.MoveTextStart, "⤒", R.string.editing_text_start),
                    swipeType = SwipeNWay.TWO_WAY_VERTICAL,
                ),
                KeyItemC(
                    center = editingCenterKey(EditingCommand.MoveLineEnd, "⇥", R.string.editing_line_end),
                ),
                emptyEditingItem(),
            ),
            listOf(
                KeyItemC(
                    center = editingCenterKey(EditingCommand.MoveWordBackward, "↞", R.string.editing_previous_word),
                    left = editingTextKey(EditingCommand.MoveCharacterBackward, "←", R.string.editing_character_left),
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
                    center = editingCenterKey(EditingCommand.MoveWordForward, "↠", R.string.editing_next_word),
                    right = editingTextKey(EditingCommand.MoveCharacterForward, "→", R.string.editing_character_right),
                    swipeType = SwipeNWay.TWO_WAY_HORIZONTAL,
                ),
                EDITING_EXIT_KEY,
            ),
            listOf(
                emptyEditingItem(),
                KeyItemC(
                    center = editingCenterKey(EditingCommand.MoveVisualDown, "↓", R.string.editing_visual_down),
                    bottom = editingTextKey(EditingCommand.MoveTextEnd, "⤓", R.string.editing_text_end),
                    swipeType = SwipeNWay.TWO_WAY_VERTICAL,
                ),
                EDITING_FORWARD_DELETE_KEY,
                EDITING_BACKSPACE_KEY,
            ),
            listOf(
                SPACEBAR_KEY_ITEM,
                RETURN_KEY_ITEM,
            ),
        ),
    )

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
