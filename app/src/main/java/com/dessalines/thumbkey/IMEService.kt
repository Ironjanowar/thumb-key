package com.dessalines.thumbkey

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dessalines.thumbkey.db.DEFAULT_CLIPBOARD_HISTORY_ENABLED
import com.dessalines.thumbkey.db.DEFAULT_DISABLE_FULLSCREEN_EDITOR
import com.dessalines.thumbkey.db.DEFAULT_SHOW_ON_SCREEN_KEYBOARD
import com.dessalines.thumbkey.db.DEFAULT_USE_PRIVATE_CLIPBOARD
import com.dessalines.thumbkey.dictation.AndroidDictationEditor
import com.dessalines.thumbkey.dictation.DictationFieldPolicy
import com.dessalines.thumbkey.dictation.DictationRuntime
import com.dessalines.thumbkey.editing.AndroidEditingPort
import com.dessalines.thumbkey.editing.EditingController
import com.dessalines.thumbkey.macros.AndroidMacroEditorPort
import com.dessalines.thumbkey.macros.MacroController
import com.dessalines.thumbkey.ui.components.keyboard.SketchState
import com.dessalines.thumbkey.utils.KeyboardDefinition
import com.dessalines.thumbkey.utils.KeyboardLayout
import com.dessalines.thumbkey.utils.TAG
import com.dessalines.thumbkey.utils.ThumbKeyClipboardManager
import com.dessalines.thumbkey.utils.resolveKeyboardDefinition
import com.dessalines.thumbkey.utils.toBool
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class IMEService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private fun setupView(): ComposeKeyboardView {
        val app = application as ThumbkeyApplication
        val settingsRepo = app.appSettingsRepository
        val clipboardRepo = app.clipboardRepository

        val appSettings = settingsRepo.appSettings.value
        val layoutIndex = appSettings?.keyboardLayout
        if (layoutIndex != null) {
            currentKeyboardDefinition =
                resolveKeyboardDefinition(KeyboardLayout.entries[layoutIndex], appSettings.keyModifications)
        }

        val view = ComposeKeyboardView(this, settingsRepo, clipboardRepo)
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }
        view.let {
            view.setViewTreeLifecycleOwner(this)
            view.setViewTreeViewModelStoreOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
        }
        return view
    }

    var currentKeyboardDefinition: KeyboardDefinition? = null

    val editingMarkActive = mutableStateOf(false)
    val editingController: EditingController by lazy {
        EditingController(AndroidEditingPort(this)) { active -> editingMarkActive.value = active }
    }
    val macroController: MacroController by lazy {
        MacroController(
            AndroidMacroEditorPort(
                connection = { runCatching { appInputConnection }.getOrNull() },
                inputType = { currentInputEditorInfo?.inputType ?: EditorInfo.TYPE_NULL },
                typingIntoOwnField = {
                    isTypingIntoOwnField || currentInputEditorInfo?.packageName == packageName
                },
                compositionActive = { macroCompositionActive },
            ),
        )
    }
    private var inputGeneration = 0L
    val dictationRuntime: DictationRuntime by lazy {
        DictationRuntime(
            this,
            lifecycleScope,
            AndroidDictationEditor(
                connection = { runCatching { appInputConnection }.getOrNull() },
                fieldToken = { "$inputGeneration:${System.identityHashCode(runCatching { appInputConnection }.getOrNull())}" },
                selection = { selectionStart to selectionEnd },
                beforeCommit = {
                    macroController.beforeExplicitEdit()
                    currentKeyboardDefinition?.settings?.textProcessor?.handleFinishInput(this)
                },
            ),
        )
    }

    fun startDictation() {
        val settings = (application as ThumbkeyApplication).appSettingsRepository.appSettings.value ?: return
        if (!settings.dictationEnabled.toBool() || isTypingIntoOwnField || !DictationFieldPolicy.allows(currentInputEditorInfo)) return
        dictationRuntime.start(settings.dictationModel, settings.dictationLanguage)
    }

    fun stopDictation() = dictationRuntime.stop()

    fun cancelDictation() = dictationRuntime.cancel()

    // While set, the keys type into this field of the keyboard's own, like sketch mode's
    // search, instead of the app's
    private var inputRedirect: InputConnection? = null

    fun setInputRedirect(connection: InputConnection?) {
        inputRedirect = connection
        macroController.beforeExplicitEdit()
    }

    /**
     * True while the keys type into a text field of the keyboard's own, like sketch mode's
     * search. The editing panel cannot drive the app's editor then, so it stays off.
     */
    val isTypingIntoOwnField: Boolean
        get() = inputRedirect != null

    // The framework's connection is null while no text field is bound, and the framework itself
    // calls this. Kotlin would check a platform value returned as non-null and throw, so pass it
    // through unchecked, as the Java method does. Nullable would break every caller.
    override fun getCurrentInputConnection(): InputConnection = inputRedirect ?: unchecked(super.getCurrentInputConnection())

    /** The app's text field, even while the keys type into the keyboard's own field. */
    val appInputConnection: InputConnection get() = super.getCurrentInputConnection()

    // Sketch mode's drawing, kept while the keyboard is closed and opened again
    val sketchState = SketchState()
    private var clipboardManager: ThumbKeyClipboardManager? = null

    /**
     * This is called every time the keyboard is brought up.
     * You can't use onCreate, because that can't pick up new numeric inputs
     */
    override fun onStartInput(
        attribute: EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInput(attribute, restarting)
        inputGeneration++
        cancelDictation()
        editingController.startSession()
        macroController.startSession()
        macroCompositionActive = false
        setInputRedirect(null)
        // A new keyboard opens on the letters: close sketch mode's search and lists, but keep
        // its drawing
        sketchState.searching = false
        sketchState.list = null
        val view = this.setupView()
        this.setInputView(view)
    }

    // Lifecycle Methods
    private var lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)

    private fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)

    override val lifecycle = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Initialize clipboard manager
        val app = application as ThumbkeyApplication
        clipboardManager = ThumbKeyClipboardManager(this, app.clipboardRepository)
        clipboardManager?.startListening()
        clipboardManager?.clearExpired()
        lifecycleScope.launch {
            (application as ThumbkeyApplication).macroRepository.macros.collectLatest(macroController::updateDefinitions)
        }
    }

    override fun onDestroy() {
        dictationRuntime.close()
        clipboardManager?.stopListening()
        clipboardManager = null
        super.onDestroy()
        handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    // Cursor update Methods
    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)

        cursorMoved =
            if (ignoreCursorMove) {
                ignoreCursorMove = false
                false
            } else {
                Log.d(TAG, "cursor moved")
                cursorAnchorInfo.selectionStart != selectionStart ||
                    cursorAnchorInfo.selectionEnd != selectionEnd
            }

        currentKeyboardDefinition?.settings?.textProcessor?.handleCursorUpdate(
            this,
            selectionStart,
            selectionEnd,
            cursorAnchorInfo.selectionStart,
            cursorAnchorInfo.selectionEnd,
        )

        selectionStart = cursorAnchorInfo.selectionStart
        selectionEnd = cursorAnchorInfo.selectionEnd

        // Cursor anchors also move when the field's content changes without a typing commit or a
        // selection callback; hide a candidate the confirmed snapshot no longer backs.
        macroController.onContentChanged()
    }

    override fun onEvaluateInputViewShown(): Boolean {
        val settingsRepo = (application as ThumbkeyApplication).appSettingsRepository
        val settings = settingsRepo.appSettings.getValue()
        val showOnScreenKeyboard =
            (settings?.showOnScreenKeyboard ?: DEFAULT_SHOW_ON_SCREEN_KEYBOARD).toBool()

        // Always call super implementation because Android docs says:
        // "If you override this method you must call through to the superclass implementation."
        // https://developer.android.com/reference/android/inputmethodservice/InputMethodService#onEvaluateInputViewShown()
        return super.onEvaluateInputViewShown() ||
            showOnScreenKeyboard
    }

    // Disable the fullscreen text editor if set by the user
    override fun onUpdateExtractingVisibility(ei: EditorInfo) {
        val settingsRepo = (application as ThumbkeyApplication).appSettingsRepository
        val settings = settingsRepo.appSettings.getValue()
        if ((settings?.disableFullscreenEditor ?: DEFAULT_DISABLE_FULLSCREEN_EDITOR).toBool()) {
            ei.imeOptions =
                ei.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        }
        super.onUpdateExtractingVisibility(ei)
    }

    fun didCursorMove(): Boolean = cursorMoved

    fun ignoreNextCursorMove() {
        // This gets reset on the next call to `onUpdateCursorAnchorInfo`
        ignoreCursorMove = true
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        cancelDictation()
        // Must run before super, which commits any composing text as it stands.
        // Running after it would let the processor write its pending text a second time.
        currentKeyboardDefinition?.settings?.textProcessor?.handleFinishInput(this)
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        cancelDictation()
        editingController.finishSession()
        macroController.finishSession()
        macroCompositionActive = false
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        macroCompositionActive = candidatesStart >= 0 || candidatesEnd >= 0
        selectionStart = newSelStart
        selectionEnd = newSelEnd
        editingController.onSelectionUpdate(newSelStart, newSelEnd)
        macroController.onSelectionChanged()
        if (dictationRuntime.controller.cancelIfDestinationChanged()) dictationRuntime.cancel()
    }

    override fun onWindowHidden() {
        cancelDictation()
        super.onWindowHidden()
    }

    private var ignoreCursorMove: Boolean = false
    private var macroCompositionActive: Boolean = false
    private var cursorMoved: Boolean = false
    private var selectionStart: Int = 0
    private var selectionEnd: Int = 0

    // ViewModelStore Methods
    override val viewModelStore = ViewModelStore()

    // SaveStateRegistry Methods
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    fun clipboardUsePrivate(): Boolean {
        val settingsRepo = (application as ThumbkeyApplication).appSettingsRepository
        val settings = settingsRepo.appSettings.getValue()
        val clipboardHistoryEnabled = (settings?.clipboardHistoryEnabled ?: DEFAULT_CLIPBOARD_HISTORY_ENABLED).toBool()
        val usePrivateClipboard = (settings?.usePrivateClipboard ?: DEFAULT_USE_PRIVATE_CLIPBOARD).toBool()
        return clipboardHistoryEnabled && usePrivateClipboard
    }

    fun clipboardAddPrivateClip(text: String): Unit? = clipboardManager?.addPrivateClip(text)

    fun clipboardWasLastCopyDoneViaSystem(): Boolean = clipboardManager?.wasLastCopyOperationDoneViaSystem() ?: true

    fun clipboardGetLastClip(): String? = clipboardManager?.getLastClip()
}

// Returns `value` without Kotlin's null check, for a platform value that may be null
@Suppress("UNCHECKED_CAST")
private fun <T> unchecked(value: T?): T = value as T
