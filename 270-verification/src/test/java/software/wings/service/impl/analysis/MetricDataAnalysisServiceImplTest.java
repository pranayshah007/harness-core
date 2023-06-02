/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.Misc.replaceDotWithUnicode;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SOWMYA;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.service.TimeSeriesAnalysisServiceImpl;
import io.harness.service.intfc.LearningEngineService;
import io.harness.service.intfc.TimeSeriesAnalysisService;

import software.wings.WingsBaseTest;
import software.wings.beans.WorkflowExecution;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.MetricType;
import software.wings.metrics.Threshold;
import software.wings.metrics.ThresholdComparisonType;
import software.wings.metrics.ThresholdType;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.analysis.TimeSeriesMLTransactionThresholds.TimeSeriesMLTransactionThresholdKeys;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.sm.StateType;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

/**
 * @author Praveen
 *
 */
@OwnedBy(HarnessTeam.CV)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class MetricDataAnalysisServiceImplTest extends WingsBaseTest {
  @Mock LearningEngineService learningEngineService;
  @Inject WingsPersistence wingsPersistence;
  @Inject MetricDataAnalysisService metricDataAnalysisService;
  @Inject DataStoreService dataStoreService;

  private String appId;
  private String workflowId;
  private String serviceId;
  private String infraMappingId;
  private String envId;
  private String cvConfigId;
  private String stateExecutionId;
  private String accountId;
  private TimeSeriesAnalysisService timeSeriesAnalysisService;

  @Before
  public void setup() throws IllegalAccessException {
    accountId = generateUuid();
    appId = generateUuid();
    serviceId = generateUuid();
    workflowId = generateUuid();
    infraMappingId = generateUuid();
    envId = generateUuid();
    cvConfigId = generateUuid();
    stateExecutionId = generateUuid();
    FieldUtils.writeField(metricDataAnalysisService, "wingsPersistence", wingsPersistence, true);
    when(learningEngineService.isStateValid(anyString(), anyString())).thenReturn(true);
    timeSeriesAnalysisService = new TimeSeriesAnalysisServiceImpl();
    FieldUtils.writeField(timeSeriesAnalysisService, "dataStoreService", dataStoreService, true);
    FieldUtils.writeField(timeSeriesAnalysisService, "learningEngineService", learningEngineService, true);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulWorkflowWithData() {
    String execId = generateUuid();
    WorkflowExecution execution = WorkflowExecution.builder()
                                      .appId(appId)
                                      .workflowId(workflowId)
                                      .infraMappingIds(Arrays.asList(infraMappingId))
                                      .serviceIds(Arrays.asList(serviceId))
                                      .envId(envId)
                                      .status(SUCCESS)
                                      .uuid(execId)
                                      .build();

    wingsPersistence.save(execution);

    timeSeriesAnalysisService.saveMetricData(accountId, appId, stateExecutionId, generateUuid(),
        Lists.newArrayList(NewRelicMetricDataRecord.builder()
                               .stateType(DelegateStateType.NEW_RELIC)
                               .appId(appId)
                               .workflowId(workflowId)
                               .workflowExecutionId(execId)
                               .serviceId(serviceId)
                               .build()));

    String lastId = metricDataAnalysisService.getLastSuccessfulWorkflowExecutionIdWithData(
        DelegateStateType.NEW_RELIC, appId, workflowId, serviceId, infraMappingId, envId);

    assertThat(lastId).isNotNull();
    assertThat(lastId).isEqualTo(execId);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulWorkflowWithDataDifferentInfra() {
    String execId = generateUuid();
    String newInfra = generateUuid();
    WorkflowExecution execution = WorkflowExecution.builder()
                                      .appId(appId)
                                      .workflowId(workflowId)
                                      .infraMappingIds(Arrays.asList(newInfra))
                                      .serviceIds(Arrays.asList(serviceId))
                                      .status(SUCCESS)
                                      .envId(envId)
                                      .uuid(execId)
                                      .build();

    wingsPersistence.save(execution);

    timeSeriesAnalysisService.saveMetricData(accountId, appId, stateExecutionId, generateUuid(),
        Lists.newArrayList(NewRelicMetricDataRecord.builder()
                               .stateType(DelegateStateType.NEW_RELIC)
                               .appId(appId)
                               .workflowId(workflowId)
                               .workflowExecutionId(execId)
                               .serviceId(serviceId)
                               .build()));

    String lastId = metricDataAnalysisService.getLastSuccessfulWorkflowExecutionIdWithData(
        DelegateStateType.NEW_RELIC, appId, workflowId, serviceId, infraMappingId, envId);

    assertThat(lastId).isNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetCustomThresholds() {
    saveCustomThresholds();

    List<TimeSeriesMLTransactionThresholds> thresholds =
        metricDataAnalysisService.getCustomThreshold(TimeSeriesMLTransactionThresholdKeys.cvConfigId, cvConfigId);
    assertThat(thresholds).isNotEmpty();
    assertThat(thresholds.size()).isEqualTo(50);
  }

  private void saveCustomThresholds() {
    int numOfTransactions = 5;
    int numOfMetricsPerTxns = 10;
    for (int i = 0; i < numOfTransactions; i++) {
      for (int j = 0; j < numOfMetricsPerTxns; j++) {
        metricDataAnalysisService.saveCustomThreshold(accountId, appId, StateType.NEW_RELIC, serviceId, cvConfigId,
            "transaction-" + (i * numOfMetricsPerTxns + j), DEFAULT_GROUP_NAME,
            TimeSeriesMetricDefinition.builder()
                .metricName("metric-" + (i * numOfMetricsPerTxns + j))
                .metricType(MetricType.THROUGHPUT)
                .customThresholds(Lists.newArrayList(Threshold.builder()
                                                         .comparisonType(ThresholdComparisonType.DELTA)
                                                         .thresholdType(ThresholdType.ALERT_WHEN_HIGHER)
                                                         .ml(i * numOfMetricsPerTxns + j)
                                                         .build()))
                .build(),
            null);
      }
    }
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetLatestLocalAnalysisRecord_EmptyRecords() {
    NewRelicMetricAnalysisRecord analysisRecord =
        metricDataAnalysisService.getLatestLocalAnalysisRecord(stateExecutionId);
    assertThat(analysisRecord).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetLatestLocalAnalysisRecord_IncorrectId() {
    NewRelicMetricAnalysisRecord record = NewRelicMetricAnalysisRecord.builder()
                                              .stateExecutionId(stateExecutionId + "2")
                                              .analysisMinute(1)
                                              .message("Test message")
                                              .build();
    wingsPersistence.save(record);
    NewRelicMetricAnalysisRecord analysisRecord =
        metricDataAnalysisService.getLatestLocalAnalysisRecord(stateExecutionId);
    assertThat(analysisRecord).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetLatestLocalAnalysisRecord_ExistingRecord() {
    NewRelicMetricAnalysisRecord record = NewRelicMetricAnalysisRecord.builder()
                                              .stateExecutionId(stateExecutionId)
                                              .analysisMinute(1)
                                              .message("The state was marked")
                                              .build();
    record.setUuid(generateUuid());
    wingsPersistence.save(record);
    NewRelicMetricAnalysisRecord analysisRecord =
        metricDataAnalysisService.getLatestLocalAnalysisRecord(stateExecutionId);
    assertThat(analysisRecord).isNotNull();
    assertThat(analysisRecord.getUuid()).isEqualTo(record.getUuid());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveMetricTemplates() {
    Map<String, TimeSeriesMetricDefinition> metricTemplates = new HashMap<>();

    metricTemplates.put("metric.with.dots", TimeSeriesMetricDefinition.builder().build());
    metricTemplates.put("metricWithoutDots", TimeSeriesMetricDefinition.builder().build());
    metricDataAnalysisService.saveMetricTemplates(
        appId, StateType.PROMETHEUS, stateExecutionId, cvConfigId, metricTemplates);

    TimeSeriesMetricTemplates timeSeriesMetricTemplates =
        wingsPersistence.createQuery(TimeSeriesMetricTemplates.class, excludeAuthority).get();
    Map<String, TimeSeriesMetricDefinition> savedMetricTemplates = timeSeriesMetricTemplates.getMetricTemplates();
    assertThat(savedMetricTemplates).containsKey("metricWithoutDots");
    assertThat(savedMetricTemplates).containsKey(replaceDotWithUnicode("metric.with.dots"));
    assertThat(savedMetricTemplates.containsKey("metric.with.dots")).isFalse();
  }
}
