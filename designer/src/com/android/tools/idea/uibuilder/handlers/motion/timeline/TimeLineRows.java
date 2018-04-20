/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.timeline;

import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * The make chart that displays the Keyframes in time
 */
public class TimeLineRows extends JPanel implements Gantt.ChartElement {
  Color myBackground = Chart.ourAvgBackground;
  Chart myChart;
  int[] myXPoints = new int[10]; // so that the memory is not allocated on paint
  int[] myYPoints = new int[10]; // so that the memory is not allocated on paint
  ArrayList<ViewRow> myViewRows = new ArrayList<>();
  public static int ourDiamondSize = JBUI.scale(10);
  private boolean myInStateChange;

  // a super light spacer to fill the bottom of the table
  JComponent mySpacer = new JComponent() {
  };

  TimeLineRows(Chart chart) {
    super(new GridBagLayout());

    myChart = chart;
    update(Reason.CONSTRUCTION);
    myChart.add(this);
  }

  @Override
  public void update(Reason reason) {
    if (reason == Reason.CURSOR_POSITION_CHANGED) {
      repaint();
      return;
    }
    if (reason == Reason.ZOOM || reason == Reason.RESIZE) {
      Dimension d = getPreferredSize();
      d.width = myChart.getGraphWidth();
      if (myChart.getmNumberOfViews() > 0) {
        Gantt.ViewElement v = myChart.myViewElements.get(myChart.getmNumberOfViews() - 1);
        d.height = v.myYStart + v.myHeight + 1;
      }

      // remove old rows
      for (ViewRow row : myViewRows) {
        remove(row);
      }
      myViewRows.clear();
      remove(mySpacer);

      // add new rows
      GridBagConstraints cons = new GridBagConstraints();
      cons.fill = GridBagConstraints.HORIZONTAL;
      cons.weightx = 1;
      cons.gridx = 0;
      int chartWidth = myChart.getGraphWidth();
      for (int i = 0; i < myChart.myViewElements.size(); i++) {
        Gantt.ViewElement v = myChart.myViewElements.get(i);
        ViewRow vr = new ViewRow(v, i);
        myViewRows.add(vr);
        vr.setPreferredSize(new Dimension(chartWidth, v.myHeight));
        add(vr, cons);
      }
      cons.weighty = 1;
      add(mySpacer, cons);
      validate();
      repaint();
      SwingUtilities.invokeLater(() -> repaint());
    }
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    if (!Float.isNaN(myChart.getTimeCursorMs())) {
      int x = myChart.getCursorPosition();
      g.setColor(myChart.myTimeCursorColor);
      g.fillRect(x, 0, 1, getHeight());
    }
  }

  private void paintBorder(Graphics g, int width, int height) {
    g.setColor(myBackground);
    g.fillRect(0, 0, width, height);
  }

  @Override
  protected void paintComponent(Graphics g) {
    int width = getWidth();
    int height = getHeight();
    paintBorder(g, width, height);
  }

  // ==============================LocationTable======================================= //
  static class LocationTable {
    int max = 10;
    int[] location = new int[max * 2];
    MotionSceneModel.KeyFrame[] keyFrames = new MotionSceneModel.KeyFrame[max];
    int addPoint = 0;

    public void clear() {
      addPoint = 0;
    }

    void add(int x, int y, MotionSceneModel.KeyFrame keyFrame) {
      if (max == addPoint) {
        max *= 2;
        location = Arrays.copyOf(location, max * 2);
        keyFrames = Arrays.copyOf(keyFrames, max);
      }
      location[addPoint * 2] = x;
      location[addPoint * 2 + 1] = y;
      keyFrames[addPoint] = keyFrame;
      addPoint++;
    }

    MotionSceneModel.KeyFrame find(int x, int y, int max) {
      int closeSq = Integer.MAX_VALUE;
      MotionSceneModel.KeyFrame keyFrame = null;
      int maxSq = max * max;
      for (int i = 0; i < keyFrames.length; i++) {
        int kf_x = location[i * 2];
        int kf_y = location[i * 2 + 1];
        int dx = Math.abs(kf_x - x);
        dx *= dx;
        if (dx > maxSq) continue;
        int dy = Math.abs(kf_y - y);
        dy *= dy;
        if (dy > maxSq) continue;
        if (closeSq > dy + dx) {
          keyFrame = keyFrames[i];
          closeSq = dy + dx;
        }
      }
      return keyFrame;
    }
  }

  /* =============================ViewRow===================================== */
  class ViewRow extends JPanel {
    final Gantt.ViewElement myViewElement;
    LocationTable myLocationTable = new LocationTable();
    int myRow;

    public ViewRow(Gantt.ViewElement v, int row) {
      myViewElement = v;
      myRow = row;
      MouseAdapter ml = new MouseAdapter() {

        @Override
        public void mouseClicked(MouseEvent e) {
          select(e.getX(), e.getY());
        }
      };
      addMouseMotionListener(ml);
      addMouseListener(ml);
    }

    private void select(int x, int y) {
      MotionSceneModel.KeyFrame keyFrame = myLocationTable.find(x, y, 10);
      if (keyFrame != myChart.mySelectedKeyFrame) {
        myChart.mySelectedKeyFrame = keyFrame;
        myChart.mySelection = Chart.Selection.KEY;
        myChart.update(Reason.SELECTION_CHANGED);
        if (keyFrame != null) {
          float position = keyFrame.getFramePosition() / 100f;
          myChart.setCursorPosition(position);
        }
      }
    }

    public void drawDiamond(Graphics g, int x, int pos) {
      int half = ourDiamondSize / 2;
      myXPoints[0] = x;
      myYPoints[0] = pos;
      myXPoints[1] = x + half;
      myYPoints[1] = pos + half;
      myXPoints[2] = x;
      myYPoints[2] = pos + ourDiamondSize;
      myXPoints[3] = x - half;
      myYPoints[3] = pos + half;
      g.fillPolygon(myXPoints, myYPoints, 4);
    }

    @Override
    protected void paintComponent(Graphics g) {

      if (myRow % 2 == 0) {
        g.setColor(Chart.ourPrimaryPanelBackground);
      }
      else {
        g.setColor(Chart.ourSecondaryPanelBackground);
      }

      myLocationTable.clear();
      int panelWidth = getWidth();
      int panelHeight = getHeight();

      g.fillRect(0, 0, getWidth(), getHeight());
      if (myRow % 2 == 1) {
        g.setColor(Chart.ourPrimaryPanelBackground);
      }
      else {
        g.setColor(Chart.ourSecondaryPanelBackground);
      }
      // Draw vertical lines
      g.setColor(Chart.ourBorder);
      for (int i = 0; i < myChart.myXTickCount; i++) {
        int xLines = myChart.myXTicksPixels[i];
        g.fillRect(xLines, 0, 1, panelHeight);
      }
      final boolean DRAW_RECTS = false;
      // Draw bounding rectangles
      g.setColor(Color.GRAY);
      int y = 0;
      if (DRAW_RECTS) {
        g.drawRect(0, y, panelWidth, myViewElement.myHeightView);
      }
      y += myViewElement.myHeightView;
      if (myViewElement.myHeightPosition > 0) {
        if (DRAW_RECTS) {
          g.drawRect(0, y, panelWidth, myViewElement.myHeightPosition);
        }
        y += myViewElement.myHeightPosition;
      }
      if (myViewElement.myHeightAttribute > 0) {
        if (DRAW_RECTS) {
          g.drawRect(0, y, panelWidth, myViewElement.myHeightAttribute);
        }
        y += myViewElement.myHeightAttribute;
      }
      if (myViewElement.myHeightCycle > 0) {
        if (DRAW_RECTS) {
          g.drawRect(0, y, panelWidth, myViewElement.myHeightCycle);
        }
        y += myViewElement.myHeightCycle;
      }

      int pos = 2;
      int width = getWidth() - myChart.myChartLeftInset - myChart.myChartRightInset;

      g.setColor(Chart.myUnSelectedLineColor);
      for (MotionSceneModel.KeyAttributes key : myViewElement.mKeyFrames.myKeyAttributes) {
        int x = myChart.myChartLeftInset + (int)((key.framePosition * width) / 100);

        if (key == myChart.mySelectedKeyFrame) {
          g.setColor(Chart.ourMySelectedLineColor);
          drawDiamond(g, x, pos);
          g.setColor(Chart.myUnSelectedLineColor);
        }
        else {
          drawDiamond(g, x, pos);
        }
        myLocationTable.add(x, pos, key);
      }
      int delta_y = getHeight() / 4;
      pos += delta_y;

      for (MotionSceneModel.KeyPosition key : myViewElement.mKeyFrames.myKeyPositions) {
        int x = myChart.myChartLeftInset + (int)((key.framePosition * width) / 100);
        if (key == myChart.mySelectedKeyFrame) {
          g.setColor(Chart.ourMySelectedLineColor);
          drawDiamond(g, x, pos);
          g.setColor(Chart.myUnSelectedLineColor);
        }
        else {
          drawDiamond(g, x, pos);
        }

        myLocationTable.add(x, pos, key);
      }
      pos += delta_y;
      for (MotionSceneModel.KeyCycle key : myViewElement.mKeyFrames.myKeyCycles) {
        int x = myChart.myChartLeftInset + (int)((key.framePosition * width) / 100);

        if (key == myChart.mySelectedKeyFrame) {
          g.setColor(Chart.ourMySelectedLineColor);
          drawDiamond(g, x, pos * 2);
          g.setColor(Chart.myUnSelectedLineColor);
        }
        else {
          drawDiamond(g, x, pos * 2);
        }
        myLocationTable.add(x, pos * 2, key);
      }

      int x = myChart.getCursorPosition();

      g.setColor(Chart.myTimeCursorColor);
      g.fillRect(x, 0, 1, panelHeight);

    }
  }
}
