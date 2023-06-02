/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static software.wings.common.VerificationConstants.CV_24x7_STATE_EXECUTION;
import static software.wings.utils.StackDriverUtils.createStackDriverConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.common.DataCollectionExecutorService;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.rule.Owner;
import io.harness.time.Timestamp;

import software.wings.WingsBaseTest;
import software.wings.beans.GcpConfig;
import software.wings.beans.TaskType;
import software.wings.beans.dto.NewRelicMetricDataRecord;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.stackdriver.StackDriverDataCollectionInfo;
import software.wings.service.impl.stackdriver.StackDriverNameSpace;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.stackdriver.StackDriverDelegateService;
import software.wings.verification.stackdriver.StackDriverMetricCVConfiguration;
import software.wings.verification.stackdriver.StackDriverMetricDefinition;
import software.wings.verification.stackdriver.StackDriverMetricDefinition.Aggregation;

import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.ListTimeSeriesResponse;
import com.google.api.services.monitoring.v3.model.Point;
import com.google.api.services.monitoring.v3.model.TimeInterval;
import com.google.api.services.monitoring.v3.model.TimeSeries;
import com.google.api.services.monitoring.v3.model.TypedValue;
import com.google.common.collect.TreeBasedTable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @author praveen
 */
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class StackDriverDataCollectionTaskTest extends WingsBaseTest {
  @Mock private DataCollectionExecutorService dataCollectionService;
  @Mock private StackDriverDelegateService stackDriverDelegateService;
  @Mock private DelegateLogService delegateLogService;
  @Mock private GcpHelperService gcpHelperService;
  @Mock private EncryptionService encryptionService;
  @Mock private MetricDataStoreService metricStoreService;
  @Mock private Monitoring monitoring;
  @Mock private Monitoring.Projects monitoringProjects;
  @Mock private Monitoring.Projects.TimeSeries monitoringTimeSeries;
  @Mock private Monitoring.Projects.TimeSeries.List monitoringTimeSeriesList;
  private StackDriverDataCollectionTask dataCollectionTask;
  private StackDriverDataCollectionInfo dataCollectionInfo;

  private String delegateId = UUID.randomUUID().toString();
  private String appId = UUID.randomUUID().toString();
  private String envId = UUID.randomUUID().toString();
  private String waitId = UUID.randomUUID().toString();
  private String accountId = UUID.randomUUID().toString();
  private String stateExecutionId = UUID.randomUUID().toString();

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    when(stackDriverDelegateService.createFilter(
             StackDriverNameSpace.POD_NAME, "kubernetes.io/container/memory/request_utilization", "dummyHost"))
        .thenReturn("testFilter dummyHost");
    when(stackDriverDelegateService.createFilter(
             StackDriverNameSpace.POD_NAME, "kubernetes.io/container/memory/request_utilization", "secondHost"))
        .thenReturn("testFilter secondHost");
    when(metricStoreService.saveNewRelicMetrics(anyString(), anyString(), anyString(), anyString(), any(List.class)))
        .thenReturn(true);
    when(gcpHelperService.getMonitoringService(any(), any(), anyBoolean())).thenReturn(monitoring);
    setupSDMonitoringObject();
    String infrastructureMappingId = UUID.randomUUID().toString();
    String timeDuration = "10";
    dataCollectionInfo = buildDataCollectionInfo();

    DelegateTask task = DelegateTask.builder()
                            .accountId(accountId)
                            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, appId)
                            .waitId(waitId)
                            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
                            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infrastructureMappingId)
                            .build();
    task.setUuid(delegateId);

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TaskType.STACKDRIVER_COLLECT_METRIC_DATA.name())
                            .parameters(new Object[] {dataCollectionInfo})
                            .timeout(TimeUnit.MINUTES.toMillis(Integer.parseInt(timeDuration) + 120))
                            .build();

    dataCollectionTask = new StackDriverDataCollectionTask(
        DelegateTaskPackage.builder().delegateId(delegateId).data(taskData).build(), null, null, null);
    when(encryptionService.decrypt(any(), any(), eq(false))).thenReturn(null);
    setupFields();
  }

  private void setupFields() throws Exception {
    FieldUtils.writeField(dataCollectionTask, "encryptionService", encryptionService, true);
    FieldUtils.writeField(dataCollectionTask, "stackDriverDelegateService", stackDriverDelegateService, true);
    FieldUtils.writeField(dataCollectionTask, "metricStoreService", metricStoreService, true);
    FieldUtils.writeField(dataCollectionTask, "gcpHelperService", gcpHelperService, true);
    FieldUtils.writeField(dataCollectionTask, "delegateLogService", delegateLogService, true);
  }

  private ListTimeSeriesResponse getResponse() throws Exception {
    ListTimeSeriesResponse response = new ListTimeSeriesResponse();
    TimeSeries ts = new TimeSeries();
    Point point = new Point();
    point.setValue(new TypedValue().setDoubleValue(0.016666666666666666));
    point.setInterval(new TimeInterval().setEndTime("2019-11-01T01:07:00Z").setStartTime("2019-11-01T01:07:00Z"));
    ts.setPoints(Arrays.asList(point));
    response.setTimeSeries(Arrays.asList(ts));
    return response;
  }

  private void setupSDMonitoringObject() throws Exception {
    ListTimeSeriesResponse respObject = getResponse();
    when(monitoring.projects()).thenReturn(monitoringProjects);
    when(monitoringProjects.timeSeries()).thenReturn(monitoringTimeSeries);
    when(monitoringTimeSeries.list(anyString())).thenReturn(monitoringTimeSeriesList);
    when(monitoringTimeSeriesList.setFilter(anyString())).thenReturn(monitoringTimeSeriesList);
    when(monitoringTimeSeriesList.setAggregationGroupByFields(any())).thenReturn(monitoringTimeSeriesList);
    when(monitoringTimeSeriesList.setAggregationAlignmentPeriod(anyString())).thenReturn(monitoringTimeSeriesList);
    when(monitoringTimeSeriesList.setIntervalStartTime(any())).thenReturn(monitoringTimeSeriesList);
    when(monitoringTimeSeriesList.setIntervalEndTime(any())).thenReturn(monitoringTimeSeriesList);
    when(monitoringTimeSeriesList.setAggregationPerSeriesAligner(anyString())).thenReturn(monitoringTimeSeriesList);
    when(monitoringTimeSeriesList.execute()).thenReturn(respObject);
  }

  private StackDriverDataCollectionInfo buildDataCollectionInfo() {
    Map<String, String> groupHostsMap = new HashMap<>();
    groupHostsMap.put("dummyHost", "default");
    groupHostsMap.put("secondHost", "default");

    return StackDriverDataCollectionInfo.builder()
        .collectionTime(10)
        .hosts(groupHostsMap)
        .applicationId(appId)
        .stateExecutionId(stateExecutionId)
        .initialDelayMinutes(0)
        .timeSeriesMlAnalysisType(TimeSeriesMlAnalysisType.COMPARATIVE)
        .timeSeriesToCollect(Collections.singletonList(StackDriverMetricDefinition.builder()
                                                           .metricName("MemoryRequestUtilization")
                                                           .txnName("Memory Request Utilization")
                                                           .metricType("VALUE")
                                                           .aggregation(new Aggregation())
                                                           .build()))
        .gcpConfig(GcpConfig.builder().accountId(accountId).build())
        .startTime(Timestamp.currentMinuteBoundary() - TimeUnit.MINUTES.toMillis(2))
        .build();
  }

  private StackDriverDataCollectionInfo buildServiceGuardDataCollection() throws Exception {
    StackDriverMetricCVConfiguration config = createStackDriverConfig(accountId);

    config.setMetricFilters();
    config.setUuid(generateUuid());

    GcpConfig gcpConfig = GcpConfig.builder().accountId(accountId).build();

    return StackDriverDataCollectionInfo.builder()
        .gcpConfig(gcpConfig)
        .applicationId(appId)
        .stateExecutionId(CV_24x7_STATE_EXECUTION + "-" + config.getUuid())
        .serviceId(config.getServiceId())
        .cvConfigId(config.getUuid())
        .startTime(Timestamp.currentMinuteBoundary() - TimeUnit.MINUTES.toMillis(10))
        .collectionTime(5)
        .timeSeriesToCollect(config.getMetricDefinitions())
        .hosts(new HashMap<>())
        .timeSeriesMlAnalysisType(TimeSeriesMlAnalysisType.PREDICTIVE)
        .dataCollectionMinute(0)
        .build();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetMetrics() throws Exception {
    FieldUtils.writeField(dataCollectionTask, "dataCollectionService", dataCollectionService, true);
    TreeBasedTable<String, Long, NewRelicMetricDataRecord> rv = TreeBasedTable.create();
    NewRelicMetricDataRecord record = NewRelicMetricDataRecord.builder().uuid("testdatarecord").build();
    rv.put("dummyHost", Timestamp.currentMinuteBoundary(), record);
    when(dataCollectionService.executeParrallel(any(List.class))).thenReturn(Arrays.asList(Optional.of(rv)));

    DataCollectionTaskResult taskResult = dataCollectionTask.initDataCollection(dataCollectionInfo);
    dataCollectionTask.getDataCollector(taskResult).run();
    ArgumentCaptor<List> taskCaptor = ArgumentCaptor.forClass(List.class);
    verify(dataCollectionService).executeParrallel(taskCaptor.capture());
    assertThat(taskCaptor.getValue().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetServiceGuardMetrics() throws Exception {
    // setup
    StackDriverDataCollectionInfo dcInfo = buildServiceGuardDataCollection();

    // create a task with 24/7 task type
    DelegateTask task = DelegateTask.builder()
                            .accountId(accountId)
                            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, appId)
                            .waitId(waitId)
                            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
                            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, generateUuid())
                            .build();
    task.setUuid(delegateId);

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TaskType.STACKDRIVER_COLLECT_24_7_METRIC_DATA.name())
                            .parameters(new Object[] {dataCollectionInfo})
                            .timeout(TimeUnit.MINUTES.toMillis(Integer.parseInt("30") + 120))
                            .build();

    dataCollectionTask = new StackDriverDataCollectionTask(
        DelegateTaskPackage.builder().delegateTaskId(task.getUuid()).delegateId(delegateId).data(taskData).build(),
        null, null, null);

    // setup the executor service to run the mock calls.
    DataCollectionExecutorService executorService = new DataCollectionExecutorService();
    FieldUtils.writeField(executorService, "dataCollectionService", Executors.newFixedThreadPool(2), true);
    FieldUtils.writeField(dataCollectionTask, "dataCollectionService", executorService, true);
    setupFields();

    // execute behavior under test
    DataCollectionTaskResult taskResult = dataCollectionTask.initDataCollection(dcInfo);
    dataCollectionTask.getDataCollector(taskResult).run();

    // verify the results
    ArgumentCaptor<List> taskCaptor = ArgumentCaptor.forClass(List.class);
    verify(metricStoreService)
        .saveNewRelicMetrics(
            eq(accountId), eq(appId), eq(dcInfo.getStateExecutionId()), eq(delegateId), taskCaptor.capture());
    List<NewRelicMetricDataRecord> metricDataRecords = taskCaptor.getValue();

    assertThat(metricDataRecords.size()).isEqualTo(3);
    boolean hasHeartBeat = false, hasTxn1 = false, txn1Has1Metric = false, hasTxn2 = false, has2MetricsInTxn2 = false;
    for (NewRelicMetricDataRecord record : metricDataRecords) {
      if (ClusterLevel.H0 == record.getLevel()) {
        hasHeartBeat = true;
      }
      if (record.getName().equals("TransactionName1")) {
        hasTxn1 = true;
        txn1Has1Metric = record.getValues().size() == 1 && record.getValues().containsKey("metricName");
      }
      if (record.getName().equals("TransactionName2")) {
        hasTxn2 = true;
        has2MetricsInTxn2 =
            record.getValues().containsKey("metricName2") && record.getValues().containsKey("metricName3");
      }
    }

    assertThat(hasHeartBeat).isTrue();
    assertThat(hasTxn1).isTrue();
    assertThat(txn1Has1Metric).isTrue();
    assertThat(hasTxn2).isTrue();
    assertThat(has2MetricsInTxn2).isTrue();
  }
}
