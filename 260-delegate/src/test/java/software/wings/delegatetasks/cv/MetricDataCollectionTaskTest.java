/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import software.wings.beans.dto.NewRelicMetricDataRecord;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.MetricDataStoreService;
import software.wings.service.impl.analysis.MetricElement;
import software.wings.service.impl.analysis.MetricsDataCollectionInfo;
import software.wings.service.intfc.analysis.ClusterLevel;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class MetricDataCollectionTaskTest extends WingsBaseTest {
  private MetricsDataCollectionTask<MetricsDataCollectionInfo> metricsDataCollectionTask;
  @Mock private MetricsDataCollector<MetricsDataCollectionInfo> metricsDataCollector;
  @Inject private DataCollectionExecutorService dataCollectionService;
  @Mock private MetricDataStoreService metricStoreService;

  @Before
  public void setupTests() throws Exception {
    initMocks(this);
    metricsDataCollectionTask = mock(MetricsDataCollectionTask.class, Mockito.CALLS_REAL_METHODS);
    when(metricsDataCollector.getHostBatchSize()).thenReturn(1);
    when(metricsDataCollectionTask.getDataCollector()).thenReturn(metricsDataCollector);
    dataCollectionService = spy(dataCollectionService);
    FieldUtils.writeField(metricsDataCollectionTask, "dataCollectionService", dataCollectionService, true);
    FieldUtils.writeField(metricsDataCollectionTask, "metricStoreService", metricStoreService, true);
    when(metricStoreService.saveNewRelicMetrics(any(), any(), any(), any(), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSavingHeartbeatsForAllHosts() throws Exception {
    MetricsDataCollectionInfo metricsDataCollectionInfo = createMetricDataCollectionInfo();
    when(metricsDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1", "host2", "host3", "host4"));
    Map<String, String> hostToGroupNameMap = new HashMap<>();
    hostToGroupNameMap.put("host1", "default");
    hostToGroupNameMap.put("host2", "default");
    hostToGroupNameMap.put("host3", "group1");
    hostToGroupNameMap.put("host4", "group2");
    when(metricsDataCollectionInfo.getHostsToGroupNameMap()).thenReturn(hostToGroupNameMap);
    Instant now = Instant.ofEpochMilli(Timestamp.currentMinuteBoundary());
    when(metricsDataCollectionInfo.getEndTime()).thenReturn(now.plus(1, ChronoUnit.MINUTES));
    when(metricsDataCollectionInfo.getStartTime()).thenReturn(now);
    when(metricsDataCollectionInfo.getDataCollectionStartTime()).thenReturn(now);
    metricsDataCollectionTask.collectAndSaveData(metricsDataCollectionInfo);
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(metricStoreService).saveNewRelicMetrics(any(), any(), any(), any(), captor.capture());
    List<NewRelicMetricDataRecord> capturedList = captor.getValue();
    assertThat(capturedList.size()).isEqualTo(3);
    assertThat(capturedList.stream().allMatch(
                   newRelicMetricDataRecord -> newRelicMetricDataRecord.getName().equals("Harness heartbeat metric")))
        .isTrue();
    assertThat(capturedList.stream().allMatch(
                   newRelicMetricDataRecord -> newRelicMetricDataRecord.getLevel() == ClusterLevel.H0))
        .isTrue();
    assertThat(capturedList.stream().allMatch(newRelicMetricDataRecord
                   -> newRelicMetricDataRecord.getTimeStamp() == metricsDataCollectionInfo.getEndTime().toEpochMilli()))
        .isTrue();
    assertThat(capturedList.stream()
                   .map(newRelicMetricDataRecord -> newRelicMetricDataRecord.getDataCollectionMinute())
                   .collect(Collectors.toList()))
        .isEqualTo(Lists.newArrayList(0, 0, 0));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCollectAndSaveData_shouldSendHeartbeatFalse() throws DataCollectionException {
    MetricsDataCollectionInfo metricsDataCollectionInfo = createMetricDataCollectionInfo();
    when(metricsDataCollectionInfo.isShouldSendHeartbeat()).thenReturn(false);
    when(metricsDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1", "host2", "host3", "host4"));
    Map<String, String> hostToGroupNameMap = new HashMap<>();
    hostToGroupNameMap.put("host1", "default");
    hostToGroupNameMap.put("host2", "default");
    hostToGroupNameMap.put("host3", "group1");
    hostToGroupNameMap.put("host4", "group2");
    when(metricsDataCollectionInfo.getHostsToGroupNameMap()).thenReturn(hostToGroupNameMap);
    Instant now = Instant.ofEpochMilli(Timestamp.currentMinuteBoundary());
    when(metricsDataCollectionInfo.getEndTime()).thenReturn(now.plus(1, ChronoUnit.MINUTES));
    when(metricsDataCollectionInfo.getStartTime()).thenReturn(now);
    when(metricsDataCollectionInfo.getDataCollectionStartTime()).thenReturn(now);
    metricsDataCollectionTask.collectAndSaveData(metricsDataCollectionInfo);
    verifyNoInteractions(metricStoreService);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCollectAndSaveData_forBatchingOfSaveCallByHost() throws Exception {
    MetricsDataCollectionInfo metricsDataCollectionInfo = createMetricDataCollectionInfo();
    when(metricsDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1", "host2"));
    Map<String, String> hostToGroupNameMap = new HashMap<>();
    hostToGroupNameMap.put("host1", "default");
    hostToGroupNameMap.put("host2", "default");
    MetricElement metricElementHost1 = MetricElement.builder()
                                           .name("metric1")
                                           .host("host1")
                                           .groupName("default")
                                           .timestamp(System.currentTimeMillis())
                                           .build();
    MetricElement metricElementHost2 = MetricElement.builder()
                                           .name("metric1")
                                           .host("host2")
                                           .groupName("default")
                                           .timestamp(System.currentTimeMillis())
                                           .build();
    when(metricsDataCollector.fetchMetrics(any()))
        .thenReturn(Lists.newArrayList(metricElementHost1))
        .thenReturn(Lists.newArrayList(metricElementHost2));
    when(metricsDataCollectionInfo.getHostsToGroupNameMap()).thenReturn(hostToGroupNameMap);
    Instant now = Instant.ofEpochMilli(Timestamp.currentMinuteBoundary());
    when(metricsDataCollectionInfo.getEndTime()).thenReturn(now.plus(1, ChronoUnit.MINUTES));
    when(metricsDataCollectionInfo.getStartTime()).thenReturn(now);
    when(metricsDataCollectionInfo.getDataCollectionStartTime()).thenReturn(now);
    metricsDataCollectionTask.collectAndSaveData(metricsDataCollectionInfo);
    verify(metricStoreService, times(3)).saveNewRelicMetrics(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCollectAndSaveData_withoutHostWithAbsoluteMinute() throws Exception {
    MetricsDataCollectionInfo metricsDataCollectionInfo = createMetricDataCollectionInfo();
    when(metricsDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet());
    Map<String, String> hostToGroupNameMap = new HashMap<>();
    hostToGroupNameMap.put("DUMMY_24_7_HOST", DEFAULT_GROUP_NAME);
    when(metricsDataCollectionInfo.getHostsToGroupNameMap()).thenReturn(hostToGroupNameMap);
    Instant now = Instant.ofEpochMilli(Timestamp.currentMinuteBoundary());
    when(metricsDataCollectionInfo.getStartTime()).thenReturn(now.minus(10, ChronoUnit.MINUTES));
    when(metricsDataCollectionInfo.getEndTime()).thenReturn(now);
    metricsDataCollectionTask.collectAndSaveData(metricsDataCollectionInfo);
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(metricStoreService).saveNewRelicMetrics(any(), any(), any(), any(), captor.capture());
    List<NewRelicMetricDataRecord> capturedList = captor.getValue();
    assertThat(capturedList.size()).isEqualTo(1);
    assertThat(capturedList.get(0).getName()).isEqualTo("Harness heartbeat metric");
    assertThat(capturedList.get(0).getLevel()).isEqualTo(ClusterLevel.H0);
    assertThat(capturedList.get(0).getTimeStamp()).isEqualTo(metricsDataCollectionInfo.getEndTime().toEpochMilli());
    assertThat(capturedList.get(0).getDataCollectionMinute())
        .isEqualTo(TimeUnit.MILLISECONDS.toMinutes(metricsDataCollectionInfo.getEndTime().toEpochMilli()));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testIfFetchCalledForEachHostParallelly() throws DataCollectionException {
    MetricsDataCollectionInfo metricsDataCollectionInfo = createMetricDataCollectionInfo();
    when(metricsDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1", "host2", "host3"));
    Instant now = Instant.now();
    when(metricsDataCollectionInfo.getStartTime()).thenReturn(now.minus(10, ChronoUnit.MINUTES));
    when(metricsDataCollectionInfo.getEndTime()).thenReturn(now);
    metricsDataCollectionTask.collectAndSaveData(metricsDataCollectionInfo);
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(dataCollectionService).executeParrallel(captor.capture());
    assertThat(captor.getValue().size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testcollectAndSaveData_IfNewRelicMetricDataRecordsAreSaved() throws Exception {
    MetricsDataCollectionInfo metricsDataCollectionInfo = createMetricDataCollectionInfo();
    when(metricsDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1"));
    Instant now = Instant.now();
    when(metricsDataCollectionInfo.getStartTime()).thenReturn(now.minus(10, ChronoUnit.MINUTES));
    when(metricsDataCollectionInfo.getEndTime()).thenReturn(now);
    MetricElement metricElement = MetricElement.builder()
                                      .name("metric1")
                                      .host("host1")
                                      .groupName("default")
                                      .timestamp(System.currentTimeMillis())
                                      .build();
    when(metricsDataCollector.fetchMetrics(any())).thenReturn(Lists.newArrayList(metricElement));
    metricsDataCollectionTask.collectAndSaveData(metricsDataCollectionInfo);

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(metricStoreService, times(2)).saveNewRelicMetrics(any(), any(), any(), any(), captor.capture());
    List<NewRelicMetricDataRecord> records = captor.getAllValues().get(0);
    assertThat(records.size()).isEqualTo(1);
    assertThat(records.get(0).getStateExecutionId()).isEqualTo(metricsDataCollectionInfo.getStateExecutionId());
    assertThat(records.get(0).getServiceId()).isEqualTo(metricsDataCollectionInfo.getServiceId());
    assertThat(records.get(0).getHost()).isEqualTo("host1");
    assertThat(records.get(0).getGroupName()).isEqualTo(metricElement.getGroupName());
    assertThat(records.get(0).getName()).isEqualTo(metricElement.getName());
    assertThat(records.get(0).getTimeStamp()).isEqualTo(metricElement.getTimestamp());
    assertThat(records.get(0).getStateType()).isEqualTo(metricsDataCollectionInfo.getStateType());
    assertThat(records.get(0).getDataCollectionMinute())
        .isEqualTo(TimeUnit.MILLISECONDS.toMinutes(metricElement.getTimestamp()));
    assertThat(records.get(0).getCvConfigId()).isEqualTo(metricsDataCollectionInfo.getCvConfigId());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testcollectAndSaveData_IfNewRelicMetricDataRecordsAreSavedWithRelativeMinute() throws Exception {
    MetricsDataCollectionInfo metricsDataCollectionInfo = createMetricDataCollectionInfo();
    when(metricsDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1"));
    Instant now = Instant.now();
    when(metricsDataCollectionInfo.getStartTime()).thenReturn(now.minus(1, ChronoUnit.MINUTES));
    when(metricsDataCollectionInfo.getEndTime()).thenReturn(now);
    when(metricsDataCollectionInfo.getDataCollectionStartTime()).thenReturn(now.minus(5, ChronoUnit.MINUTES));
    MetricElement metricElement = MetricElement.builder()
                                      .name("metric1")
                                      .host("host1")
                                      .groupName("default")
                                      .timestamp(System.currentTimeMillis())
                                      .build();
    when(metricsDataCollector.fetchMetrics(any())).thenReturn(Lists.newArrayList(metricElement));
    metricsDataCollectionTask.collectAndSaveData(metricsDataCollectionInfo);

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(metricStoreService, times(2)).saveNewRelicMetrics(any(), any(), any(), any(), captor.capture());
    List<NewRelicMetricDataRecord> records = captor.getAllValues().get(0);
    assertThat(records.size()).isEqualTo(1);
    assertThat(records.get(0).getStateExecutionId()).isEqualTo(metricsDataCollectionInfo.getStateExecutionId());
    assertThat(records.get(0).getServiceId()).isEqualTo(metricsDataCollectionInfo.getServiceId());
    assertThat(records.get(0).getHost()).isEqualTo("host1");
    assertThat(records.get(0).getGroupName()).isEqualTo(metricElement.getGroupName());
    assertThat(records.get(0).getName()).isEqualTo(metricElement.getName());
    assertThat(records.get(0).getTimeStamp()).isEqualTo(metricElement.getTimestamp());
    assertThat(records.get(0).getStateType()).isEqualTo(metricsDataCollectionInfo.getStateType());
    assertThat(records.get(0).getDataCollectionMinute()).isEqualTo(5);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCollectAndSaveData_withNewRelicRecordsSaveCallsReturnsFalse() throws Exception {
    when(metricStoreService.saveNewRelicMetrics(any(), any(), any(), any(), any())).thenReturn(false);
    MetricsDataCollectionInfo metricsDataCollectionInfo = createMetricDataCollectionInfo();
    when(metricsDataCollectionInfo.getHosts()).thenReturn(Sets.newHashSet("host1"));
    Instant now = Instant.now();
    when(metricsDataCollectionInfo.getStartTime()).thenReturn(now.minus(10, ChronoUnit.MINUTES));
    when(metricsDataCollectionInfo.getEndTime()).thenReturn(now);
    MetricElement metricElement = MetricElement.builder()
                                      .name("metric1")
                                      .host("host1")
                                      .groupName("default")
                                      .timestamp(System.currentTimeMillis())
                                      .build();
    when(metricsDataCollector.fetchMetrics(any())).thenReturn(Lists.newArrayList(metricElement));
    assertThatThrownBy(() -> metricsDataCollectionTask.collectAndSaveData(metricsDataCollectionInfo))
        .isInstanceOf(DataCollectionException.class)
        .hasMessage("Unable to save metrics elements. Manager API returned false");
  }

  private MetricsDataCollectionInfo createMetricDataCollectionInfo() {
    DelegateStateType stateType = DelegateStateType.NEW_RELIC;
    MetricsDataCollectionInfo dataCollectionInfo = mock(MetricsDataCollectionInfo.class);
    when(dataCollectionInfo.getAccountId()).thenReturn(UUID.randomUUID().toString());
    when(dataCollectionInfo.getApplicationId()).thenReturn(UUID.randomUUID().toString());
    when(dataCollectionInfo.getStateExecutionId()).thenReturn(UUID.randomUUID().toString());
    when(dataCollectionInfo.getStateType()).thenReturn(stateType);
    when(dataCollectionInfo.getHostsToGroupNameMap()).thenReturn(new HashMap<>());
    Instant now = Instant.now();
    when(dataCollectionInfo.getStartTime()).thenReturn(now.minus(10, ChronoUnit.MINUTES));
    when(dataCollectionInfo.getEndTime()).thenReturn(now);
    when(dataCollectionInfo.isShouldSendHeartbeat()).thenReturn(true);
    return dataCollectionInfo;
  }
}
