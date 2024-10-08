package com.teixeira.vcspace.editor

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.blankj.utilcode.util.FileIOUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.teixeira.vcspace.databinding.LayoutCodeEditorBinding
import com.teixeira.vcspace.events.OnPreferenceChangeEvent
import com.teixeira.vcspace.preferences.PREF_EDITOR_DELETELINEONBACKSPACE_KEY
import com.teixeira.vcspace.preferences.PREF_EDITOR_DELETETABONBACKSPACE_KEY
import com.teixeira.vcspace.preferences.PREF_EDITOR_FONTLIGATURES_KEY
import com.teixeira.vcspace.preferences.PREF_EDITOR_FONTSIZE_KEY
import com.teixeira.vcspace.preferences.PREF_EDITOR_FONT_KEY
import com.teixeira.vcspace.preferences.PREF_EDITOR_INDENT_KEY
import com.teixeira.vcspace.preferences.PREF_EDITOR_LINENUMBER_KEY
import com.teixeira.vcspace.preferences.PREF_EDITOR_STICKYSCROLL_KEY
import com.teixeira.vcspace.preferences.PREF_EDITOR_WORDWRAP_KEY
import com.teixeira.vcspace.preferences.editorDeleteLineOnBackspace
import com.teixeira.vcspace.preferences.editorDeleteTabOnBackspace
import com.teixeira.vcspace.preferences.editorFont
import com.teixeira.vcspace.preferences.editorFontLigatures
import com.teixeira.vcspace.preferences.editorFontSize
import com.teixeira.vcspace.preferences.editorIndent
import com.teixeira.vcspace.preferences.editorLineNumber
import com.teixeira.vcspace.preferences.editorStickyScroll
import com.teixeira.vcspace.preferences.editorUseTab
import com.teixeira.vcspace.preferences.editorWordWrap
import com.teixeira.vcspace.providers.GrammarProvider
import com.teixeira.vcspace.resources.R
import com.teixeira.vcspace.utils.cancelIfActive
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.LineSeparator
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@SuppressLint("ViewConstructor")
class CodeEditorView(context: Context, file: File) : LinearLayout(context) {

  private val binding = LayoutCodeEditorBinding.inflate(LayoutInflater.from(context))

  private val editorScope = CoroutineScope(Dispatchers.Default)

  val editor: VCSpaceEditor
    get() = binding.editor

  val modified: Boolean
    get() = editor.modified

  val file: File?
    get() = editor.file

  init {
    EventBus.getDefault().register(this)
    binding.editor.apply {
      colorScheme = createColorScheme()
      lineSeparator = LineSeparator.LF
      this.file = file
    }
    binding.searcher.bindSearcher(editor.searcher)
    addView(binding.root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    configureEditor()
    readFile(file)
  }

  private fun readFile(file: File) {
    setLoading(true)
    editorScope.launch {
      val content = FileIOUtils.readFile2String(file)
      val language = createLanguage()

      withContext(Dispatchers.Main) {
        binding.editor.setText(content, null)
        postRead(language)
      }
    }
  }

  private fun postRead(language: Language) {
    setLoading(false)

    editor.setEditorLanguage(language)
  }

  fun confirmReload() {
    if (modified) {
      MaterialAlertDialogBuilder(context)
        .setTitle(R.string.file_reload)
        .setMessage(R.string.file_reload_unsaved_message)
        .setPositiveButton(R.string.yes) { _, _ -> readFile(file!!) }
        .setNegativeButton(R.string.no, null)
        .show()
    } else readFile(file!!)
  }

  fun undo() = editor.undo()

  fun redo() = editor.redo()

  fun canUndo() = editor.canUndo()

  fun canRedo() = editor.canRedo()

  fun setModified(modified: Boolean) {
    editor.modified = modified
  }

  fun setFile(file: File) {
    editor.file = file
  }

  fun release() {
    EventBus.getDefault().unregister(this)
    editorScope.cancelIfActive("Editor has been released")
    editor.release()
  }

  suspend fun saveFile(): Boolean {
    return if (modified && FileIOUtils.writeFileFromString(file, editor.text.toString())) {
      setModified(false)
      true
    } else false
  }

  fun beginSearchMode() {
    binding.searcher.beginSearchMode()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onSharedPreferenceChanged(event: OnPreferenceChangeEvent) {
    when (event.prefKey) {
      PREF_EDITOR_FONT_KEY -> updateEditorFont()
      PREF_EDITOR_FONTSIZE_KEY -> updateFontSize()
      PREF_EDITOR_INDENT_KEY -> updateEditorIndent()
      PREF_EDITOR_STICKYSCROLL_KEY -> updateStickyScroll()
      PREF_EDITOR_FONTLIGATURES_KEY -> updateFontLigatures()
      PREF_EDITOR_WORDWRAP_KEY -> updateWordWrap()
      PREF_EDITOR_LINENUMBER_KEY -> updateLineNumbers()
      PREF_EDITOR_DELETELINEONBACKSPACE_KEY -> updateDeleteEmptyLineFast()
      PREF_EDITOR_DELETETABONBACKSPACE_KEY -> updateDeleteTabs()
    }
  }

  private fun configureEditor() {
    updateEditorFont()
    updateFontSize()
    updateEditorIndent()
    updateStickyScroll()
    updateFontLigatures()
    updateWordWrap()
    updateLineNumbers()
    updateDeleteEmptyLineFast()
    updateDeleteTabs()
  }

  private fun updateEditorFont() {
    val font = ResourcesCompat.getFont(context, editorFont)
    editor.typefaceText = font
    editor.typefaceLineNumber = font
  }

  private fun updateFontSize() {
    editor.setTextSize(editorFontSize)
  }

  private fun updateEditorIndent() {
    editor.tabWidth = editorIndent
  }

  private fun updateStickyScroll() {
    editor.props.stickyScroll = editorStickyScroll
  }

  private fun updateFontLigatures() {
    editor.isLigatureEnabled = editorFontLigatures
  }

  private fun updateWordWrap() {
    editor.isWordwrap = editorWordWrap
  }

  private fun updateLineNumbers() {
    editor.isLineNumberEnabled = editorLineNumber
  }

  private fun updateDeleteEmptyLineFast() {
    editor.props.deleteEmptyLineFast = editorDeleteLineOnBackspace
  }

  private fun updateDeleteTabs() {
    editor.props.deleteMultiSpaces = if (editorDeleteTabOnBackspace) -1 else 1
  }

  private fun setLoading(loading: Boolean) {
    binding.progress.isVisible = loading
    editor.isEditable = !loading
  }

  private fun createColorScheme(): EditorColorScheme {
    return try {
      TextMateColorScheme.create(ThemeRegistry.getInstance())
    } catch (e: Exception) {
      EditorColorScheme()
    }
  }

  private suspend fun createLanguage(): Language {
    val scopeName: String? = GrammarProvider.findScopeByFileExtension(file?.extension)

    return if (scopeName != null) {
      TextMateLanguage.create(scopeName, GrammarRegistry.getInstance(), false).apply {
        tabSize = editorIndent
        useTab(editorUseTab)
      }
    } else EmptyLanguage()
  }
}
