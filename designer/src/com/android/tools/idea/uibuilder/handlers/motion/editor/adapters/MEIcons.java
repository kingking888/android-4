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
package com.android.tools.idea.uibuilder.handlers.motion.editor.adapters;

import com.intellij.icons.AllIcons;
import icons.StudioIcons;
import javax.swing.Icon;

/**
 * Provide indirection to StudioIcons
 */
public class MEIcons {
  public static final Icon SLOW_MOTION =  StudioIcons.LayoutEditor.Motion.SLOW_MOTION;
  public static final Icon PLAY =   StudioIcons.LayoutEditor.Motion.PLAY;
  public static final Icon FORWARD = StudioIcons.LayoutEditor.Motion.GO_TO_END;
  public static final Icon BACKWARD = StudioIcons.LayoutEditor.Motion.GO_TO_START;
  public static final Icon LOOP = StudioIcons.LayoutEditor.Motion.PLAY_YOYO;
  public static final Icon PAUSE = StudioIcons.LayoutEditor.Motion.PAUSE;
  public static final Icon LIST_LAYOUT = StudioIcons.LayoutEditor.Motion.BASE_LAYOUT; // TODO fix
  public static final Icon LIST_STATE = StudioIcons.LayoutEditor.Motion.CONSTRAINT_SET; // TODO fix
  public static final Icon LIST_TRANSITION = StudioIcons.LayoutEditor.Toolbar.ARROW_RIGHT; // TODO fix
  public static final Icon LIST_GRAY_STATE = StudioIcons.LayoutEditor.Toolbar.EXPAND_TO_FIT; // TODO fix
  public static final Icon CREATE_MENU = StudioIcons.LayoutEditor.Toolbar.ADD_COMPONENT; // TODO fix
  public static final Icon CYCLE_LAYOUT =  AllIcons.General.LayoutEditorPreview; // TODO fix
  public static final Icon EDIT_MENU = StudioIcons.Common.EDIT; // TODO fix
  public static final Icon EDIT_MENU_DISABLED = StudioIcons.Avd.EDIT; // TODO fix
  public static final Icon CREATE_KEYFRAME = StudioIcons.LayoutEditor.Motion.ADD_KEYFRAME;

  public static final Icon CREATE_TRANSITION = StudioIcons.LayoutEditor.Motion.ADD_TRANSITION; // TODO fix
  public static final Icon CREATE_CONSTRAINTSET = StudioIcons.LayoutEditor.Motion.ADD_CONSTRAINT_SET; // TODO fix  ;
  public static final Icon CREATE_ON_STAR = StudioIcons.LayoutEditor.Motion.ADD_GESTURE; // TODO fix

  public static final Icon CREATE_ON_CLICK = StudioIcons.LayoutEditor.Motion.ADD_GESTURE; // TODO fix
  public static final Icon CREATE_ON_SWIPE = StudioIcons.LayoutEditor.Motion.ADD_GESTURE; // TODO fix

}
