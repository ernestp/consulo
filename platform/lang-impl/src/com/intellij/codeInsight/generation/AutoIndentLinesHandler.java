/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.RequiredWriteAction;

public class AutoIndentLinesHandler extends CodeInsightActionHandler.WriteActionAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.AutoIndentLinesHandler");

  @RequiredWriteAction
  @Override
  public void invokeInWriteAction(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)){
      return;
    }

    Document document = editor.getDocument();
    int startOffset;
    int endOffset;
    RangeMarker selectionEndMarker = null;
    if (editor.getSelectionModel().hasSelection()){
      startOffset = editor.getSelectionModel().getSelectionStart();
      endOffset = editor.getSelectionModel().getSelectionEnd();
      selectionEndMarker = document.createRangeMarker(endOffset, endOffset);
      endOffset -= 1;
    }
    else{
      startOffset = endOffset = editor.getCaretModel().getOffset();
    }
    int line1 = editor.offsetToLogicalPosition(startOffset).line;
    int col = editor.getCaretModel().getLogicalPosition().column;

    try{
      adjustLineIndent(file, document, startOffset, endOffset, line1, project);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }

    if (selectionEndMarker == null){
      if (line1 < document.getLineCount() - 1){
        if (document.getLineStartOffset(line1 + 1) + col >= document.getTextLength()) {
          col = document.getLineEndOffset(line1 + 1) - document.getLineStartOffset(line1 + 1);
        }
        LogicalPosition pos = new LogicalPosition(line1 + 1, col);
        editor.getCaretModel().moveToLogicalPosition(pos);
        editor.getSelectionModel().removeSelection();
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }
    else{
      if (!selectionEndMarker.isValid()) return;
      endOffset = selectionEndMarker.getEndOffset();
      editor.getSelectionModel().setSelection(startOffset, endOffset);
    }
  }

  private static void adjustLineIndent(PsiFile file,
                                Document document,
                                int startOffset, int endOffset, int line, Project project) {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    if (startOffset == endOffset) {
      int lineStart = document.getLineStartOffset(line);
      if (codeStyleManager.isLineToBeIndented(file, lineStart)) {
        codeStyleManager.adjustLineIndent(file, lineStart);
      }
    } else {
      codeStyleManager.adjustLineIndent(file, new TextRange(startOffset, endOffset));
    }
  }
}
