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
package com.intellij.core;

import com.intellij.mock.MockProject;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.stores.DefaultStateSerializer;
import com.intellij.openapi.components.impl.stores.DirectoryStorageData;
import com.intellij.openapi.components.impl.stores.StorageData;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author yole
 */
public class CoreProjectLoader {
  public static boolean loadProject(MockProject project, @NotNull VirtualFile virtualFile)
          throws IOException, JDOMException, InvalidDataException {
    if (virtualFile.isDirectory() && virtualFile.findChild(Project.DIRECTORY_STORE_FOLDER) != null) {
      project.setBaseDir(virtualFile);
      loadDirectoryProject(project, virtualFile);
      return true;
    }

    // TODO load .ipr
    return false;
  }

  private static void loadDirectoryProject(MockProject project, VirtualFile projectDir) throws IOException, JDOMException,
                                                                                               InvalidDataException {
    VirtualFile dotIdea = projectDir.findChild(Project.DIRECTORY_STORE_FOLDER);
    assert dotIdea != null;
    VirtualFile modulesXml = dotIdea.findChild("modules.xml");
    assert modulesXml != null;
    StorageData storageData = loadStorageFile(project, modulesXml);
    final Element moduleManagerState = storageData.getState("ProjectModuleManager");
    if (moduleManagerState == null) {
      throw new JDOMException("cannot find ProjectModuleManager state in modules.xml");
    }
    final CoreModuleManager moduleManager = (CoreModuleManager)ModuleManager.getInstance(project);
    moduleManager.loadState(moduleManagerState);

    VirtualFile libraries = dotIdea.findChild("libraries");
    if (libraries != null) {
      DirectoryStorageData data = new DirectoryStorageData();
      data.loadFrom(libraries, PathMacroManager.getInstance(project).createTrackingSubstitutor());
      final Element libraryTable = DefaultStateSerializer.deserializeState(data.getCompositeStateAndArchive("libraryTable", new ProjectLibraryTable.LibraryStateSplitter()), Element.class, null);
      ((LibraryTableBase) ProjectLibraryTable.getInstance(project)).loadState(libraryTable);
    }

    moduleManager.loadModules();
    project.projectOpened();
  }

  @NotNull
  public static StorageData loadStorageFile(@NotNull ComponentManager componentManager, @NotNull VirtualFile modulesXml) throws JDOMException, IOException {
    StorageData storageData = new StorageData("project");
    storageData.load(JDOMUtil.loadDocument(modulesXml.contentsToByteArray()).getRootElement(), PathMacroManager.getInstance(componentManager), false);
    return storageData;
  }
}
