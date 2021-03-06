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
package com.android.tools.idea.lite;

import com.intellij.openapi.application.ApplicationStarter;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Entry point for running "studio tools".
 */
final class GameToolsStarter implements ApplicationStarter {
  @Override
  public String getCommandName() {
    return "game-tools";
  }

  @Override
  public void premain(@NotNull List<String> args) {
  }

  @Override
  public void main(String @NotNull [] args) {
    // TODO (b/135942818): Create a project and quick launch into profilers
  }

  @Override
  public boolean canProcessExternalCommandLine() {
    // This allows us to launch even if an instance of studio is already started.
    return true;
  }

  @Override
  public int getRequiredModality() {
    return ANY_MODALITY;
  }
}
