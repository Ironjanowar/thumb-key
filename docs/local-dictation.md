# Local Whisper dictation

Thumb-Key can dictate without sending audio off-device. The feature is off by default.

1. Open Thumb-Key settings → **Offline dictation**.
2. Enable dictation and grant microphone permission.
3. Choose Spanish, English, or automatic detection.
4. Choose Tiny, Base (recommended), Small, or Turbo Q5 (experimental) and explicitly download it. Small and Turbo Q5 can be slower and use substantially more memory; prefer Base until you have measured whether their accuracy improvement is worth it on your device.
5. In the keyboard, swipe **left on the special key in the top-right corner** (the key that carries the language globe). Tap **Stop** to transcribe or **Cancel** to discard. Recording stops automatically after 60 seconds. The globe moves to the left-down diagonal of that same key, replacing the shortcut to another IME's voice input; layouts without that key keep the dictation entry on the right swipe of their numeric key.

Models are public multilingual GGML artifacts from `ggerganov/whisper.cpp`, pinned to Hugging Face revision `5359861c739e955e79d9a303bcbc70fb988958b1`; size and SHA-256 are checked before a `.part` file is atomically published. Turbo Q5 is the quantized `large-v3-turbo` model (`ggml-large-v3-turbo-q5_0.bin`, 574,041,195 bytes, SHA-256 `394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2`). Models live under Android's no-backup private directory. Audio is mono 16 kHz PCM held only in memory. A conservative energy check skips transcription for quiet input, but it cannot guarantee that every Whisper hallucination is prevented. Audio is discarded after success, error, cancellation, field change, keyboard hiding, or service destruction. INTERNET is used only for an explicit model download; recognition uses the vendored whisper.cpp v1.9.5 engine and works offline afterward.

Dictation is unavailable in password and `TYPE_NULL` fields. A result is committed only if the original input connection and selection are still current. Dictation does not press Enter, use the clipboard, or run personal macro expansion. Models and the separate personal-macros database are not included in Thumb-Key's existing settings backup. Performance and battery use remain to be measured on the OnePlus 13R.

Native source is vendored at `third_party/whisper.cpp`, pinned to commit `d1be6fde11ac6e0407606b4e42fe72d34add8037`, and built for arm64-v8a and x86_64 with 16 KiB maximum ELF page alignment.
