package com.dessalines.thumbkey.macros

enum class MacroMode { MANUAL, IMMEDIATE, ON_SEPARATOR }

data class MacroDefinition(
    val id: Long = 0,
    val key: String,
    val expansions: List<String>,
    val caseSensitive: Boolean = false,
    val mode: MacroMode = MacroMode.MANUAL,
)
