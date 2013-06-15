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
package com.intellij.project.model.impl.module.dependencies;

import com.intellij.openapi.roots.ModuleExtensionWithSdkOrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.project.model.impl.module.JpsRootModel;
import org.consulo.module.extension.ModuleExtensionWithSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsSdkDependency;

/**
 * @author nik
 */
public class JpsModuleExtensionWithSdkOrderEntry extends JpsSdkOrderEntryBase implements ModuleExtensionWithSdkOrderEntry {
  public JpsModuleExtensionWithSdkOrderEntry(JpsRootModel rootModel, JpsSdkDependency dependencyElement) {
    super(rootModel, dependencyElement);
  }

  @Override
  public <R> R accept(RootPolicy<R> policy, @Nullable R initialValue) {
    return policy.visitModuleJdkOrderEntry(this, initialValue);
  }

  @NotNull
  @Override
  public String getModuleExtensionId() {
    return null;
  }

  @Nullable
  @Override
  public ModuleExtensionWithSdk<?> getModuleExtension() {
    return null;
  }
}
