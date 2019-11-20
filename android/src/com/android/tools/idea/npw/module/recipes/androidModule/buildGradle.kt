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
package com.android.tools.idea.npw.module.recipes.androidModule

import com.android.tools.idea.npw.module.recipes.androidConfig
import com.android.tools.idea.npw.module.recipes.getConfigurationName
import com.android.tools.idea.templates.RepositoryUrlManager
import com.android.tools.idea.templates.resolveDependency
import com.android.tools.idea.wizard.template.GradlePluginVersion
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.renderIf

fun buildGradle(
  isLibraryProject: Boolean,
  isDynamicFeature: Boolean,
  packageName: String,
  buildApiString: String,
  explicitBuildToolsVersion: Boolean,
  buildToolsVersion: String,
  minApi: Int,
  targetApi: Int,
  useAndroidX: Boolean,
  language: Language,
  gradlePluginVersion: GradlePluginVersion,
  supportsImprovedTestDeps: Boolean,
  includeCppSupport: Boolean,
  // TODO(qumeric): do something better
  cppFlags: String = "",
  isCompose: Boolean = false,
  baseFeatureName: String = "base",
  wearProjectName: String = "wear",
  mobileIncluded: Boolean = true,
  wearIncluded: Boolean = false
): String {
  val isApplicationProject = !isLibraryProject
  val kotlinPluginsBlock = renderIf(language == Language.Kotlin) {
    """
    apply plugin : "kotlin-android"
    apply plugin : "kotlin-android-extensions"
    """
  }
  val pluginsBlock = "    " + when {
    isLibraryProject -> "apply plugin : \"com.android.library\""
    isDynamicFeature -> "apply plugin : \"com.android.dynamic-feature\""
    else -> "apply plugin : \"com.android.application\""
  } + "\n    " + kotlinPluginsBlock


  val androidConfigBlock = androidConfig(
    buildApiString,
    explicitBuildToolsVersion,
    buildToolsVersion,
    minApi,
    targetApi,
    useAndroidX,
    cppFlags,
    isLibraryProject,
    includeCppSupport,
    isApplicationProject,
    packageName,
    hasTests = true,
    canHaveCpp = true,
    canUseProguard = true
  )

  val composeDependenciesBlock = renderIf(isCompose) { "kotlinPlugin \"androidx.compose:compose-compiler:+\"" }

  val oldTestDependenciesBlock = renderIf(!supportsImprovedTestDeps) {
    """
    ${getConfigurationName("androidTestCompile", gradlePluginVersion)}("${
    resolveDependency(RepositoryUrlManager.get(), "com.android.support.test.espresso:espresso-core:+")
    }", {
      exclude group : "com.android.support", module: "support-annotations"
    })
    """
  }

  val kotlinDependenciesBlock = renderIf(language == Language.Kotlin) {
    kotlinDependencies(gradlePluginVersion)
  }

  val dynamicFeatureBlock = when {
    isDynamicFeature -> """implementation project (":${baseFeatureName}")"""
    !wearProjectName.isBlank() && mobileIncluded && wearIncluded -> """wearApp project (":${wearProjectName}")"""
    else -> ""
  }

  val dependenciesBlock = """
  dependencies {
    $composeDependenciesBlock
    ${getConfigurationName("compile", gradlePluginVersion)} fileTree (dir: "libs", include: ["*.jar"])
    $oldTestDependenciesBlock
    $kotlinDependenciesBlock
    $dynamicFeatureBlock
  }
  """

  return """
    $pluginsBlock
    $androidConfigBlock
    $dependenciesBlock
  """
}

// TODO(qumeric): move everything under  to a proper place
private fun kotlinDependencies(agpVersion: String) =
  "${getConfigurationName("compile", agpVersion)} \"org.jetbrains.kotlin:kotlin-stdlib-jdk7:${'$'}kotlin_version\""

