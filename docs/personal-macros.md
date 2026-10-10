# Personal macros

Personal macros are stored only on the device in Thumb-Key's private `macros_db` Room database. They are not sent over the network and typed text is not logged or stored by the macro engine.

The existing **Backup and restore** screen exports the main settings database only. It does **not** include `macros_db`; back up macro definitions manually if you need to reinstall or clear app data.

For safety, macros are disabled for password fields, raw (`TYPE_NULL`) editors, Thumb-Key's own fields, selected or composing text, and editors that do not provide a complete confirmed extracted-text snapshot. Paste, sketch input, clipboard candidates and text-processor composition do not trigger expansions.
