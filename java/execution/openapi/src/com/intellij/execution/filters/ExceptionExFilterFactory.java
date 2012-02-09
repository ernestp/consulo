/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution.filters;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author gregsh
 */
public class ExceptionExFilterFactory implements ExceptionFilterFactory {
  @Override
  public Filter create(GlobalSearchScope searchScope) {
    return new MyFilter(searchScope);
  }

  private static class MyFilter implements Filter, FilterMixin {
    private final GlobalSearchScope myScope;

    public MyFilter(@NotNull final GlobalSearchScope scope) {
      myScope = scope;
    }

    public Result applyFilter(final String line, final int textEndOffset) {
      return null;
    }

    @Override
    public boolean shouldRunHeavy() {
      return true;
    }

    @Override
    public void applyHeavyFilter(final Document copiedFragment,
                                 final int startOffset,
                                 int startLineNumber,
                                 final Consumer<AdditionalHighlight> consumer) {
      for (int i = 0; i < copiedFragment.getLineCount(); i++) {
        final int lineStartOffset = copiedFragment.getLineStartOffset(i);
        final int lineEndOffset = copiedFragment.getLineEndOffset(i);
        final ExceptionWorker worker = new ExceptionWorker(myScope.getProject(), myScope);
        Result result = null;
        AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
        try {
          String text = copiedFragment.getText(new TextRange(lineStartOffset, lineEndOffset));
          worker.execute(text, lineEndOffset);
          result = worker.getResult();
          if (result == null) continue;
          int offset = result.hyperlinkInfo instanceof OpenFileHyperlinkInfo
                       ? ((OpenFileHyperlinkInfo)result.hyperlinkInfo).getDescriptor().getOffset()
                       : -1;
          PsiFile psiFile = worker.getFile();
          if (offset <= 0 || psiFile == null) continue;
          PsiElement element = psiFile.findElementAt(offset);
          PsiTryStatement parent = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class, true, PsiClass.class);
          PsiCodeBlock tryBlock = parent != null? parent.getTryBlock() : null;
          if (tryBlock == null || !tryBlock.getTextRange().contains(offset)) continue;
        }
        finally {
          token.finish();
        }
        Trinity<TextRange,TextRange,TextRange> info = worker.getInfo();
        int off = startOffset + lineStartOffset;
        final TextAttributes attributes = result.highlightAttributes;
        consumer.consume(new AdditionalHighlight(off + info.first.getStartOffset(), off + info.second.getEndOffset()) {
          @Override
          public TextAttributes getTextAttributes(@Nullable TextAttributes source) {
            return new TextAttributes(null, null, attributes.getEffectColor(), EffectType.LINE_UNDERSCORE, Font.PLAIN);
          }
        });
      }
    }
  }
}