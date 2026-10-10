# Personal macros

Personal macros turn a short key into text you type often. They live only on the device, in Thumb-Key's private `macros_db` Room database. Nothing is sent over the network, and the macro engine neither logs nor stores the text you type.

## Creating a macro

Settings → **Personal macros** → **+**. Each macro has a key and one or more expansions.

- **One expansion** — choose **Manual**, **Immediate**, or **After space or punctuation**.
- **Several expansions** — the macro is always **Manual**: the options appear as a row of buttons above the keyboard and you pick one. Adding a second expansion switches the macro to manual and the automatic triggers are disabled with an explanation.
- **Match uppercase and lowercase exactly** is off by default, so `jj`, `JJ` and `Jj` all trigger the same macro. Turning it on makes each spelling its own key.

A key can be any non-empty text that does not contain a space, a line break or any other whitespace character — letters, numbers, punctuation, symbols, emoji or a mixture, such as `@ME`, `#tag`, `.` or `🙂`. Whitespace is excluded because a space is exactly what separates the key from the text around it, so a key built from it could never be recognised. A badly chosen key (one that collides with your normal typing) is accepted; it is up to you.

A key matches only at the start of a word — after a space, a line break, punctuation or the start of the field. The rule applies to the key's **first character**: the character immediately before it must be a boundary (whitespace or punctuation), or the key must begin the field. So `relojjj` never triggers a `jj` macro, and a symbol key such as `@ME` expands after a space or at the start of the field ( `@ME`, `@ME`) but never glued to a letter (`hola@ME`).

## Typing with macros

- An **immediate** macro expands as soon as you finish the key: `jj` becomes `jajajajaja` on the spot.
- An **after space or punctuation** macro waits for the separator and keeps it: `jj ` becomes `jajajajaja `.
- A **manual** macro shows its options above the keyboard. Tapping one replaces the key only; touching nothing leaves the text as you typed it.

After an automatic expansion an **Undo expansion** action appears above the keyboard. It restores the key exactly as you wrote it — `JJ`, `JJ `, `Jj` — and does not expand it again. It stays available until you type, delete, move the cursor or change field; it never expires on its own.

The bar raises the keyboard by its own height while it is visible. The key grid itself never moves, resizes or gets covered.

## Safety limits

Macros are disabled for password fields, raw (`TYPE_NULL`) editors, Thumb-Key's own fields, selected or composing text, and editors that do not provide a complete confirmed extracted-text snapshot. Paste, sketch input, clipboard candidates and text-processor composition do not trigger expansions, and an expansion is never re-scanned for further macros.

## Backup

The **Backup and restore** screen exports the main settings database only. It does **not** include `macros_db`, so back up macro definitions manually if you reinstall or clear app data.
