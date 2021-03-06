/*
 * Copyright 2013 must-be.org
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
package com.intellij.ide;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.extensions.CompositeExtensionPointName;

/**
 * @author VISTALL
 * @since 0:20/19.07.13
 */
public interface IconDescriptorUpdater {
  CompositeExtensionPointName<IconDescriptorUpdater> EP_NAME =
          CompositeExtensionPointName.applicationPoint("com.intellij.iconDescriptorUpdater", IconDescriptorUpdater.class);

  void updateIcon(@NotNull IconDescriptor iconDescriptor, @NotNull PsiElement element, int flags);
}
