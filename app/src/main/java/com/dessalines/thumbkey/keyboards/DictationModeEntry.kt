package com.dessalines.thumbkey.keyboards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import com.dessalines.thumbkey.R
import com.dessalines.thumbkey.utils.FontSizeVariant
import com.dessalines.thumbkey.utils.KeyAction
import com.dessalines.thumbkey.utils.KeyC
import com.dessalines.thumbkey.utils.KeyDisplay
import com.dessalines.thumbkey.utils.KeyboardC
import com.dessalines.thumbkey.utils.KeyboardDefinition

val TOGGLE_DICTATION_KEYC =
    KeyC(
        action = KeyAction.ToggleDictation,
        display = KeyDisplay.IconDisplay(Icons.Outlined.Mic),
        size = FontSizeVariant.SMALLEST,
        contentDescriptionRes = R.string.dictation_start,
    )

fun installDictationModeEntry(
    definition: KeyboardDefinition,
    enabled: Boolean,
): KeyboardDefinition {
    if (!enabled) return definition
    val modes = definition.modes

    fun KeyboardC.install(): KeyboardC {
        var changed = false
        val next =
            arr.map { row ->
                row.map { item ->
                    val anchor = (item.center.action as? KeyAction.ToggleNumericMode)?.enable == true
                    if (anchor && item.right == null) {
                        changed = true
                        item.copy(right = TOGGLE_DICTATION_KEYC)
                    } else {
                        item
                    }
                }
            }
        return if (changed) copy(arr = next) else this
    }
    val main = modes.main.install()
    val shifted = modes.shifted.install()
    val numeric = modes.numeric.install()
    val ctrled = modes.ctrled?.install()
    val alted = modes.alted?.install()
    val emoji = modes.emoji?.install()
    if (main === modes.main && shifted === modes.shifted && numeric === modes.numeric && ctrled === modes.ctrled && alted === modes.alted &&
        emoji === modes.emoji
    ) {
        return definition
    }
    return definition.copy(
        modes = modes.copy(main = main, shifted = shifted, numeric = numeric, ctrled = ctrled, alted = alted, emoji = emoji),
    )
}
