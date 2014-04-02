/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.AndroidGradleProjectResolver;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationExtension;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.event.HyperlinkEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.project.ProjectImportErrorHandler.*;

public class GradleNotificationExtension implements ExternalSystemNotificationExtension {
  private static final Logger LOG = Logger.getInstance(GradleNotificationExtension.class);

  private static final Pattern ERROR_LOCATION_IN_FILE_PATTERN = Pattern.compile("Build file '(.*)' line: ([\\d]+)");
  private static final Pattern ERROR_IN_FILE_PATTERN = Pattern.compile("Build file '(.*)'");
  private static final Pattern MISSING_DEPENDENCY_PATTERN = Pattern.compile("Could not find (.*)\\.");
  private static final Pattern MISSING_MATCHING_DEPENDENCY_PATTERN = Pattern.compile("Could not find any version that matches (.*)\\.");

  private static final NotificationType DEFAULT_NOTIFICATION_TYPE = NotificationType.ERROR;

  @NotNull
  @Override
  public ProjectSystemId getTargetExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @Override
  public void customize(@NotNull NotificationData notification, @NotNull Project project, @Nullable Throwable error) {
    Throwable cause = error;
    if (error instanceof UndeclaredThrowableException) {
      cause = ((UndeclaredThrowableException)error).getUndeclaredThrowable();
      if (cause instanceof InvocationTargetException) {
        cause = ((InvocationTargetException)cause).getTargetException();
      }
    }
    if (cause instanceof ExternalSystemException) {
      updateNotification(notification, project, (ExternalSystemException)cause);
    }
  }

  private static void updateNotification(@NotNull NotificationData notification,
                                         @NotNull Project project,
                                         @NotNull ExternalSystemException error) {
    String msg = error.getMessage();
    if (msg != null && !msg.isEmpty()) {
      if (msg.startsWith(AndroidGradleProjectResolver.UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX)) {
        updateNotification(notification, project, msg, new FixGradleModelVersionHyperlink());
        return;
      }

      if (msg.contains(FAILED_TO_PARSE_SDK_ERROR)) {
        String pathOfBrokenSdk = findPathOfSdkMissingOrEmptyAddonsFolder(project);
        String newMsg;
        if (pathOfBrokenSdk != null) {
          newMsg = String.format("The directory '%1$s', in the Android SDK at '%2$s', is either missing or empty", SdkConstants.FD_ADDONS,
                                 pathOfBrokenSdk);
          File sdkHomeDir = new File(pathOfBrokenSdk);
          if (!sdkHomeDir.canWrite()) {
            String format = "\n\nCurrent user (%1$s) does not have write access to the SDK directory.";
            newMsg += String.format(format, SystemProperties.getUserName());
          }
        }
        else {
          newMsg = splitLines(msg).get(0);
        }
        updateNotification(notification, project, newMsg);
        return;
      }

      List<String> lines = splitLines(msg);
      String lastLine = lines.get(lines.size() - 1);

      if (OPEN_GRADLE_SETTINGS.equals(lastLine)) {
        updateNotification(notification, project, msg, new OpenGradleSettingsHyperlink());
        return;
      }

      if (FIX_SDK_DIR_PROPERTY.equals(lastLine)) {
        File file = new File(project.getBasePath(), SdkConstants.FN_LOCAL_PROPERTIES);
        if (file.isFile()) {
          // If we got this far, local.properties exists.
          BufferedReader reader = null;
          try {
            //noinspection IOResourceOpenedButNotSafelyClosed
            reader = new BufferedReader(new FileReader(file));
            int counter = 0;
            String line;
            while ((line = reader.readLine()) != null) {
              if (line.startsWith(SdkConstants.SDK_DIR_PROPERTY)) {
                updateNotification(notification, project, msg, new OpenFileHyperlink(file.getPath(), counter));
                return;
              }
              counter++;
            }
          }
          catch (IOException e) {
            LOG.info("Unable to read file: " + file.getPath(), e);
          }
          finally {
            Closeables.closeQuietly(reader);
          }
          updateNotification(notification, project, msg, new OpenFileHyperlink(file.getPath(), 0));
          return;
        }
      }

      if (lastLine.contains(INSTALL_ANDROID_SUPPORT_REPO) ||
          lastLine.contains(INSTALL_MISSING_PLATFORM) ||
          lastLine.contains(INSTALL_MISSING_BUILD_TOOLS)) {
        List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
        if (!facets.isEmpty()) {
          // We can only open SDK manager if the project has an Android facet. Android facet has a reference to the Android SDK manager.
          updateNotification(notification, project, msg, new OpenAndroidSdkManagerHyperlink());
          return;
        }
        updateNotification(notification, project, msg);
        return;
      }

      if (lastLine.contains(FIX_GRADLE_VERSION)) {
        List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
        File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);
        if (wrapperPropertiesFile != null) {
          // It is very likely that we need to fix the model version as well. Do everything in one shot.
          NotificationHyperlink hyperlink = new FixGradleModelVersionHyperlink("Fix Gradle wrapper and re-import project", false);
          hyperlinks.add(hyperlink);
        }
        hyperlinks.add(new OpenGradleSettingsHyperlink());
        updateNotification(notification, project, msg, hyperlinks);
        return;
      }

      String firstLine = lines.get(0);

      if (firstLine.startsWith("Unknown host '")) {
        List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
        NotificationHyperlink enableOfflineMode = ToggleOfflineModeHyperlink.enableOfflineMode(project);
        if (enableOfflineMode != null) {
          hyperlinks.add(enableOfflineMode);
        }
        String gradleDocsUrl = "http://www.gradle.org/docs/current/userguide/userguide_single.html#sec:accessing_the_web_via_a_proxy";
        hyperlinks.add(new OpenUrlHyperlink(gradleDocsUrl, "Learn about configuring HTTP proxies in Gradle"));
        updateNotification(notification, project, msg, hyperlinks);
        return;
      }

      if (isInternetConnectionError(firstLine)) {
        List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
        NotificationHyperlink enableOfflineMode = ToggleOfflineModeHyperlink.enableOfflineMode(project);
        if (enableOfflineMode != null) {
          hyperlinks.add(enableOfflineMode);
        }
        updateNotification(notification, project, msg, hyperlinks);
        return;
      }

      if (firstLine.startsWith("No cached version of ") && firstLine.contains("available for offline mode.")) {
        List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
        NotificationHyperlink disableOfflineMode = ToggleOfflineModeHyperlink.disableOfflineMode(project);
        if (disableOfflineMode != null) {
          hyperlinks.add(disableOfflineMode);
        }
        updateNotification(notification, project, firstLine, hyperlinks);
        return;
      }

      Matcher matcher = MISSING_MATCHING_DEPENDENCY_PATTERN.matcher(firstLine);
      if (matcher.matches()) {
        String dependency = matcher.group(1);
        createMissingDependencyNotification(notification, project, firstLine, dependency);
        return;
      }

      matcher = MISSING_DEPENDENCY_PATTERN.matcher(firstLine);
      if (matcher.matches() && lines.size() > 1 && lines.get(1).startsWith("Required by:")) {
        String dependency = matcher.group(1);
        if (!Strings.isNullOrEmpty(dependency)) {
          if (lastLine != null) {
            Pair<String, Integer> errorLocation = getErrorLocation(lastLine);
            if (errorLocation != null) {
              // We have a location in file, show the "Open File" hyperlink.
              String filePath = errorLocation.getFirst();
              int line = errorLocation.getSecond();
              createMissingDependencyNotification(notification, project, msg, dependency, new OpenFileHyperlink(filePath, line - 1));
              return;
            }
          }
          createMissingDependencyNotification(notification, project, msg, dependency);
          return;
        }
      }

      for (String line : lines) {
        // This happens when Gradle cannot find the Android Gradle plug-in in Maven Central.
        if (line == null) {
          continue;
        }
        matcher = MISSING_MATCHING_DEPENDENCY_PATTERN.matcher(line);
        if (matcher.matches()) {
          String dependency = matcher.group(1);
          createMissingDependencyNotification(notification, project, line, dependency);
          return;
        }
      }

      if (lastLine != null) {
        if (lastLine.contains(UNEXPECTED_ERROR_FILE_BUG)) {
          updateNotification(notification, project, msg, new FileBugHyperlink(), new ShowLogHyperlink());
          return;
        }

        Pair<String, Integer> errorLocation = getErrorLocation(lastLine);
        if (errorLocation != null) {
          String filePath = errorLocation.getFirst();
          int line = errorLocation.getSecond();
          updateNotification(notification, project, msg, new OpenFileHyperlink(filePath, line - 1));
        }
      }
    }
  }

  private static void createMissingDependencyNotification(@NotNull NotificationData notification,
                                                          @NotNull Project project,
                                                          @NotNull String msg,
                                                          @NotNull String dependency,
                                                          @NotNull NotificationHyperlink... additionalHyperlinks) {
    List<NotificationHyperlink> hyperlinks = Lists.newArrayList(additionalHyperlinks);
    ToggleOfflineModeHyperlink disableOfflineMode = ToggleOfflineModeHyperlink.disableOfflineMode(project);
    if (disableOfflineMode != null) {
      hyperlinks.add(0, disableOfflineMode);
    }
    hyperlinks.add(new SearchInBuildFilesHyperlink(dependency));
    updateNotification(notification, project, msg, hyperlinks);
  }

  @Nullable
  private static Pair<String, Integer> getErrorLocation(@NotNull String msg) {
    Matcher matcher = ERROR_LOCATION_IN_FILE_PATTERN.matcher(msg);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      int line = -1;
      try {
        line = Integer.parseInt(matcher.group(2));
      }
      catch (NumberFormatException e) {
        // ignored.
      }
      return Pair.create(filePath, line);
    }

    matcher = ERROR_IN_FILE_PATTERN.matcher(msg);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      return Pair.create(filePath, -1);
    }
    return null;
  }

  @Nullable
  private static String findPathOfSdkMissingOrEmptyAddonsFolder(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
      if (moduleSdk != null && moduleSdk.getSdkType().equals(AndroidSdkType.getInstance())) {
        String sdkHomeDirPath = moduleSdk.getHomePath();
        File addonsDir = new File(sdkHomeDirPath, SdkConstants.FD_ADDONS);
        if (!addonsDir.isDirectory() || FileUtil.notNullize(addonsDir.listFiles()).length == 0) {
          return sdkHomeDirPath;
        }
      }
    }
    return null;
  }

  @NotNull
  private static List<String> splitLines(@NotNull String s) {
    return Lists.newArrayList(Splitter.on('\n').split(s));
  }

  private static void updateNotification(@NotNull NotificationData notification,
                                         @NotNull Project project,
                                         @NotNull String errorMsg,
                                         @NotNull List<NotificationHyperlink> hyperlinks) {
    updateNotification(notification, project, errorMsg, hyperlinks.toArray(new NotificationHyperlink[hyperlinks.size()]));
  }

  private static boolean isInternetConnectionError(@NotNull String msg) {
    return msg.startsWith("Could not GET ") || msg.startsWith("Could not HEAD ") || msg.startsWith("Network is unreachable");
  }

  @VisibleForTesting
  static void updateNotification(@NotNull NotificationData notification,
                                 @NotNull final Project project,
                                 @NotNull String errorMsg,
                                 @NotNull NotificationHyperlink... hyperlinks) {
    String text = errorMsg;
    int hyperlinkCount = hyperlinks.length;
    if (hyperlinkCount > 0) {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < hyperlinkCount; i++) {
        b.append(hyperlinks[i].toString());
        if (i < hyperlinkCount - 1) {
          b.append(" ");
        }
      }
      text += ('\n' + b.toString());
    }
    String title = String.format("Failed to refresh Gradle project '%1$s'", project.getName());

    notification.setTitle(title);
    notification.setMessage(text);
    notification.setNotificationType(DEFAULT_NOTIFICATION_TYPE);
    for (final NotificationHyperlink hyperlink : hyperlinks) {
      notification.setListener(hyperlink.getUrl(), new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
          hyperlink.executeIfClicked(project, e);
        }
      });
    }
  }
}
