package me.fornever.avaloniarider.me.fornever.avaloniarider.actions

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import me.fornever.avaloniarider.actions.StartAvaloniaPreviewerAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.rider.ideaInterop.fileTypes.xaml.XamlFileType
import com.jetbrains.rider.util.idea.PsiFile

class TypedToFileActionHandler(private val avaloniaAction: StartAvaloniaPreviewerAction) : TypedActionHandler {

    override fun execute(editor: Editor, c: Char, context: DataContext) {
        val runnable = Runnable {
            insertCharacter(editor, editor.document, c)
            if (context.PsiFile!!.fileType is XamlFileType) {
                val xaml = editor.document.text
                avaloniaAction.updateView(xaml)
            }
        }
        WriteCommandAction.runWriteCommandAction(editor.project, runnable)
    }

    private fun insertCharacter(editor: Editor, document: Document, c: Char) {
        val caretModel = editor.caretModel
        document.insertString(caretModel.offset, c.toString())
        caretModel.moveToOffset(caretModel.offset + 1)
    }
}

/*
// delegate for type action behaviour
class XamlTypedHandlerDelegate : TypedHandlerDelegate() {

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.fileType is XamlFileType) {
            updateView(editor.document.text)
        }
        return Result.CONTINUE
    }
}
*/
