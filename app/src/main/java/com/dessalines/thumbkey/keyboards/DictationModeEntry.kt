package com.dessalines.thumbkey.keyboards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import com.dessalines.thumbkey.R
import com.dessalines.thumbkey.utils.ColorVariant
import com.dessalines.thumbkey.utils.FontSizeVariant
import com.dessalines.thumbkey.utils.KeyAction
import com.dessalines.thumbkey.utils.KeyC
import com.dessalines.thumbkey.utils.KeyDisplay
import com.dessalines.thumbkey.utils.KeyItemC
import com.dessalines.thumbkey.utils.KeyboardC
import com.dessalines.thumbkey.utils.KeyboardDefinition
import com.dessalines.thumbkey.utils.KeyboardDefinitionModes

/**
 * Dictation legend. It takes the slot the language globe used to hold on the special key, so it
 * carries the same size and colour as its neighbours in that cluster.
 */
val TOGGLE_DICTATION_KEYC =
    KeyC(
        action = KeyAction.ToggleDictation,
        display = KeyDisplay.IconDisplay(Icons.Outlined.Mic),
        size = FontSizeVariant.SMALL,
        color = ColorVariant.MUTED,
        contentDescriptionRes = R.string.dictation_start,
    )

private val SWITCH_LANGUAGE_ACTION = KeyAction.SwitchLanguage
private val SWITCH_IME_VOICE_ACTION = KeyAction.SwitchIMEVoice

private fun KeyItemC.isSpecialClusterKey(): Boolean =
    left?.action == SWITCH_LANGUAGE_ACTION && bottomLeft?.action == SWITCH_IME_VOICE_ACTION

private fun KeyItemC.hasDictation(): Boolean =
    listOf(center, left, top, right, bottom, topLeft, topRight, bottomLeft, bottomRight)
        .any { it?.action == KeyAction.ToggleDictation }

private fun KeyboardC.anyDictation(): Boolean = arr.any { row -> row.any(KeyItemC::hasDictation) }

/**
 * Adds the dictation entry to a layout.
 *
 * The gesture lives on the special key of the top-right corner: swiping left starts dictation and
 * the language globe moves to the left-down diagonal, replacing the shortcut to another IME's voice
 * input. Layouts without that cluster keep the original numeric-key right swipe as a fallback so
 * dictation stays reachable everywhere.
 */
fun installDictationModeEntry(
    definition: KeyboardDefinition,
    enabled: Boolean,
): KeyboardDefinition {
    if (!enabled) return definition
    val modes = definition.modes
    if (allModes(modes).any(KeyboardC::anyDictation)) return definition

    val hasCluster = allModes(modes).any { mode -> mode.arr.any { row -> row.any(KeyItemC::isSpecialClusterKey) } }

    fun KeyboardC.install(): KeyboardC {
        var changed = false
        val next =
            arr.map { row ->
                row.map { item ->
                    when {
                        item.isSpecialClusterKey() -> {
                            changed = true
                            item.copy(left = TOGGLE_DICTATION_KEYC, bottomLeft = SWITCH_LANGUAGE_KEYC)
                        }

                        !hasCluster && (item.center.action as? KeyAction.ToggleNumericMode)?.enable == true && item.right == null -> {
                            changed = true
                            item.copy(right = TOGGLE_DICTATION_KEYC)
                        }

                        else -> {
                            item
                        }
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

private fun allModes(modes: KeyboardDefinitionModes): List<KeyboardC> =
    listOfNotNull(modes.main, modes.shifted, modes.numeric, modes.ctrled, modes.alted, modes.emoji)
