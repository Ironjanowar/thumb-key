package com.dessalines.thumbkey.keyboards

import com.dessalines.thumbkey.utils.KeyAction
import com.dessalines.thumbkey.utils.KeyboardC
import com.dessalines.thumbkey.utils.KeyboardDefinition

/** Adds editing-mode entry only to numeric anchors whose left gesture is free. */
fun installEditingModeEntry(
    definition: KeyboardDefinition,
    enabled: Boolean,
): KeyboardDefinition {
    if (!enabled) return definition

    val modes = definition.modes
    val main = modes.main.installEditingModeEntry()
    val shifted = modes.shifted.installEditingModeEntry()
    val numeric = modes.numeric.installEditingModeEntry()
    val ctrled = modes.ctrled?.installEditingModeEntry()
    val alted = modes.alted?.installEditingModeEntry()
    val emoji = modes.emoji?.installEditingModeEntry()

    if (
        main === modes.main &&
        shifted === modes.shifted &&
        numeric === modes.numeric &&
        ctrled === modes.ctrled &&
        alted === modes.alted &&
        emoji === modes.emoji
    ) {
        return definition
    }

    return definition.copy(
        modes =
            modes.copy(
                main = main,
                shifted = shifted,
                numeric = numeric,
                ctrled = ctrled,
                alted = alted,
                emoji = emoji,
            ),
    )
}

private fun KeyboardC.installEditingModeEntry(): KeyboardC {
    var changed = false
    val transformed =
        arr.map { row ->
            row.map { item ->
                val isNumericAnchor =
                    (item.center.action as? KeyAction.ToggleNumericMode)?.enable == true
                if (isNumericAnchor && item.left == null) {
                    changed = true
                    item.copy(left = TOGGLE_EDITING_TRUE_KEYC)
                } else {
                    item
                }
            }
        }
    return if (changed) copy(arr = transformed) else this
}
