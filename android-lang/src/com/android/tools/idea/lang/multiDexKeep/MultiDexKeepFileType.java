/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.lang.multiDexKeep;

import com.intellij.openapi.fileTypes.LanguageFileType;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * File type for multiDexKeepFile format.
 * @see MultiDexKeepLanguage
 */
public class MultiDexKeepFileType extends LanguageFileType {
  public static final MultiDexKeepFileType INSTANCE = new MultiDexKeepFileType();

  private MultiDexKeepFileType() {
    super(MultiDexKeepLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "MultiDexKeep file";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "MultiDexKeepFile format";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return null;
  }
}