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
package com.intellij.psi.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.util.containers.ConcurrentWeakValueHashMap;
import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.RequiredReadAction;
import org.mustbe.consulo.RequiredWriteAction;

import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
class EmptyFileManager implements FileManager {
  private final PsiManagerImpl myManager;
  private final ConcurrentWeakValueHashMap<VirtualFile, FileViewProvider> myVFileToViewProviderMap = new ConcurrentWeakValueHashMap<VirtualFile, FileViewProvider>();

  EmptyFileManager(final PsiManagerImpl manager) {
    myManager = manager;
  }

  @Override
  public void dispose() {
  }

  @RequiredReadAction
  @Override
  public PsiFile findFile(@NotNull VirtualFile vFile) {
    return null;
  }

  @RequiredReadAction
  @Override
  public PsiDirectory findDirectory(@NotNull VirtualFile vFile) {
    return null;
  }

  @RequiredWriteAction
  @Override
  public void reloadFromDisk(@NotNull PsiFile file) {
  }

  @RequiredReadAction
  @Override
  public PsiFile getCachedPsiFile(@NotNull VirtualFile vFile) {
    return null;
  }

  @Override
  public void cleanupForNextTest() {
  }

  @RequiredReadAction
  @Override
  public FileViewProvider findViewProvider(@NotNull VirtualFile file) {
    return myVFileToViewProviderMap.get(file);
  }

  @RequiredReadAction
  @Override
  public FileViewProvider findCachedViewProvider(@NotNull VirtualFile file) {
    return myVFileToViewProviderMap.get(file);
  }

  @Override
  @NotNull
  public FileViewProvider createFileViewProvider(@NotNull final VirtualFile file, final boolean physical) {
    return new SingleRootFileViewProvider(myManager, file, physical);
  }

  @RequiredReadAction
  @Override
  public void setViewProvider(@NotNull final VirtualFile virtualFile, final FileViewProvider singleRootFileViewProvider) {
    if (!(virtualFile instanceof VirtualFileWindow)) {
      if (singleRootFileViewProvider == null) {
        myVFileToViewProviderMap.remove(virtualFile);
      }
      else {
        myVFileToViewProviderMap.put(virtualFile, singleRootFileViewProvider);
      }
    }
  }

  @NotNull
  @Override
  public List<PsiFile> getAllCachedFiles() {
    return Collections.emptyList();
  }
}
