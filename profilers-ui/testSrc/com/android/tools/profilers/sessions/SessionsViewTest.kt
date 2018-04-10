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
package com.android.tools.profilers.sessions

import com.android.testutils.TestUtils
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.stdui.CommonAction
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profiler.proto.MemoryProfiler
import com.android.tools.profiler.protobuf3jarjar.ByteString
import com.android.tools.profilers.*
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.cpu.ProfilingConfiguration
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeCaptureObjectLoader
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.memory.HprofSessionArtifact
import com.android.tools.profilers.memory.MemoryProfilerStage
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.event.ActionEvent

class SessionsViewTest {

  private val VALID_TRACE_PATH = "tools/adt/idea/profilers-ui/testData/valid_trace.trace"

  private val myProfilerService = FakeProfilerService(false)
  private val myMemoryService = FakeMemoryService()
  private val myCpuService = FakeCpuService()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel(
    "SessionsViewTestChannel",
    myProfilerService,
    myMemoryService,
    myCpuService,
    FakeEventService(),
    FakeNetworkService.newBuilder().build()
  )

  private lateinit var myTimer: FakeTimer
  private lateinit var myProfilers: StudioProfilers
  private lateinit var mySessionsManager: SessionsManager
  private lateinit var mySessionsView: SessionsView

  @Before
  fun setup() {
    myTimer = FakeTimer()
    myProfilers = StudioProfilers(myGrpcChannel.client, FakeIdeProfilerServices(), myTimer)
    mySessionsManager = myProfilers.sessionsManager
    mySessionsView = SessionsView(myProfilers, FakeIdeProfilerComponents())
  }

  @Test
  fun testSessionsListUpToDate() {
    val sessionArtifacts = mySessionsView.sessionsList.model
    assertThat(sessionArtifacts.size).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    myProfilerService.setTimestampNs(1)
    mySessionsManager.beginSession(device, process1)
    var session1 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(1)
    var sessionItem0 = sessionArtifacts.getElementAt(0) as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session1)

    mySessionsManager.endCurrentSession()
    session1 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(1)
    sessionItem0 = sessionArtifacts.getElementAt(0) as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session1)

    myProfilerService.setTimestampNs(2)
    mySessionsManager.beginSession(device, process2)
    val session2 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(2)
    // Sessions are sorted in descending order.
    sessionItem0 = sessionArtifacts.getElementAt(0) as SessionItem
    var sessionItem1 = sessionArtifacts.getElementAt(1) as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(sessionItem1.session).isEqualTo(session1)

    // Add the heap dump and CPU capture, expand the first session and make sure the artifacts are shown in the list
    val heapDumpTimestamp = 10L
    val cpuTraceTimestamp = 20L
    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(heapDumpTimestamp).setEndTime(heapDumpTimestamp + 1).build()
    val cpuTraceInfo = CpuProfiler.TraceInfo.newBuilder().setFromTimestamp(cpuTraceTimestamp).setToTimestamp(cpuTraceTimestamp + 1).build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)
    myCpuService.addTraceInfo(cpuTraceInfo)
    mySessionsManager.update()

    assertThat(sessionArtifacts.size).isEqualTo(6)
    sessionItem0 = sessionArtifacts.getElementAt(0) as SessionItem
    val cpuCaptureItem0 = sessionArtifacts.getElementAt(1) as CpuCaptureSessionArtifact
    val hprofItem0 = sessionArtifacts.getElementAt(2) as HprofSessionArtifact
    sessionItem1 = sessionArtifacts.getElementAt(3) as SessionItem
    val cpuCaptureItem1 = sessionArtifacts.getElementAt(4) as CpuCaptureSessionArtifact
    val hprofItem1 = sessionArtifacts.getElementAt(5) as HprofSessionArtifact
    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(hprofItem0.session).isEqualTo(session2)
    assertThat(cpuCaptureItem0.session).isEqualTo(session2)
    assertThat(sessionItem1.session).isEqualTo(session1)
    assertThat(hprofItem1.session).isEqualTo(session1)
    assertThat(cpuCaptureItem1.session).isEqualTo(session1)
  }

  @Test
  fun testProcessDropdownUpToDate() {
    val device1 = Common.Device.newBuilder()
      .setDeviceId(1).setManufacturer("Manufacturer1").setModel("Model1").setState(Common.Device.State.ONLINE).build()
    val device2 = Common.Device.newBuilder()
      .setDeviceId(2).setManufacturer("Manufacturer2").setModel("Model2").setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(1).setName("Process1").setState(Common.Process.State.ALIVE).build()
    val otherProcess1 = Common.Process.newBuilder()
      .setPid(20).setDeviceId(1).setName("Other1").setState(Common.Process.State.ALIVE).build()
    val otherProcess2 = Common.Process.newBuilder()
      .setPid(30).setDeviceId(2).setName("Other2").setState(Common.Process.State.ALIVE).build()
    // Process* is preferred, Other* should be in the other processes flyout.
    myProfilers.setPreferredDeviceAndProcessNames(null, "Process")

    var selectionAction = mySessionsView.processSelectionAction
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    var loadAction = selectionAction.childrenActions.first { c -> c.text == "Load from file..." }
    assertThat(loadAction.isSelected).isFalse()
    assertThat(loadAction.isEnabled).isTrue()
    assertThat(loadAction.childrenActionCount).isEqualTo(0)
    assertThat(selectionAction.childrenActions[1]).isInstanceOf(CommonAction.SeparatorAction::class.java)
    assertThat(selectionAction.childrenActions[2].text).isEqualTo(SessionsView.NO_SUPPORTED_DEVICES)
    assertThat(selectionAction.childrenActions[2].isEnabled).isFalse()

    myProfilerService.addDevice(device1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    assertThat(selectionAction.childrenActions[1]).isInstanceOf(CommonAction.SeparatorAction::class.java)
    loadAction = selectionAction.childrenActions.first { c -> c.text == "Load from file..." }
    assertThat(loadAction.isSelected).isFalse()
    assertThat(loadAction.isEnabled).isTrue()
    assertThat(loadAction.childrenActionCount).isEqualTo(0)
    var deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isSelected).isTrue()
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(1)
    assertThat(deviceAction1.childrenActions[0].text).isEqualTo(SessionsView.NO_DEBUGGABLE_PROCESSES)
    assertThat(deviceAction1.childrenActions[0].isEnabled).isFalse()

    myProfilerService.addProcess(device1, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myProfilers.process = process1
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isSelected).isTrue()
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(1)
    var processAction1 = deviceAction1.childrenActions.first { c -> c.text == "Process1 (10)" }
    assertThat(processAction1.isSelected).isTrue()
    assertThat(processAction1.childrenActionCount).isEqualTo(0)

    myProfilerService.addProcess(device1, otherProcess1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isSelected).isTrue()
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(3)  // process1 + separator + "other processes"
    processAction1 = deviceAction1.childrenActions.first { c -> c.text == "Process1 (10)" }
    assertThat(processAction1.isSelected).isTrue()
    assertThat(deviceAction1.childrenActions[1]).isInstanceOf(CommonAction.SeparatorAction::class.java)
    var processAction2 = deviceAction1.childrenActions
      .first { c -> c.text == "Other processes" }.childrenActions
      .first { c -> c.text == "Other1 (20)" }
    assertThat(processAction2.isSelected).isFalse()

    // Test the reverse case of having only "other" processes
    myProfilerService.addDevice(device2)
    myProfilerService.addProcess(device2, otherProcess2)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(4)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isSelected).isTrue()
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(3)  // process1 + separator + "other processes"
    processAction1 = deviceAction1.childrenActions.first { c -> c.text == "Process1 (10)" }
    assertThat(processAction1.isSelected).isTrue()
    assertThat(deviceAction1.childrenActions[1]).isInstanceOf(CommonAction.SeparatorAction::class.java)
    processAction2 = deviceAction1.childrenActions
      .first { c -> c.text == "Other processes" }.childrenActions
      .first { c -> c.text == "Other1 (20)" }
    assertThat(processAction2.isSelected).isFalse()
    var deviceAction2 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer2 Model2" }
    assertThat(deviceAction2.isSelected).isFalse()
    assertThat(deviceAction2.isEnabled).isTrue()
    assertThat(deviceAction2.childrenActionCount).isEqualTo(1) // There should be no separator in this case.
    var processAction3 = deviceAction2.childrenActions
      .first { c -> c.text == "Other processes" }.childrenActions
      .first { c -> c.text == "Other2 (30)" }
    assertThat(processAction3.isSelected).isFalse()
  }

  @Test
  fun testProcessDropdownHideDeadDevicesAndProcesses() {
    val deadDevice = Common.Device.newBuilder()
      .setDeviceId(1).setManufacturer("Manufacturer1").setModel("Model1").setState(Common.Device.State.DISCONNECTED).build()
    val onlineDevice = Common.Device.newBuilder()
      .setDeviceId(2).setManufacturer("Manufacturer2").setModel("Model2").setState(Common.Device.State.ONLINE).build()
    val deadProcess1 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(1).setName("Process1").setState(Common.Process.State.DEAD).build()
    val aliveProcess1 = Common.Process.newBuilder()
      .setPid(20).setDeviceId(1).setName("Process2").setState(Common.Process.State.ALIVE).build()
    val deadProcess2 = Common.Process.newBuilder()
      .setPid(30).setDeviceId(2).setName("Process3").setState(Common.Process.State.DEAD).build()
    val aliveProcess2 = Common.Process.newBuilder()
      .setPid(40).setDeviceId(2).setName("Process4").setState(Common.Process.State.ALIVE).build()
    val deadProcess3 = Common.Process.newBuilder()
      .setPid(50).setDeviceId(2).setName("Dead").setState(Common.Process.State.DEAD).build()
    // Also test processes that can be grouped in the fly-out menu.
    myProfilers.setPreferredDeviceAndProcessNames(null, "Process4")

    myProfilerService.addDevice(deadDevice)
    myProfilerService.addDevice(onlineDevice)
    myProfilerService.addProcess(deadDevice, deadProcess1)
    myProfilerService.addProcess(deadDevice, aliveProcess1)
    myProfilerService.addProcess(onlineDevice, deadProcess2)
    myProfilerService.addProcess(onlineDevice, aliveProcess2)
    myProfilerService.addProcess(onlineDevice, deadProcess3)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    var selectionAction = mySessionsView.processSelectionAction
    assertThat(selectionAction.childrenActions.any { c -> c.text == "Manufacturer1 Model1" }).isFalse()
    val aliveDeviceAction = selectionAction.childrenActions.first { c -> c.text == "Manufacturer2 Model2" }
    assertThat(aliveDeviceAction.isSelected).isTrue()
    assertThat(aliveDeviceAction.childrenActionCount).isEqualTo(1)
    var processAction1 = aliveDeviceAction.childrenActions.first { c -> c.text == "Process4 (40)" }
    assertThat(processAction1.isSelected).isTrue()
    assertThat(processAction1.childrenActionCount).isEqualTo(0)
  }

  @Test
  fun testDropdownActionsTriggerProcessChange() {
    val device1 = Common.Device.newBuilder()
      .setDeviceId(1).setManufacturer("Manufacturer1").setModel("Model1").setState(Common.Device.State.ONLINE).build()
    val device2 = Common.Device.newBuilder()
      .setDeviceId(2).setManufacturer("Manufacturer2").setModel("Model2").setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(1).setName("Process1").setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder()
      .setPid(20).setDeviceId(1).setName("Process2").setState(Common.Process.State.ALIVE).build()
    val process3 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(2).setName("Process3").setState(Common.Process.State.ALIVE).build()
    // Mark all process as preferred processes as we are not testing the other processes flyout here.
    myProfilers.setPreferredDeviceAndProcessNames(null, "Process")

    myProfilerService.addDevice(device1)
    myProfilerService.addProcess(device1, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    myProfilerService.addProcess(device1, process2)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    var selectionAction = mySessionsView.processSelectionAction
    var processAction2 = selectionAction.childrenActions
      .first { c -> c.text == "Manufacturer1 Model1" }.childrenActions
      .first { c -> c.text == "Process2 (20)" }
    processAction2.actionPerformed(ActionEvent(processAction2, 0, ""))
    assertThat(myProfilers.device).isEqualTo(device1)
    assertThat(myProfilers.process).isEqualTo(process2)

    myProfilerService.addDevice(device2)
    myProfilerService.addProcess(device2, process3)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    var processAction3 = selectionAction.childrenActions
      .first { c -> c.text == "Manufacturer2 Model2" }.childrenActions
      .first { c -> c.text == "Process3 (10)" }
    processAction3.actionPerformed(ActionEvent(processAction3, 0, ""))
    assertThat(myProfilers.device).isEqualTo(device2)
    assertThat(myProfilers.process).isEqualTo(process3)
  }

  @Test
  fun testStopProfiling() {
    val device1 = Common.Device.newBuilder()
      .setDeviceId(1).setManufacturer("Manufacturer1").setModel("Model1").setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(1).setName("Process1").setState(Common.Process.State.ALIVE).build()

    val stopProfilingButton = mySessionsView.stopProfilingButton
    assertThat(stopProfilingButton.isEnabled).isFalse()

    myProfilerService.addDevice(device1)
    myProfilerService.addProcess(device1, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    val session = myProfilers.session
    assertThat(stopProfilingButton.isEnabled).isTrue()
    assertThat(mySessionsManager.profilingSession).isNotEqualTo(Common.Session.getDefaultInstance())
    assertThat(mySessionsManager.profilingSession).isEqualTo(session)

    stopProfilingButton.doClick()
    assertThat(stopProfilingButton.isEnabled).isFalse()
    assertThat(myProfilers.device).isNull()
    assertThat(myProfilers.process).isNull()
    assertThat(mySessionsManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myProfilers.session.sessionId).isEqualTo(session.sessionId)
  }

  @Test
  fun testImportSessionsFromHprofFile() {
    val sessionArtifacts = mySessionsView.sessionsList.model
    assertThat(sessionArtifacts.size).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    mySessionsManager.beginSession(device, process1)
    val session1 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(1)
    assertThat(sessionArtifacts.getElementAt(0).session).isEqualTo(session1)

    val session = mySessionsManager.createImportedSession("fake.hprof", Common.SessionMetaData.SessionType.MEMORY_CAPTURE, 0, 0, 0)
    mySessionsManager.update()
    mySessionsManager.setSession(session)
    assertThat(sessionArtifacts.size).isEqualTo(2)

    val selectedSession = mySessionsManager.selectedSession
    assertThat(session).isEqualTo(selectedSession)
    assertThat(myProfilers.selectedSessionMetaData.type).isEqualTo(Common.SessionMetaData.SessionType.MEMORY_CAPTURE)
  }

  @Test
  fun testSessionItemSelection() {
    val sessionsList = mySessionsView.sessionsList
    val sessionArtifacts = sessionsList.model
    assertThat(sessionArtifacts.size).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()
    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(10).setEndTime(11).build()
    val cpuTraceInfo = CpuProfiler.TraceInfo.newBuilder().setFromTimestamp(20).setToTimestamp(21).build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)
    myCpuService.addTraceInfo(cpuTraceInfo)

    myProfilerService.setTimestampNs(1)
    mySessionsManager.beginSession(device, process1)
    mySessionsManager.endCurrentSession()
    val session1 = mySessionsManager.selectedSession
    myProfilerService.setTimestampNs(2)
    mySessionsManager.beginSession(device, process2)
    mySessionsManager.endCurrentSession()
    val session2 = mySessionsManager.selectedSession

    assertThat(sessionArtifacts.size).isEqualTo(6)
    // Sessions are sorted in descending order.
    var sessionItem0 = sessionArtifacts.getElementAt(0) as SessionItem
    val cpuCaptureItem0 = sessionArtifacts.getElementAt(1) as CpuCaptureSessionArtifact
    val hprofItem0 = sessionArtifacts.getElementAt(2) as HprofSessionArtifact
    var sessionItem1 = sessionArtifacts.getElementAt(3) as SessionItem
    var cpuCaptureItem1 = sessionArtifacts.getElementAt(4) as CpuCaptureSessionArtifact
    var hprofItem1 = sessionArtifacts.getElementAt(5) as HprofSessionArtifact
    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(hprofItem0.session).isEqualTo(session2)
    assertThat(cpuCaptureItem0.session).isEqualTo(session2)
    assertThat(sessionItem1.session).isEqualTo(session1)
    assertThat(hprofItem1.session).isEqualTo(session1)
    assertThat(cpuCaptureItem1.session).isEqualTo(session1)

    // Selecting on the second item should select the session.
    assertThat(mySessionsManager.selectedSession).isEqualTo(session2)
    sessionsList.selectedIndex = 3
    assertThat(mySessionsManager.selectedSession).isEqualTo(session1)
  }

  @Test
  fun testCpuCaptureItemSelection() {
    val sessionsList = mySessionsView.sessionsList
    val sessionArtifacts = sessionsList.model
    assertThat(sessionArtifacts.size).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val traceInfoId = 13
    val cpuTraceInfo = CpuProfiler.TraceInfo.newBuilder()
      .setTraceId(traceInfoId)
      .setFromTimestamp(10)
      .setToTimestamp(11)
      .setProfilerType(CpuProfiler.CpuProfilerType.SIMPLEPERF)
      .build()
    myCpuService.addTraceInfo(cpuTraceInfo)

    myProfilerService.setTimestampNs(1)
    mySessionsManager.beginSession(device, process)
    mySessionsManager.endCurrentSession()
    val session = mySessionsManager.selectedSession

    assertThat(sessionArtifacts.size).isEqualTo(2)
    val sessionItem = sessionArtifacts.getElementAt(0) as SessionItem
    val cpuCaptureItem = sessionArtifacts.getElementAt(1) as CpuCaptureSessionArtifact
    assertThat(sessionItem.session).isEqualTo(session)
    assertThat(cpuCaptureItem.session).isEqualTo(session)
    assertThat(cpuCaptureItem.isOngoingCapture).isFalse()
    assertThat(cpuCaptureItem.name).isEqualTo(ProfilingConfiguration.SIMPLEPERF_ARTIFACT)

    // Prepare FakeCpuService to return a valid trace.
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS)
    myCpuService.setValidTrace(true)
    val traceBytes = ByteString.copyFrom(TestUtils.getWorkspaceFile(VALID_TRACE_PATH).readBytes())
    myCpuService.setTrace(traceBytes)

    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java) // Makes sure we're in monitor stage
    // Selecting the CpuCaptureSessionArtifact should open CPU profiler and select the capture
    sessionsList.selectedIndex = 1
    assertThat(myProfilers.stage).isInstanceOf(CpuProfilerStage::class.java) // Makes sure CPU profiler stage is now open
    val selectedCapture = (myProfilers.stage as CpuProfilerStage).capture
    // Makes sure that there is a capture selected and it's the one we clicked.
    assertThat(selectedCapture).isNotNull()
    assertThat(selectedCapture!!.traceId).isEqualTo(traceInfoId)
    assertThat(myProfilers.timeline.isStreaming).isFalse()
  }

  @Test
  fun testCpuOngoingCaptureItemSelection() {
    val sessionsList = mySessionsView.sessionsList
    val sessionArtifacts = sessionsList.model
    assertThat(sessionArtifacts.size).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val sessionStartNs = 1L

    // Sets an ongoing profiling configuration in the service
    val configuration = CpuProfiler.CpuProfilerConfiguration.newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.ATRACE).build()
    myCpuService.setOngoingCaptureConfiguration(configuration, sessionStartNs + 1)

    myProfilerService.setTimestampNs(sessionStartNs)
    mySessionsManager.beginSession(device, process)
    val session = mySessionsManager.selectedSession

    assertThat(sessionArtifacts.size).isEqualTo(2)
    val sessionItem = sessionArtifacts.getElementAt(0) as SessionItem
    val cpuCaptureItem = sessionArtifacts.getElementAt(1) as CpuCaptureSessionArtifact
    assertThat(sessionItem.session).isEqualTo(session)
    assertThat(cpuCaptureItem.session).isEqualTo(session)
    assertThat(cpuCaptureItem.isOngoingCapture).isTrue()
    assertThat(cpuCaptureItem.name).isEqualTo(ProfilingConfiguration.ATRACE)

    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java) // Makes sure we're in monitor stage
    // Selecting on the CpuCaptureSessionArtifact should open CPU profiler and select the capture
    sessionsList.selectedIndex = 1
    assertThat(myProfilers.stage).isInstanceOf(CpuProfilerStage::class.java) // Makes sure CPU profiler stage is now open
    val selectedCapture = (myProfilers.stage as CpuProfilerStage).capture
    // Makes sure that there is no capture selected, because the ongoing capture was not generated by a trace just yet.
    assertThat(selectedCapture).isNull()
    assertThat(myProfilers.timeline.isStreaming).isTrue()
  }

  @Test
  fun testMemoryItemSelection() {
    val sessionsList = mySessionsView.sessionsList
    val sessionArtifacts = sessionsList.model
    assertThat(sessionArtifacts.size).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()

    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(10).setEndTime(11).build();
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo);

    myProfilerService.setTimestampNs(1)
    mySessionsManager.beginSession(device, process)
    mySessionsManager.endCurrentSession()
    val session = mySessionsManager.selectedSession

    assertThat(sessionArtifacts.size).isEqualTo(2)
    var sessionItem = sessionArtifacts.getElementAt(0) as SessionItem
    var hprofItem = sessionArtifacts.getElementAt(1) as HprofSessionArtifact
    assertThat(sessionItem.session).isEqualTo(session)
    assertThat(hprofItem.session).isEqualTo(session)

    myMemoryService.setExplicitHeapDumpInfo(10, 11)
    myMemoryService.setExplicitSnapshotBuffer(ByteArray(0))
    myMemoryService.setExplicitHeapDumpStatus(MemoryProfiler.TriggerHeapDumpResponse.Status.SUCCESS)
    myMemoryService.setExplicitDumpDataStatus(MemoryProfiler.DumpDataResponse.Status.SUCCESS);
    // Because we do not provide valid data for heap dump, memory stage would fail to load and set selectedCapture back to null.
    // To prevent the loading function from getting called, we register the session change listener with a FakeCaptureObjectLoader.
    myProfilers.registerSessionChangeListener(
      Common.SessionMetaData.SessionType.MEMORY_CAPTURE, {
      myProfilers.stage = MemoryProfilerStage(myProfilers, FakeCaptureObjectLoader())
    }
    )

    // Makes sure we're in monitor stage.
    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java)
    // Selecting on the HprofSessionArtifact should open Memory profiler and select the capture.
    sessionsList.selectedIndex = 1
    // Makes sure memory profiler stage is now open.
    assertThat(myProfilers.stage).isInstanceOf(MemoryProfilerStage::class.java)
    // Makes sure a HeapDumpCaptureObject is loaded.
    assertThat((myProfilers.stage as MemoryProfilerStage).selectedCapture).isInstanceOf(HeapDumpCaptureObject::class.java)
  }
}