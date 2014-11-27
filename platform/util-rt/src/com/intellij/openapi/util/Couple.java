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
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.DeprecationInfo;

/**
 * @author Konstantin Bulenkov
 */
public class Couple<T> extends Pair<T, T> {
  private static final Couple EMPTY_COUPLE = of(null, null);

  public Couple(T first, T second) {
    super(first, second);
  }

  @NotNull
  public static <T> Couple<T> of(T first, T second) {
    return new Couple<T>(first, second);
  }

  @NotNull
  @Deprecated
  @DeprecationInfo(value = "Use #of method", until = "1.0")
  public static <T> Couple<T> newOne(T first, T second) {
    return of(first, second);
  }

  @SuppressWarnings("unchecked")
  public static <T> Couple<T> getEmpty() {
    return EMPTY_COUPLE;
  }
}
