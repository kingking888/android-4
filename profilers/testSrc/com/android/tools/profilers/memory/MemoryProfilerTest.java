/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_ID;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_DETACHED_RESPONSE;
import static org.junit.Assert.assertArrayEquals;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.idea.transport.faketransport.commands.StartAllocTracking;
import com.android.tools.idea.transport.faketransport.commands.StopAllocTracking;
import com.android.tools.perflib.heap.SnapshotBuilder;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.HeapDumpInfo;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.network.FakeNetworkService;
import com.google.common.truth.Truth;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class MemoryProfilerTest {
  @Parameterized.Parameters
  public static Collection<Boolean> useUnifiedPipeline() {
    return Arrays.asList(false, true);
  }

  private static final int FAKE_PID = 111;
  private static final Common.Session TEST_SESSION = Common.Session.newBuilder().setSessionId(1).setPid(FAKE_PID).build();
  private static final long DEVICE_STARTTIME_NS = 0;

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer, false);
  private final FakeMemoryService myMemoryService = new FakeMemoryService();
  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("MemoryProfilerTest", myMemoryService, myTransportService, new FakeProfilerService(myTimer), new FakeEventService(),
                        new FakeCpuService(),
                        FakeNetworkService.newBuilder().build());

  private StudioProfilers myStudioProfiler;
  private FakeIdeProfilerServices myIdeProfilerServices;

  private final boolean myUnifiedPipeline;

  public MemoryProfilerTest(boolean useUnifiedPipeline) {
    myUnifiedPipeline = useUnifiedPipeline;
  }

  @Before
  public void setUp() {
    myIdeProfilerServices = new FakeIdeProfilerServices();
    myIdeProfilerServices.enableEventsPipeline(myUnifiedPipeline);
    myStudioProfiler = new StudioProfilers(new ProfilerClient(myGrpcChannel.getName()), myIdeProfilerServices, myTimer);
  }

  @Test
  public void testStartMonitoring() {
    Assume.assumeFalse("Unified pipeline does not go through StartMonitoringApp", myUnifiedPipeline);

    MemoryProfiler memoryProfiler = new MemoryProfiler(myStudioProfiler);
    memoryProfiler.startProfiling(TEST_SESSION);
    Truth.assertThat(myMemoryService.getProcessId()).isEqualTo(FAKE_PID);
  }

  @Test
  public void testStopMonitoring() {
    Assume.assumeFalse("Unified pipeline does not go through StopMonitoringApp", myUnifiedPipeline);

    MemoryProfiler memoryProfiler = new MemoryProfiler(myStudioProfiler);
    memoryProfiler.stopProfiling(TEST_SESSION);
    Truth.assertThat(myMemoryService.getProcessId()).isEqualTo(FAKE_PID);
  }

  @Test
  public void testStopMonitoringCallsStopTracking() {
    MemoryProfiler memoryProfiler = new MemoryProfiler(myStudioProfiler);

    // We stop any ongoing (legacy or jvmti) allocation tracking session before stopping the memory profiler.
    if (myUnifiedPipeline) {
      StopAllocTracking stopAllocTracking =
        (StopAllocTracking)myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_ALLOC_TRACKING);
      Truth.assertThat(stopAllocTracking.getLastInfo()).isEqualTo(Memory.AllocationsInfo.getDefaultInstance());

      memoryProfiler.stopProfiling(TEST_SESSION);
      Truth.assertThat(stopAllocTracking.getLastInfo()).isNotEqualTo(Memory.AllocationsInfo.getDefaultInstance());
    }
    else {
      Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(0);
      memoryProfiler.stopProfiling(TEST_SESSION);
      Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(1);
    }
  }

  @Test
  public void testLiveAllocationTrackingOnAgentAttach() {
    myIdeProfilerServices.enableLiveAllocationTracking(true);
    setupODeviceAndProcess();

    Truth.assertThat(myStudioProfiler.isAgentAttached()).isFalse();
    if (myUnifiedPipeline) {
      Truth.assertThat(
        ((StartAllocTracking)myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING)).getLastInfo())
        .isEqualTo(Memory.AllocationsInfo.getDefaultInstance());
    }
    else {
      Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(0);
    }

    // Advance the timer to select the device + process
    myTransportService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isTrue();
    if (myUnifiedPipeline) {
      // We first call stop tracking before starting.
      Truth.assertThat(
        ((StopAllocTracking)myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_ALLOC_TRACKING)).getLastInfo())
        .isNotEqualTo(Memory.AllocationsInfo.getDefaultInstance());
      Truth.assertThat(
        ((StartAllocTracking)myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING)).getLastInfo())
        .isNotEqualTo(Memory.AllocationsInfo.getDefaultInstance());
    }
    else {
      Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(2);
    }
  }

  @Test
  public void liveAllocationTrackingDidNotStartIfAgentIsNotAttached() {
    myIdeProfilerServices.enableLiveAllocationTracking(true);
    setupODeviceAndProcess();

    myTransportService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isTrue();

    StopAllocTracking stopAllocTracking =
      (StopAllocTracking)myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_ALLOC_TRACKING);
    StartAllocTracking startAllocTracking =
      (StartAllocTracking)myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING);
    if (myUnifiedPipeline) {
      // We first call stop tracking before starting.
      Truth.assertThat(stopAllocTracking.getLastInfo()).isNotEqualTo(Memory.AllocationsInfo.getDefaultInstance());
      Truth.assertThat(startAllocTracking.getLastInfo()).isNotEqualTo(Memory.AllocationsInfo.getDefaultInstance());
      stopAllocTracking.setLastInfo(Memory.AllocationsInfo.getDefaultInstance());
      startAllocTracking.setLastInfo(Memory.AllocationsInfo.getDefaultInstance());

      myTransportService.addEventToStream(myStudioProfiler.getSession().getStreamId(), Common.Event.newBuilder()
        .setPid(myStudioProfiler.getSession().getPid())
        .setKind(Common.Event.Kind.AGENT)
        .setAgentData(DEFAULT_AGENT_DETACHED_RESPONSE)
        .build());
    }
    else {
      Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(2);

      myMemoryService.resetTrackAllocationCount();
      myTransportService.setAgentStatus(DEFAULT_AGENT_DETACHED_RESPONSE);
    }

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStudioProfiler.changed(ProfilerAspect.AGENT);
    Truth.assertThat(myStudioProfiler.isAgentAttached()).isFalse();
    if (myUnifiedPipeline) {
      Truth.assertThat(stopAllocTracking.getLastInfo()).isEqualTo(Memory.AllocationsInfo.getDefaultInstance());
      Truth.assertThat(startAllocTracking.getLastInfo()).isEqualTo(Memory.AllocationsInfo.getDefaultInstance());
    }
    else {
      Truth.assertThat(myMemoryService.getTrackAllocationCount()).isEqualTo(0);
    }
  }

  @Test
  public void testSaveHeapDumpToFile() {
    Assume.assumeFalse("Unified pipeline import cannot yet be tested because of dependencies on TransportService.getInstance().",
                       myUnifiedPipeline);

    long startTimeNs = 3;
    long endTimeNs = 8;
    HeapDumpInfo dumpInfo = HeapDumpInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    // Load in a simple Snapshot and verify the MemoryObject hierarchy:
    // - 1 holds reference to 2
    // - single root object in default heap
    SnapshotBuilder snapshotBuilder = new SnapshotBuilder(2, 0, 0)
      .addReferences(1, 2)
      .addRoot(1);
    byte[] buffer = snapshotBuilder.getByteBuffer();
    myTransportService.addFile(Long.toString(startTimeNs), ByteString.copyFrom(buffer));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    MemoryProfiler.saveHeapDumpToFile(myStudioProfiler.getClient(), ProfilersTestData.SESSION_DATA, dumpInfo, baos,
                                      myStudioProfiler.getIdeServices().getFeatureTracker());
    assertArrayEquals(buffer, baos.toByteArray());
  }

  @Test
  public void testgetAllocationInfosForSession() {
    Assume.assumeTrue(myUnifiedPipeline);

    Common.Session session = myStudioProfiler.getSession();

    // Insert a completed info.
    Memory.AllocationsInfo info1 = Memory.AllocationsInfo.newBuilder()
      .setStartTime(1).setEndTime(2).setSuccess(true).setLegacy(true).build();
    myTransportService.addEventToStream(
      session.getStreamId(),
      ProfilersTestData.generateMemoryAllocationInfoData(1, session.getPid(), info1).build());

    List<Memory.AllocationsInfo> infos = MemoryProfiler.getAllocationInfosForSession(myStudioProfiler.getClient(),
                                                                                     session,
                                                                                     new Range(0, 10),
                                                                                     myStudioProfiler.getIdeServices());
    Truth.assertThat(infos).containsExactly(info1);

    // Insert a not yet completed info followed up by a generic end event.
    Memory.AllocationsInfo info2 = Memory.AllocationsInfo.newBuilder().setStartTime(5).setEndTime(Long.MAX_VALUE).setLegacy(true).build();
    myTransportService.addEventToStream(
      session.getStreamId(),
      Common.Event.newBuilder().setTimestamp(5).setGroupId(5).setKind(Common.Event.Kind.MEMORY_ALLOC_TRACKING)
        .setPid(session.getPid())
        .setMemoryAllocTracking(Memory.MemoryAllocTrackingData.newBuilder().setInfo(info2))
        .build());
    myTransportService.addEventToStream(
      session.getStreamId(),
      Common.Event.newBuilder().setTimestamp(10).setGroupId(5).setKind(Common.Event.Kind.MEMORY_ALLOC_TRACKING)
        .setPid(session.getPid()).setIsEnded(true).build());
    infos = MemoryProfiler.getAllocationInfosForSession(myStudioProfiler.getClient(),
                                                        session,
                                                        new Range(0, 10),
                                                        myStudioProfiler.getIdeServices());
    Truth.assertThat(infos).containsExactly(info1, info2.toBuilder().setEndTime(session.getEndTimestamp()).setSuccess(false).build());
  }

  private void setupODeviceAndProcess() {
    Common.Device device = Common.Device.newBuilder()
      .setDeviceId(FAKE_DEVICE_ID)
      .setSerial("FakeDevice")
      .setState(Common.Device.State.ONLINE)
      .setFeatureLevel(AndroidVersion.VersionCodes.O)
      .build();
    Common.Process process = Common.Process.newBuilder()
      .setPid(20)
      .setDeviceId(FAKE_DEVICE_ID)
      .setState(Common.Process.State.ALIVE)
      .setName("FakeProcess")
      .setStartTimestampNs(DEVICE_STARTTIME_NS)
      .build();
    myTransportService.addDevice(device);
    myTransportService.addProcess(device, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStudioProfiler.setProcess(device, process);
  }
}