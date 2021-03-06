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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.tools.idea.rendering.RenderTaskAllocationTrackerKt;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * {@link TestRule} that verifies that all {@link RenderTask} have been properly de-allocated
 */
public class RenderTaskLeakCheckRule implements TestRule {
  @NotNull
  @Override
  public Statement apply(@NotNull Statement base, @NotNull Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        RenderTaskAllocationTrackerKt.clearTrackedAllocations();
        try {
          base.evaluate();
        }
        finally {
          RenderTaskAllocationTrackerKt.notDisposedRenderTasks()
            .iterator()
            .forEachRemaining(stackTrace -> {
              String stackTraceString = stackTrace.stream()
                .map(element -> "\t\t" + element)
                .collect(Collectors.joining("\n"));
              throw new IllegalStateException(
                "Render task not released. Allocated at \n" + stackTraceString);
            });
        }
      }
    };
  }
}
