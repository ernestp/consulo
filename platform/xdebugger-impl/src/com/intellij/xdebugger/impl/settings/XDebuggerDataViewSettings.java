/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.settings;

import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;

/**
 * @author nik
 */
@Tag("data-views")
public class XDebuggerDataViewSettings implements XDebuggerSettingsManager.DataViewSettings {
  static final int DEFAULT_VALUE_TOOLTIP_DELAY = 700;

  private boolean mySortValues;
  private boolean myShowVariablesInEditor = true;

  private boolean autoExpressions = true;
  private int valueLookupDelay = DEFAULT_VALUE_TOOLTIP_DELAY;

  @Override
  @Tag("sort-values")
  public boolean isSortValues() {
    return mySortValues;
  }

  public void setSortValues(boolean sortValues) {
    mySortValues = sortValues;
  }

  @Override
  public int getValueLookupDelay() {
    return valueLookupDelay;
  }

  public void setValueLookupDelay(int value) {
    valueLookupDelay = value;
  }

  public void setShowVariablesInEditor(boolean showVariablesInEditor) {
    myShowVariablesInEditor = showVariablesInEditor;
  }

  @Override
  public boolean isAutoExpressions() {
    return autoExpressions;
  }

  @Override
  @Tag("show-variables-in-editor")
  public boolean isShowVariablesInEditor() {
    return myShowVariablesInEditor;
  }

  public void setAutoExpressions(boolean autoExpressions) {
    this.autoExpressions = autoExpressions;
  }
}
