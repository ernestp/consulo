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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Rustam Vishnyakov
 */
@State(
        name = "CodeStyleSchemeSettings",
        storages = {@Storage(
                file = StoragePathMacros.APP_CONFIG + "/" + PersistableCodeStyleSchemes.CODE_STYLE_SCHEMES_FILE
        )}
)
public class PersistableCodeStyleSchemes extends CodeStyleSchemesImpl implements PersistentStateComponent<Element>, NamedComponent {
  @NonNls static final String CODE_STYLE_SCHEMES_FILE = "code.style.schemes.xml";

  private boolean isLoaded;

  public PersistableCodeStyleSchemes(SchemesManagerFactory schemesManagerFactory) {
    super(schemesManagerFactory);
  }

  @Nullable
  @Override
  public Element getState() {
    return XmlSerializer.serialize(this, new SerializationFilter() {
      @Override
      public boolean accepts(Accessor accessor, Object bean) {
        return accessor.getValueClass().equals(String.class);
      }
    });
  }

  @Override
  public void loadState(Element state) {
    init();
    XmlSerializer.deserializeInto(this, state);
    isLoaded = true;
    updateCurrentScheme();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "CodeStyleSchemeSettings";
  }

  @Override
  public boolean isLoaded() {
    return isLoaded;
  }

  @Override
  public void loadSettings() {
    init();
    LegacyCodeStyleSchemesSettings legacySettings = ServiceManager.getService(LegacyCodeStyleSchemesSettings.class);
    if (legacySettings != null) {
      CURRENT_SCHEME_NAME = legacySettings.CURRENT_SCHEME_NAME;
    }
    isLoaded = true;
    updateCurrentScheme();
  }

  private void updateCurrentScheme() {
    CodeStyleScheme current = findSchemeByName(CURRENT_SCHEME_NAME);
    if (current == null) current = getDefaultScheme();
    setCurrentScheme(current);
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[]{new File(PathManager.getConfigPath() + File.separator + CODE_STYLES_DIRECTORY),
            new File(PathManager.getOptionsPath() + File.separator + CODE_STYLE_SCHEMES_FILE)};
  }
}
