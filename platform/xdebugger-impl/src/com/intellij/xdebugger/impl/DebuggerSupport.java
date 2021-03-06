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
package com.intellij.xdebugger.impl;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.AbstractDebuggerSession;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import com.intellij.xdebugger.impl.actions.EditBreakpointActionHandler;
import com.intellij.xdebugger.impl.actions.MarkObjectActionHandler;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author nik
 */
public abstract class DebuggerSupport {
  private static final ExtensionPointName<DebuggerSupport> EXTENSION_POINT = ExtensionPointName.create("com.intellij.xdebugger.debuggerSupport");

  protected static final class DisabledActionHandler extends DebuggerActionHandler {
    public static final DisabledActionHandler INSTANCE = new DisabledActionHandler();

    @Override
    public void perform(@NotNull Project project, AnActionEvent event) {
    }

    @Override
    public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
      return false;
    }
  }

  @NotNull
  public static DebuggerSupport[] getDebuggerSupports() {
    return Extensions.getExtensions(EXTENSION_POINT);
  }

  @NotNull
  public abstract BreakpointPanelProvider<?> getBreakpointPanelProvider();

  @NotNull
  public abstract DebuggerActionHandler getStepOverHandler();

  @NotNull
  public abstract DebuggerActionHandler getStepIntoHandler();

  @NotNull
  public abstract DebuggerActionHandler getSmartStepIntoHandler();

  @NotNull
  public abstract DebuggerActionHandler getStepOutHandler();

  @NotNull
  public abstract DebuggerActionHandler getForceStepOverHandler();

  @NotNull
  public abstract DebuggerActionHandler getForceStepIntoHandler();


  @NotNull
  public abstract DebuggerActionHandler getRunToCursorHandler();

  @NotNull
  public abstract DebuggerActionHandler getForceRunToCursorHandler();


  @NotNull
  public abstract DebuggerActionHandler getResumeActionHandler();

  @NotNull
  public abstract DebuggerActionHandler getPauseHandler();


  @NotNull
  public abstract DebuggerActionHandler getToggleLineBreakpointHandler();

  @NotNull
  public abstract DebuggerActionHandler getToggleTemporaryLineBreakpointHandler();


  @NotNull
  public abstract DebuggerActionHandler getShowExecutionPointHandler();

  @NotNull
  public abstract DebuggerActionHandler getEvaluateHandler();

  private static final QuickEvaluateHandler DISABLED_QUICK_EVALUATE = new QuickEvaluateHandler() {
    @Override
    public boolean isEnabled(@NotNull Project project) {
      return false;
    }

    @Nullable
    @Override
    public AbstractValueHint createValueHint(@NotNull Project project, @NotNull Editor editor, @NotNull Point point, ValueHintType type) {
      return null;
    }

    @Override
    public boolean canShowHint(@NotNull Project project) {
      return false;
    }

    @Override
    public int getValueLookupDelay(Project project) {
      return 0;
    }
  };

  @NotNull
  public QuickEvaluateHandler getQuickEvaluateHandler() {
    return DISABLED_QUICK_EVALUATE;
  }

  @NotNull
  public abstract DebuggerActionHandler getAddToWatchesActionHandler();

  public DebuggerActionHandler getEvaluateInConsoleActionHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  public abstract DebuggerToggleActionHandler getMuteBreakpointsHandler();

  @NotNull
  public abstract MarkObjectActionHandler getMarkObjectHandler();


  @Nullable
  public abstract AbstractDebuggerSession getCurrentSession(@NotNull Project project);

  @NotNull
  public abstract EditBreakpointActionHandler getEditBreakpointAction();


  @NotNull
  public static <T extends DebuggerSupport> DebuggerSupport getDebuggerSupport(Class<T> aClass) {
    for (DebuggerSupport support : getDebuggerSupports()) {
      if (support.getClass() == aClass) {
        return support;
      }
    }
    throw new IllegalStateException();
  }
}
