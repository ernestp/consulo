/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.fileTypes.InternalStdFileTypes;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author oleg
 */
public class CheckinHandlerUtil {
  public static List<VirtualFile> filterOutGeneratedAndExcludedFiles(@NotNull Collection<VirtualFile> files, @NotNull Project project) {
    ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    List<VirtualFile> result = new ArrayList<VirtualFile>(files.size());
    for (VirtualFile file : files) {
      if (!fileIndex.isExcluded(file) && !GeneratedSourcesFilter.isGenerated(project, file)) {
        result.add(file);
      }
    }
    return result;
  }

  public static PsiFile[] getPsiFiles(final Project project, final Collection<VirtualFile> selectedFiles) {
    ArrayList<PsiFile> result = new ArrayList<PsiFile>();
    PsiManager psiManager = PsiManager.getInstance(project);

    VirtualFile projectFileDir = null;
    final StorageScheme storageScheme = ((ProjectEx) project).getStateStore().getStorageScheme();
    if (StorageScheme.DIRECTORY_BASED.equals(storageScheme)) {
      VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) {
        projectFileDir = baseDir.findChild(Project.DIRECTORY_STORE_FOLDER);
      }
    }

    for (VirtualFile file : selectedFiles) {
      if (file.isValid()) {
        if (isUnderProjectFileDir(projectFileDir, file) || !isFileUnderSourceRoot(project, file)) {
          continue;
        }
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) result.add(psiFile);
      }
    }
    return PsiUtilCore.toPsiFileArray(result);
  }

  private static boolean isUnderProjectFileDir(@Nullable VirtualFile projectFileDir, @NotNull VirtualFile file) {
    return projectFileDir != null && VfsUtilCore.isAncestor(projectFileDir, file, false);
  }

  private static boolean isFileUnderSourceRoot(@NotNull Project project, @NotNull VirtualFile file) {
    ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    if (InternalStdFileTypes.JAVA == file.getFileType()) {
      return index.isInSource(file) && !index.isInLibrarySource(file);
    } else {
      return index.isInContent(file) && !index.isInLibrarySource(file) ;
    }
  }

  static void disableWhenDumb(@NotNull Project project, @NotNull JCheckBox checkBox, @NotNull String tooltip) {
    boolean dumb = DumbService.isDumb(project);
    checkBox.setEnabled(!dumb);
    checkBox.setToolTipText(dumb ? tooltip : "");
  }
}
