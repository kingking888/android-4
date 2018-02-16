/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.guitestprojectsystem

import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.fest.swing.core.Robot
import java.io.File

interface GuiTestProjectSystem {
  /**
   * A unique identifier for the test system implementation. This identifier may be used by users of [GuiTestProjectSystem] to
   * identify which underlying implementation of [GuiTestProjectSystem] is being used.
   */
  val id: String

  /**
   * The build system for which this test system is responsible for providing implementations for.
   */
  val buildSystem: TargetBuildSystem.BuildSystem

  /**
   * Check the test setup to see if all requirements are met (environment variables, required plugins, etc) and throws an
   * [IllegalArgumentException] if there are errors.
   */
  fun validateSetup()

  /**
   * Modifies the test project in preparation for testing. (e.g. removing/transforming build files.)
   */
  fun prepareTestForImport(targetTestDirectory: File)

  /**
   * Runs the build system specific import routine for a given project.  Implementations may choose to import
   * the project by performing UI actions with a FEST robot. (e.g. Click through the import project wizard.)
   */
  fun importProject(targetTestDirectory: File, robot: Robot, buildPath: String? = null)

  /**
   * Triggers a project sync by invoking a build system specific menu path.
   */
  fun requestProjectSync(ideFrameFixture: IdeFrameFixture): GuiTestProjectSystem

  /**
   * Waits for the project sync to finish using build system specific waiting logic.
   */
  fun waitForProjectSyncToFinish(ideFrameFixture: IdeFrameFixture)

  /**
   * Returns a [VirtualFile] corresponding to the given [project]'s root directory (i.e. the top-level directory containing all
   * source files related to this project). This may be (but is not necessarily) the same as the [project]'s base directory
   * (i.e. the directory containing .idea/ ).
   */
  fun getProjectRootDirectory(project: Project): VirtualFile

  companion object {
    val EP_NAME: ExtensionPointName<GuiTestProjectSystem> = ExtensionPointName.create("com.android.project.guitestprojectsystem")

    fun forBuildSystem(buildSystem: TargetBuildSystem.BuildSystem): GuiTestProjectSystem?
        = EP_NAME.extensions.firstOrNull { it.buildSystem == buildSystem }
  }
}