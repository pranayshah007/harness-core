/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.rule.OwnerRule.KAMAL;

import static software.wings.common.VerificationConstants.TOTAL_HITS_PER_MIN_THRESHOLD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.common.DataCollectionExecutorService;
import io.harness.rule.Owner;
import io.harness.time.Timestamp;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.DelegateCVActivityLogService.Logger;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.LogAnalysisStoreService;
import software.wings.service.impl.analysis.LogDataCollectionInfoV2;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.intfc.analysis.ClusterLevel;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class LogDataCollectionTaskTest extends WingsBaseTest {
  private LogDataCollectionTask<LogDataCollectionInfoV2> logDataCollectionTask;
  @Mock private LogDataCollector<LogDataCollectionInfoV2> logDataCollector;
  @Mock private LogAnalysisStoreService logAnalysisStoreService;
  @Inject private DataCollectionExecutorService dataCollectionService;
  @Mock private Logger activityLogger;
  @Before
  public void setupTests() throws IllegalAccessException, IOException {
    initMocks(this);
    logDataCollectionTask = mock(LogDataCollectionTask.class, Mockito.CALLS_REAL_METHODS);
    when(logDataCollector.getHostBatchSize()).thenReturn(1);
    when(logDataCollectionTask.getDataCollector()).thenReturn(logDataCollector);
    dataCollectionService = spy(dataCollectionService);
    FieldUtils.writeField(logDataCollectionTask, "dataCollectionService", dataCollectionService, true);
    FieldUtils.writeField(logDataCollectionTask, "logAnalysisStoreService", logAnalysisStoreService, true);
    when(logDataCollectionTask.getActivityLogger()).thenReturn(activityLogger);
    when(logAnalysisStoreService.save(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(true);
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSavingHeartbeatsForAllHosts() throws DataCollectionException, IOException {
    LogDataCollectionInfoV2 logDataCollectionInfo = createLogDataCollectionInfo();
    when(logDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1", "host2", "host3", "host4"));
    Instant now = Instant.ofEpochMilli(Timestamp.currentMinuteBoundary());
    when(logDataCollectionInfo.getStartTime()).thenReturn(now.minus(10, ChronoUnit.MINUTES));
    when(logDataCollectionInfo.getEndTime()).thenReturn(now);
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    logDataCollectionTask.collectAndSaveData(logDataCollectionInfo);
    verify(logAnalysisStoreService)
        .save(eq(logDataCollectionInfo.getStateType()), any(), any(), any(), any(), any(), any(), any(), any(),
            captor.capture());
    List<LogElement> capturedList = captor.getValue();
    assertThat(capturedList.size()).isEqualTo(40);
    assertThat(capturedList.stream().allMatch(
                   logElement -> logElement.getClusterLabel().equals(ClusterLevel.H2.getLevel() + "")))
        .isTrue();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testIfFetchCalledForEachHostParallelly() throws DataCollectionException, IOException {
    LogDataCollectionInfoV2 logDataCollectionInfo = createLogDataCollectionInfo();
    when(logDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1", "host2", "host3"));
    Instant now = Instant.now();
    when(logDataCollectionInfo.getStartTime()).thenReturn(now.minus(10, ChronoUnit.MINUTES));
    when(logDataCollectionInfo.getEndTime()).thenReturn(now);
    logDataCollectionTask.collectAndSaveData(logDataCollectionInfo);
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(dataCollectionService).executeParrallel(captor.capture());
    assertThat(captor.getValue().size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchLog_calledForEachHostBatchParallelly() throws DataCollectionException, IOException {
    LogDataCollectionInfoV2 logDataCollectionInfo = createLogDataCollectionInfo();
    when(logDataCollector.getHostBatchSize()).thenReturn(2);
    when(logDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1", "host2", "host3"));
    Instant now = Instant.now();
    when(logDataCollectionInfo.getStartTime()).thenReturn(now.minus(10, ChronoUnit.MINUTES));
    when(logDataCollectionInfo.getEndTime()).thenReturn(now);
    logDataCollectionTask.collectAndSaveData(logDataCollectionInfo);
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(dataCollectionService).executeParrallel(captor.capture());
    ArgumentCaptor<List> hostCapture = ArgumentCaptor.forClass(List.class);
    verify(logDataCollector, times(2)).fetchLogs(hostCapture.capture());
    assertThat(captor.getValue().size()).isEqualTo(2);
    assertThat(hostCapture.getAllValues().get(0)).isEqualTo(Lists.newArrayList("host1", "host3"));
    assertThat(hostCapture.getAllValues().get(1)).isEqualTo(Lists.newArrayList("host2"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCollectAndSave_whenLogElementsSaveCallReturnsFalse() throws DataCollectionException, IOException {
    when(logAnalysisStoreService.save(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(false);
    LogDataCollectionInfoV2 logDataCollectionInfo = createLogDataCollectionInfo();
    when(logDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1", "host2", "host3"));
    Instant now = Instant.now();
    when(logDataCollectionInfo.getStartTime()).thenReturn(now.minus(10, ChronoUnit.MINUTES));
    when(logDataCollectionInfo.getEndTime()).thenReturn(now);
    assertThatThrownBy(() -> logDataCollectionTask.collectAndSaveData(logDataCollectionInfo))
        .isInstanceOf(DataCollectionException.class)
        .hasMessage("Unable to save log elements. Manager API returned false.");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCollectAndSave_IfNumberOfLogsPerHostsAreLessThanTheThreshold()
      throws DataCollectionException, IOException {
    LogDataCollectionInfoV2 logDataCollectionInfo = createLogDataCollectionInfo();
    when(logDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1"));
    Instant now = Instant.ofEpochMilli(Timestamp.currentMinuteBoundary());
    when(logDataCollectionInfo.getStartTime()).thenReturn(now.minus(1, ChronoUnit.MINUTES));
    when(logDataCollectionInfo.getEndTime()).thenReturn(now);
    int numberOfLogs = (int) TOTAL_HITS_PER_MIN_THRESHOLD;
    List<LogElement> logElements = getLogElements(numberOfLogs);
    logElements.forEach(logElement -> logElement.setHost("host1"));
    when(logDataCollector.fetchLogs(any())).thenReturn(logElements);
    logDataCollectionTask.collectAndSaveData(logDataCollectionInfo);
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(logAnalysisStoreService)
        .save(eq(logDataCollectionInfo.getStateType()), any(), any(), any(), any(), any(), any(), any(), any(),
            captor.capture());
    List<LogElement> capturedList = captor.getValue();
    assertThat(capturedList.size()).isEqualTo(TOTAL_HITS_PER_MIN_THRESHOLD + 1);
    verifyNoInteractions(activityLogger);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCollectAndSave_IfNumberOfLogsPerHostsAreMoreThanTheThreshold() throws DataCollectionException {
    LogDataCollectionInfoV2 logDataCollectionInfo = createLogDataCollectionInfo();
    when(logDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1"));
    Instant now = Instant.ofEpochMilli(Timestamp.currentMinuteBoundary());
    when(logDataCollectionInfo.getStartTime()).thenReturn(now.minus(1, ChronoUnit.MINUTES));
    when(logDataCollectionInfo.getEndTime()).thenReturn(now);
    int numberOfLogs = (int) TOTAL_HITS_PER_MIN_THRESHOLD + 1;
    List<LogElement> logElements = getLogElements(numberOfLogs);
    logElements.forEach(logElement -> logElement.setHost("host1"));
    when(logDataCollector.fetchLogs(any())).thenReturn(logElements);
    assertThatThrownBy(() -> logDataCollectionTask.collectAndSaveData(logDataCollectionInfo))
        .isInstanceOf(DataCollectionException.class);
    verify(activityLogger)
        .error(eq("Too many logs(" + numberOfLogs
            + ") for host host1, Please refine your query. The threshold per minute is "
            + TOTAL_HITS_PER_MIN_THRESHOLD));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCollectAndSave_IfNumberOfLogsPerHostsAreMoreThanTheThresholdForMultipleMinutes()
      throws DataCollectionException {
    LogDataCollectionInfoV2 logDataCollectionInfo = createLogDataCollectionInfo();
    when(logDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1"));
    Instant now = Instant.ofEpochMilli(Timestamp.currentMinuteBoundary());
    when(logDataCollectionInfo.getStartTime()).thenReturn(now.minus(15, ChronoUnit.MINUTES));
    when(logDataCollectionInfo.getEndTime()).thenReturn(now);
    int numberOfLogs = (int) TOTAL_HITS_PER_MIN_THRESHOLD * 15 + 1;
    List<LogElement> logElements = getLogElements(numberOfLogs);
    logElements.forEach(logElement -> logElement.setHost("host1"));
    when(logDataCollector.fetchLogs(any())).thenReturn(logElements);
    assertThatThrownBy(() -> logDataCollectionTask.collectAndSaveData(logDataCollectionInfo))
        .isInstanceOf(DataCollectionException.class);
    verify(activityLogger)
        .error(eq("Too many logs(" + numberOfLogs
            + ") for host host1, Please refine your query. The threshold per minute is "
            + TOTAL_HITS_PER_MIN_THRESHOLD));
  }

  private List<LogElement> getLogElements(int size) {
    List<LogElement> logElements = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      logElements.add(getLogElement());
    }
    return logElements;
  }

  private LogElement getLogElement() {
    return LogElement.builder().host(UUID.randomUUID().toString()).build();
  }

  public LogDataCollectionInfoV2 createLogDataCollectionInfo() {
    DelegateStateType stateType = DelegateStateType.SPLUNKV2;
    LogDataCollectionInfoV2 dataCollectionInfoV2 = mock(LogDataCollectionInfoV2.class);
    when(dataCollectionInfoV2.getStateType()).thenReturn(stateType);
    Instant now = Instant.now();
    when(dataCollectionInfoV2.getStartTime()).thenReturn(now.minus(10, ChronoUnit.MINUTES));
    when(dataCollectionInfoV2.getEndTime()).thenReturn(now);
    when(dataCollectionInfoV2.isShouldSendHeartbeat()).thenReturn(true);
    return dataCollectionInfoV2;
  }
}
