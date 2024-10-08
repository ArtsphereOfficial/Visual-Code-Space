/*
 * This file is part of Visual Code Space.
 *
 * Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Visual Code Space is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Visual Code Space.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.teixeira.vcspace.editor

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import com.teixeira.vcspace.editor.completion.CompletionListAdapter
import com.teixeira.vcspace.editor.completion.CustomCompletionLayout
import com.teixeira.vcspace.editor.events.OnContentChangeEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import java.io.File
import org.eclipse.tm4e.languageconfiguration.internal.model.CommentRule
import org.greenrobot.eventbus.EventBus

class VCSpaceEditor
@JvmOverloads
constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
  defStyleRes: Int = 0,
) : CodeEditor(context, attrs, defStyleAttr, defStyleRes) {

  private var textActions: TextActionsWindow? = TextActionsWindow(this)

  var file: File? = null
  var modified: Boolean = false

  val commentRule: CommentRule?
    get() = (editorLanguage as? TextMateLanguage)?.languageConfiguration?.comments

  init {
    getComponent(EditorTextActionWindow::class.java).isEnabled = false
    getComponent(EditorAutoCompletion::class.java).setLayout(CustomCompletionLayout())
    getComponent(EditorAutoCompletion::class.java).setAdapter(CompletionListAdapter())
    subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
      modified = event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT
      EventBus.getDefault().post(OnContentChangeEvent(file))
    }
    inputType = createInputTypeFlags()
  }

  override fun hideEditorWindows() {
    super.hideEditorWindows()
    textActions?.dismiss()
  }

  override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
    super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    if (!gainFocus) hideEditorWindows()
  }

  override fun release() {
    super.release()
    textActions = null
    file = null
  }

  companion object {

    fun createInputTypeFlags(): Int {
      return EditorInfo.TYPE_CLASS_TEXT or
        EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or
        EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }
  }
}
