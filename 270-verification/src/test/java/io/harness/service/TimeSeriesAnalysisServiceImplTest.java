/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.Misc.replaceDotWithUnicode;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rest.RestResponse.Builder.aRestResponse;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SOWMYA;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.common.VerificationConstants.CV_24x7_STATE_EXECUTION;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.VerificationBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder.OrderType;
import io.harness.category.element.UnitTests;
import io.harness.entities.TimeSeriesAnomaliesRecord;
import io.harness.entities.TimeSeriesAnomaliesRecord.TimeSeriesAnomaliesRecordKeys;
import io.harness.entities.TimeSeriesCumulativeSums;
import io.harness.exception.WingsException;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.service.intfc.LearningEngineService;
import io.harness.service.intfc.TimeSeriesAnalysisService;
import io.harness.time.Timestamp;

import software.wings.delegatetasks.DelegateStateType;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.MetricType;
import software.wings.metrics.RiskLevel;
import software.wings.metrics.Threshold;
import software.wings.metrics.TimeSeriesDataRecord;
import software.wings.metrics.TimeSeriesDataRecord.TimeSeriesMetricRecordKeys;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.analysis.MetricAnalysisRecord;
import software.wings.service.impl.analysis.TimeSeriesKeyTransactions;
import software.wings.service.impl.analysis.TimeSeriesMLAnalysisRecord;
import software.wings.service.impl.analysis.TimeSeriesMLHostSummary;
import software.wings.service.impl.analysis.TimeSeriesMLScores;
import software.wings.service.impl.analysis.TimeSeriesMLTransactionThresholds;
import software.wings.service.impl.analysis.TimeSeriesMetricGroup;
import software.wings.service.impl.analysis.TimeSeriesMetricGroup.TimeSeriesMlAnalysisGroupInfo;
import software.wings.service.impl.analysis.TimeSeriesMetricTemplates;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.analysis.Version;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord.NewRelicMetricDataRecordKeys;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeBasedTable;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.util.Lists;
import org.intellij.lang.annotations.Language;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
@OwnedBy(HarnessTeam.CV)
public class TimeSeriesAnalysisServiceImplTest extends VerificationBase {
  private String cvConfigId;
  private String serviceId;
  private String accountId;
  private String appId;
  private String stateExecutionId;
  private String workflowExecutionId;
  private Random randomizer;
  private String hostname;
  private int currentEpochMinute;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private DataStoreService dataStoreService;
  @Inject private TimeSeriesAnalysisService timeSeriesAnalysisService;
  @Mock private VerificationManagerClientHelper managerClientHelper;

  @Before
  public void setup() throws IllegalAccessException {
    long seed = System.currentTimeMillis();
    log.info("seed: {}", seed);
    randomizer = new Random(seed);
    cvConfigId = generateUuid();
    serviceId = generateUuid();
    accountId = generateUuid();
    appId = generateUuid();
    stateExecutionId = generateUuid();
    workflowExecutionId = generateUuid();
    timeSeriesAnalysisService = spy(timeSeriesAnalysisService);
    currentEpochMinute = (int) TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis());
    hostname = generateUuid();
    MockitoAnnotations.initMocks(this);
    when(managerClientHelper.callManagerWithRetry(any())).thenReturn(aRestResponse().withResource(false).build());

    FieldUtils.writeField(timeSeriesAnalysisService, "managerClientHelper", managerClientHelper, true);
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveMetricDataIfMetricDataIsEmpty() {
    assertThat(
        timeSeriesAnalysisService.saveMetricData(accountId, appId, stateExecutionId, generateUuid(), Lists.emptyList()))
        .isFalse();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveMetricDataIfStateExecutionIdIsInvalid() throws IllegalAccessException {
    NewRelicMetricDataRecord newRelicMetricDataRecord = NewRelicMetricDataRecord.builder().build();
    LearningEngineService learningEngineService = mock(LearningEngineService.class);
    FieldUtils.writeField(timeSeriesAnalysisService, "learningEngineService", learningEngineService, true);
    when(learningEngineService.isStateValid(anyString(), anyString())).thenReturn(false);
    assertThat(timeSeriesAnalysisService.saveMetricData(
                   accountId, appId, stateExecutionId, generateUuid(), Lists.newArrayList(newRelicMetricDataRecord)))
        .isFalse();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveMetricDataForAddingValidUntil() throws IllegalAccessException {
    NewRelicMetricDataRecord newRelicMetricDataRecord =
        NewRelicMetricDataRecord.builder().cvConfigId(generateUuid()).build();
    LearningEngineService learningEngineService = mock(LearningEngineService.class);
    FieldUtils.writeField(timeSeriesAnalysisService, "learningEngineService", learningEngineService, true);
    when(learningEngineService.isStateValid(anyString(), anyString())).thenReturn(true);

    assertThat(timeSeriesAnalysisService.saveMetricData(
                   accountId, appId, stateExecutionId, generateUuid(), Lists.newArrayList(newRelicMetricDataRecord)))
        .isTrue();
    assertThat(newRelicMetricDataRecord.getValidUntil())
        .isBefore(Date.from(OffsetDateTime.now().plusMonths(2).toInstant()));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveMetricData_WhenAlreadyProcessed() throws IllegalAccessException {
    String stateExecutionId = generateUuid();
    String groupName = generateUuid();
    int dataCollectionMinute = 5;
    List<NewRelicMetricDataRecord> metricDataRecords =
        Lists.newArrayList(NewRelicMetricDataRecord.builder()
                               .stateExecutionId(stateExecutionId)
                               .groupName(groupName)
                               .dataCollectionMinute(dataCollectionMinute)
                               .appId(appId)
                               .build(),
            NewRelicMetricDataRecord.builder()
                .stateExecutionId(stateExecutionId)
                .groupName(groupName)
                .dataCollectionMinute(dataCollectionMinute)
                .appId(appId)
                .level(ClusterLevel.H0)
                .build());
    LearningEngineService learningEngineService = mock(LearningEngineService.class);
    FieldUtils.writeField(timeSeriesAnalysisService, "learningEngineService", learningEngineService, true);
    when(learningEngineService.isStateValid(anyString(), anyString())).thenReturn(true);
    PageRequest<TimeSeriesDataRecord> pageRequest = aPageRequest().build();
    PageResponse<TimeSeriesDataRecord> timeSeriesDataRecords =
        dataStoreService.list(TimeSeriesDataRecord.class, pageRequest);

    assertThat(timeSeriesDataRecords).isEmpty();
    dataStoreService.save(TimeSeriesDataRecord.class,
        Lists.newArrayList(TimeSeriesDataRecord.builder()
                               .stateExecutionId(stateExecutionId)
                               .groupName(groupName)
                               .level(ClusterLevel.HF)
                               .dataCollectionMinute(dataCollectionMinute)
                               .build()),
        false);

    assertThat(dataStoreService.list(TimeSeriesDataRecord.class, pageRequest).size()).isEqualTo(1);
    assertThat(
        timeSeriesAnalysisService.saveMetricData(accountId, appId, stateExecutionId, generateUuid(), metricDataRecords))
        .isTrue();

    assertThat(dataStoreService.list(TimeSeriesDataRecord.class, pageRequest).size()).isEqualTo(1);
    metricDataRecords = Lists.newArrayList(NewRelicMetricDataRecord.builder()
                                               .stateExecutionId(stateExecutionId)
                                               .groupName(groupName)
                                               .dataCollectionMinute(dataCollectionMinute + 1)
                                               .appId(appId)
                                               .build(),
        NewRelicMetricDataRecord.builder()
            .stateExecutionId(stateExecutionId)
            .groupName(groupName)
            .dataCollectionMinute(dataCollectionMinute + 1)
            .appId(appId)
            .level(ClusterLevel.H0)
            .build());

    assertThat(
        timeSeriesAnalysisService.saveMetricData(accountId, appId, stateExecutionId, generateUuid(), metricDataRecords))
        .isTrue();
    assertThat(dataStoreService.list(TimeSeriesDataRecord.class, pageRequest).size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveMetricDataForWorkflowAsNewRelicDataRecord() throws IllegalAccessException {
    NewRelicMetricDataRecord newRelicMetricDataRecord =
        NewRelicMetricDataRecord.builder().stateExecutionId(generateUuid()).cvConfigId(generateUuid()).build();
    LearningEngineService learningEngineService = mock(LearningEngineService.class);
    FieldUtils.writeField(timeSeriesAnalysisService, "learningEngineService", learningEngineService, true);
    when(learningEngineService.isStateValid(anyString(), anyString())).thenReturn(true);

    assertThat(
        timeSeriesAnalysisService.saveMetricData(accountId, appId, newRelicMetricDataRecord.getStateExecutionId(),
            generateUuid(), Lists.newArrayList(newRelicMetricDataRecord)))
        .isTrue();
    TimeSeriesDataRecord timeSeriesDataRecord =
        wingsPersistence.createQuery(TimeSeriesDataRecord.class)
            .filter(TimeSeriesMetricRecordKeys.stateExecutionId, newRelicMetricDataRecord.getStateExecutionId())
            .get();
    assertThat(timeSeriesDataRecord).isNotNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveMetricDataForServiceGuard() throws IllegalAccessException {
    NewRelicMetricDataRecord newRelicMetricDataRecord =
        NewRelicMetricDataRecord.builder()
            .stateExecutionId(CV_24x7_STATE_EXECUTION + "-" + generateUuid())
            .cvConfigId(generateUuid())
            .build();
    LearningEngineService learningEngineService = mock(LearningEngineService.class);
    FieldUtils.writeField(timeSeriesAnalysisService, "learningEngineService", learningEngineService, true);
    when(learningEngineService.isStateValid(anyString(), anyString())).thenReturn(true);

    assertThat(
        timeSeriesAnalysisService.saveMetricData(accountId, appId, newRelicMetricDataRecord.getStateExecutionId(),
            generateUuid(), Lists.newArrayList(newRelicMetricDataRecord)))
        .isTrue();
    NewRelicMetricDataRecord savedRecord =
        wingsPersistence.createQuery(NewRelicMetricDataRecord.class)
            .filter("stateExecutionId", newRelicMetricDataRecord.getStateExecutionId())
            .get();
    assertThat(savedRecord).isNull();
    TimeSeriesDataRecord timeSeriesDataRecord =
        wingsPersistence.createQuery(TimeSeriesDataRecord.class)
            .filter(TimeSeriesMetricRecordKeys.stateExecutionId, newRelicMetricDataRecord.getStateExecutionId())
            .get();
    assertThat(timeSeriesDataRecord).isNotNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetTimeSeriesMLScores() {
    String workflowId = generateUuid();
    TimeSeriesMLScores timeSeriesMLScores = TimeSeriesMLScores.builder()
                                                .analysisMinute(currentEpochMinute)
                                                .workflowExecutionId(workflowExecutionId)
                                                .appId(appId)
                                                .stateExecutionId(stateExecutionId)
                                                .stateType(StateType.NEW_RELIC)
                                                .workflowId(workflowId)
                                                .build();
    wingsPersistence.save(timeSeriesMLScores);
    List<String> workflowIds = Lists.newArrayList(workflowExecutionId);
    doReturn(workflowIds)
        .when(timeSeriesAnalysisService)
        .getLastSuccessfulWorkflowExecutionIds(anyString(), anyString(), any());
    List<TimeSeriesMLScores> returnedResults =
        timeSeriesAnalysisService.getTimeSeriesMLScores(appId, workflowId, currentEpochMinute, 1);
    assertThat(returnedResults).hasSize(1);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetRecords_IfNoRecordsAvailable() {
    when(managerClientHelper.callManagerWithRetry(any())).thenReturn(aRestResponse().withResource(true).build());
    Set<NewRelicMetricDataRecord> records = timeSeriesAnalysisService.getRecords(
        appId, stateExecutionId, DEFAULT_GROUP_NAME, Sets.newHashSet(hostname), 10, 10, accountId);
    assertThat(records).isEmpty();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetRecords_WhenRecordsAreAvailable() {
    when(managerClientHelper.callManagerWithRetry(any())).thenReturn(aRestResponse().withResource(true).build());
    NewRelicMetricDataRecord expectedRecord = NewRelicMetricDataRecord.builder()
                                                  .groupName(DEFAULT_GROUP_NAME)
                                                  .name("key")
                                                  .stateExecutionId(stateExecutionId)
                                                  .stateType(DelegateStateType.NEW_RELIC)
                                                  .dataCollectionMinute(currentEpochMinute)
                                                  .host(hostname)
                                                  .values(new HashMap<>())
                                                  .build();
    expectedRecord.getValues().put("name", 1.0);
    TimeSeriesDataRecord timeSeriesDataRecord = TimeSeriesDataRecord.builder()
                                                    .groupName(DEFAULT_GROUP_NAME)
                                                    .stateExecutionId(stateExecutionId)
                                                    .stateType(DelegateStateType.NEW_RELIC)
                                                    .dataCollectionMinute(currentEpochMinute)
                                                    .host(hostname)
                                                    .values(HashBasedTable.create())
                                                    .build();
    timeSeriesDataRecord.getValues().put("key", "name", 1.0);
    timeSeriesDataRecord.compress();
    wingsPersistence.save(timeSeriesDataRecord);
    Set<NewRelicMetricDataRecord> records = timeSeriesAnalysisService.getRecords(appId, stateExecutionId,
        DEFAULT_GROUP_NAME, Sets.newHashSet(hostname), currentEpochMinute, currentEpochMinute, accountId);
    assertThat(records).hasSize(1);
    assertThat(records.iterator().next()).isEqualTo(expectedRecord);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetRecords_ExcludeHeartbeatRecord() {
    when(managerClientHelper.callManagerWithRetry(any())).thenReturn(aRestResponse().withResource(true).build());
    TimeSeriesDataRecord timeSeriesDataRecord = TimeSeriesDataRecord.builder()
                                                    .groupName(DEFAULT_GROUP_NAME)
                                                    .stateExecutionId(stateExecutionId)
                                                    .stateType(DelegateStateType.NEW_RELIC)
                                                    .dataCollectionMinute(currentEpochMinute)
                                                    .host(hostname)
                                                    .level(ClusterLevel.L1)
                                                    .build();
    wingsPersistence.save(timeSeriesDataRecord);
    wingsPersistence.save(TimeSeriesDataRecord.builder()
                              .groupName(DEFAULT_GROUP_NAME)
                              .stateExecutionId(stateExecutionId)
                              .stateType(DelegateStateType.NEW_RELIC)
                              .dataCollectionMinute(currentEpochMinute)
                              .host(hostname)
                              .level(ClusterLevel.H0)
                              .build());
    wingsPersistence.save(NewRelicMetricDataRecord.builder()
                              .groupName(DEFAULT_GROUP_NAME)
                              .stateExecutionId(stateExecutionId)
                              .appId(appId)
                              .stateType(DelegateStateType.NEW_RELIC)
                              .dataCollectionMinute(currentEpochMinute)
                              .host(hostname)
                              .level(ClusterLevel.HF)
                              .build());
    Set<NewRelicMetricDataRecord> records = timeSeriesAnalysisService.getRecords(appId, stateExecutionId,
        DEFAULT_GROUP_NAME, Sets.newHashSet(hostname), currentEpochMinute, currentEpochMinute, accountId);
    assertThat(records).hasSize(1);
    assertThat(records.iterator().next().getLevel()).isEqualTo(ClusterLevel.L1);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetPreviousSuccessfulRecordss_ExcludeHeartbeatRecord() {
    when(managerClientHelper.callManagerWithRetry(any())).thenReturn(aRestResponse().withResource(true).build());
    TimeSeriesDataRecord timeSeriesDataRecord = TimeSeriesDataRecord.builder()
                                                    .groupName(DEFAULT_GROUP_NAME)
                                                    .stateExecutionId(stateExecutionId)
                                                    .workflowExecutionId(workflowExecutionId)
                                                    .stateType(DelegateStateType.NEW_RELIC)
                                                    .dataCollectionMinute(currentEpochMinute)
                                                    .host(hostname)
                                                    .level(ClusterLevel.L1)
                                                    .build();
    wingsPersistence.save(timeSeriesDataRecord);
    wingsPersistence.save(TimeSeriesDataRecord.builder()
                              .groupName(DEFAULT_GROUP_NAME)
                              .stateExecutionId(stateExecutionId)
                              .workflowExecutionId(workflowExecutionId)
                              .stateType(DelegateStateType.NEW_RELIC)
                              .dataCollectionMinute(currentEpochMinute)
                              .host(hostname)
                              .level(ClusterLevel.H0)
                              .build());
    wingsPersistence.save(TimeSeriesDataRecord.builder()
                              .groupName(DEFAULT_GROUP_NAME)
                              .stateExecutionId(stateExecutionId)
                              .workflowExecutionId(workflowExecutionId)
                              .stateType(DelegateStateType.NEW_RELIC)
                              .dataCollectionMinute(currentEpochMinute)
                              .host(hostname)
                              .level(ClusterLevel.HF)
                              .build());
    Set<NewRelicMetricDataRecord> records = timeSeriesAnalysisService.getPreviousSuccessfulRecords(
        appId, workflowExecutionId, DEFAULT_GROUP_NAME, currentEpochMinute, currentEpochMinute, accountId);
    assertThat(records).hasSize(1);
    assertThat(records.iterator().next().getLevel()).isEqualTo(ClusterLevel.L1);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetPreviousSuccessfulRecords_IfWorkflowExecutionIdIsEmpty() {
    when(managerClientHelper.callManagerWithRetry(any())).thenReturn(aRestResponse().withResource(true).build());
    TimeSeriesDataRecord timeSeriesDataRecord = TimeSeriesDataRecord.builder()
                                                    .groupName(DEFAULT_GROUP_NAME)
                                                    .workflowExecutionId(workflowExecutionId)
                                                    .stateType(DelegateStateType.NEW_RELIC)
                                                    .dataCollectionMinute(currentEpochMinute)
                                                    .host(hostname)
                                                    .level(ClusterLevel.L1)
                                                    .build();
    wingsPersistence.save(timeSeriesDataRecord);
    Set<NewRelicMetricDataRecord> records = timeSeriesAnalysisService.getPreviousSuccessfulRecords(
        appId, null, DEFAULT_GROUP_NAME, currentEpochMinute, 10, accountId);
    assertThat(records).isEmpty();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetPreviousSuccessfulRecords_BaselinePinnedPreviously() {
    when(managerClientHelper.callManagerWithRetry(any())).thenReturn(aRestResponse().withResource(true).build());
    NewRelicMetricDataRecord newRelicMetricDataRecord = NewRelicMetricDataRecord.builder()
                                                            .groupName(DEFAULT_GROUP_NAME)
                                                            .workflowExecutionId(workflowExecutionId)
                                                            .stateType(DelegateStateType.NEW_RELIC)
                                                            .dataCollectionMinute(currentEpochMinute)
                                                            .workflowExecutionId(workflowExecutionId)
                                                            .host(hostname)
                                                            .level(ClusterLevel.L1)
                                                            .build();
    wingsPersistence.save(newRelicMetricDataRecord);
    Set<NewRelicMetricDataRecord> records = timeSeriesAnalysisService.getPreviousSuccessfulRecords(
        appId, workflowExecutionId, DEFAULT_GROUP_NAME, currentEpochMinute, 10, accountId);
    assertThat(records).hasSize(1);
    assertThat(records.iterator().next().getLevel()).isEqualTo(ClusterLevel.L1);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMaxControlMinute_WithData() {
    when(managerClientHelper.callManagerWithRetry(any())).thenReturn(aRestResponse().withResource(true).build());
    String workflowId = generateUuid();
    wingsPersistence.save(TimeSeriesDataRecord.builder()
                              .groupName(DEFAULT_GROUP_NAME)
                              .workflowId(workflowId)
                              .stateExecutionId(stateExecutionId)
                              .workflowExecutionId(workflowExecutionId)
                              .serviceId(serviceId)
                              .stateType(DelegateStateType.NEW_RELIC)
                              .dataCollectionMinute(currentEpochMinute)
                              .host(hostname)
                              .level(ClusterLevel.L1)
                              .build());
    wingsPersistence.save(TimeSeriesDataRecord.builder()
                              .groupName(DEFAULT_GROUP_NAME)
                              .workflowId(workflowId)
                              .stateExecutionId(stateExecutionId)
                              .workflowExecutionId(workflowExecutionId)
                              .serviceId(serviceId)
                              .stateType(DelegateStateType.NEW_RELIC)
                              .dataCollectionMinute(currentEpochMinute - 2)
                              .host(hostname)
                              .level(ClusterLevel.L1)
                              .build());
    int maxControlMinuteWithData = timeSeriesAnalysisService.getMaxControlMinuteWithData(
        StateType.NEW_RELIC, appId, serviceId, workflowId, workflowExecutionId, DEFAULT_GROUP_NAME, accountId);
    assertThat(maxControlMinuteWithData).isEqualTo(currentEpochMinute);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMaxControlMinute_WithNoData() {
    when(managerClientHelper.callManagerWithRetry(any())).thenReturn(aRestResponse().withResource(true).build());
    String workflowId = generateUuid();
    int maxControlMinuteWithData = timeSeriesAnalysisService.getMaxControlMinuteWithData(
        StateType.NEW_RELIC, appId, serviceId, workflowId, workflowExecutionId, DEFAULT_GROUP_NAME, accountId);
    assertThat(maxControlMinuteWithData).isEqualTo(-1);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMinControlMinute_WithData() {
    when(managerClientHelper.callManagerWithRetry(any())).thenReturn(aRestResponse().withResource(true).build());
    String workflowId = generateUuid();
    wingsPersistence.save(TimeSeriesDataRecord.builder()
                              .groupName(DEFAULT_GROUP_NAME)
                              .workflowId(workflowId)
                              .stateExecutionId(stateExecutionId)
                              .workflowExecutionId(workflowExecutionId)
                              .serviceId(serviceId)
                              .stateType(DelegateStateType.NEW_RELIC)
                              .dataCollectionMinute(currentEpochMinute)
                              .host(hostname)
                              .level(ClusterLevel.L1)
                              .build());
    wingsPersistence.save(TimeSeriesDataRecord.builder()
                              .groupName(DEFAULT_GROUP_NAME)
                              .workflowId(workflowId)
                              .stateExecutionId(stateExecutionId)
                              .workflowExecutionId(workflowExecutionId)
                              .serviceId(serviceId)
                              .stateType(DelegateStateType.NEW_RELIC)
                              .dataCollectionMinute(currentEpochMinute - 2)
                              .host(hostname)
                              .level(ClusterLevel.L1)
                              .build());
    int maxControlMinuteWithData = timeSeriesAnalysisService.getMinControlMinuteWithData(
        StateType.NEW_RELIC, appId, serviceId, workflowId, workflowExecutionId, DEFAULT_GROUP_NAME, accountId);
    assertThat(maxControlMinuteWithData).isEqualTo(currentEpochMinute - 2);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMinControlMinute_WithNoData() {
    when(managerClientHelper.callManagerWithRetry(any())).thenReturn(aRestResponse().withResource(true).build());
    String workflowId = generateUuid();
    int maxControlMinuteWithData = timeSeriesAnalysisService.getMinControlMinuteWithData(
        StateType.NEW_RELIC, appId, serviceId, workflowId, workflowExecutionId, DEFAULT_GROUP_NAME, accountId);
    assertThat(maxControlMinuteWithData).isEqualTo(-1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRecordsIfNoRecordsAvailable() {
    Set<NewRelicMetricDataRecord> records = timeSeriesAnalysisService.getRecords(
        appId, stateExecutionId, "DEFAULT", Sets.newHashSet("test-host"), 10, 10, accountId);
    assertThat(records).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRecordsWhenRecordsAreAvailable() {
    NewRelicMetricDataRecord newRelicMetricDataRecord = NewRelicMetricDataRecord.builder()
                                                            .groupName("DEFAULT")
                                                            .stateExecutionId(stateExecutionId)
                                                            .appId(appId)
                                                            .name("metric-name")
                                                            .stateType(DelegateStateType.NEW_RELIC)
                                                            .dataCollectionMinute(currentEpochMinute)
                                                            .host("test-host")
                                                            .level(ClusterLevel.L1)
                                                            .build();
    wingsPersistence.save(TimeSeriesDataRecord.getTimeSeriesDataRecordsFromNewRelicDataRecords(
        Collections.singletonList(newRelicMetricDataRecord)));
    Set<NewRelicMetricDataRecord> records = timeSeriesAnalysisService.getRecords(appId, stateExecutionId, "DEFAULT",
        Sets.newHashSet("test-host"), currentEpochMinute, currentEpochMinute, accountId);
    assertThat(records).hasSize(1);
    assertThat(records.iterator().next().getHost()).isEqualTo("test-host");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRecordsAndExcludeHeartbeatRecord() {
    List<NewRelicMetricDataRecord> recordsToSave = new ArrayList<>();
    recordsToSave.add(NewRelicMetricDataRecord.builder()
                          .groupName("DEFAULT")
                          .stateExecutionId(stateExecutionId)
                          .appId(appId)
                          .stateType(DelegateStateType.NEW_RELIC)
                          .dataCollectionMinute(currentEpochMinute)
                          .host("test-host")
                          .level(ClusterLevel.L1)
                          .build());
    recordsToSave.add(NewRelicMetricDataRecord.builder()
                          .groupName("DEFAULT")
                          .stateExecutionId(stateExecutionId)
                          .appId(appId)
                          .stateType(DelegateStateType.NEW_RELIC)
                          .dataCollectionMinute(currentEpochMinute)
                          .host("test-host")
                          .level(ClusterLevel.H0)
                          .build());
    recordsToSave.add(NewRelicMetricDataRecord.builder()
                          .groupName("DEFAULT")
                          .stateExecutionId(stateExecutionId)
                          .appId(appId)
                          .stateType(DelegateStateType.NEW_RELIC)
                          .dataCollectionMinute(currentEpochMinute)
                          .host("test-host")
                          .level(ClusterLevel.HF)
                          .build());
    wingsPersistence.save(TimeSeriesDataRecord.getTimeSeriesDataRecordsFromNewRelicDataRecords(recordsToSave));
    Set<NewRelicMetricDataRecord> records = timeSeriesAnalysisService.getRecords(appId, stateExecutionId, "DEFAULT",
        Sets.newHashSet("test-host"), currentEpochMinute, currentEpochMinute, accountId);
    assertThat(records).hasSize(1);
    assertThat(records.iterator().next().getLevel()).isEqualTo(ClusterLevel.L1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetPreviousRecordsAndExcludeHeartbeatRecord() {
    List<NewRelicMetricDataRecord> recordsToSave = Lists.newArrayList(NewRelicMetricDataRecord.builder()
                                                                          .groupName("DEFAULT")
                                                                          .workflowExecutionId(workflowExecutionId)
                                                                          .appId(appId)
                                                                          .stateType(DelegateStateType.NEW_RELIC)
                                                                          .dataCollectionMinute(currentEpochMinute)
                                                                          .host("test-host")
                                                                          .level(ClusterLevel.L1)
                                                                          .build(),
        NewRelicMetricDataRecord.builder()
            .groupName("DEFAULT")
            .workflowExecutionId(workflowExecutionId)
            .appId(appId)
            .stateType(DelegateStateType.NEW_RELIC)
            .dataCollectionMinute(currentEpochMinute)
            .host("test-host")
            .level(ClusterLevel.H0)
            .build(),
        NewRelicMetricDataRecord.builder()
            .groupName("DEFAULT")
            .workflowExecutionId(workflowExecutionId)
            .appId(appId)
            .stateType(DelegateStateType.NEW_RELIC)
            .dataCollectionMinute(currentEpochMinute)
            .host("test-host")
            .level(ClusterLevel.HF)
            .build());

    wingsPersistence.save(TimeSeriesDataRecord.getTimeSeriesDataRecordsFromNewRelicDataRecords(recordsToSave));
    Set<NewRelicMetricDataRecord> records = timeSeriesAnalysisService.getPreviousSuccessfulRecords(
        appId, workflowExecutionId, "DEFAULT", currentEpochMinute, currentEpochMinute, accountId);
    assertThat(records).hasSize(1);
    assertThat(records.iterator().next().getLevel()).isEqualTo(ClusterLevel.L1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetPreviousRecordsIfWorkflowExecutionIdIsEmpty() {
    NewRelicMetricDataRecord newRelicMetricDataRecord = NewRelicMetricDataRecord.builder()
                                                            .groupName("DEFAULT")
                                                            .workflowExecutionId(workflowExecutionId)
                                                            .appId(appId)
                                                            .stateType(DelegateStateType.NEW_RELIC)
                                                            .dataCollectionMinute(currentEpochMinute)
                                                            .host("test-host")
                                                            .level(ClusterLevel.L1)
                                                            .build();
    wingsPersistence.save(newRelicMetricDataRecord);
    Set<NewRelicMetricDataRecord> records = timeSeriesAnalysisService.getPreviousSuccessfulRecords(
        appId, null, "DEFAULT", currentEpochMinute, 10, accountId);
    assertThat(records).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMaxControlMinuteWithData() {
    String workflowId = generateUuid();
    List<NewRelicMetricDataRecord> recordsToSave = Lists.newArrayList(NewRelicMetricDataRecord.builder()
                                                                          .groupName("DEFAULT")
                                                                          .workflowId(workflowId)
                                                                          .stateExecutionId(stateExecutionId)
                                                                          .workflowExecutionId(workflowExecutionId)
                                                                          .appId(appId)
                                                                          .serviceId(serviceId)
                                                                          .stateType(DelegateStateType.NEW_RELIC)
                                                                          .dataCollectionMinute(currentEpochMinute)
                                                                          .host("test-host")
                                                                          .level(ClusterLevel.L1)
                                                                          .build(),
        NewRelicMetricDataRecord.builder()
            .groupName("DEFAULT")
            .workflowId(workflowId)
            .stateExecutionId(stateExecutionId)
            .workflowExecutionId(workflowExecutionId)
            .appId(appId)
            .serviceId(serviceId)
            .stateType(DelegateStateType.NEW_RELIC)
            .dataCollectionMinute(currentEpochMinute - 2)
            .host("test-host")
            .level(ClusterLevel.L1)
            .build());

    wingsPersistence.save(TimeSeriesDataRecord.getTimeSeriesDataRecordsFromNewRelicDataRecords(recordsToSave));
    int maxControlMinuteWithData = timeSeriesAnalysisService.getMaxControlMinuteWithData(
        StateType.NEW_RELIC, appId, serviceId, workflowId, workflowExecutionId, "DEFAULT", accountId);
    assertThat(maxControlMinuteWithData).isEqualTo(currentEpochMinute);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMaxControlMinuteWithNoData() {
    String workflowId = generateUuid();
    int maxControlMinuteWithData = timeSeriesAnalysisService.getMaxControlMinuteWithData(
        StateType.NEW_RELIC, appId, serviceId, workflowId, workflowExecutionId, "DEFAULT", accountId);
    assertThat(maxControlMinuteWithData).isEqualTo(-1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMinControlMinuteWithData() {
    String workflowId = generateUuid();
    List<NewRelicMetricDataRecord> recordsToSave = Lists.newArrayList(NewRelicMetricDataRecord.builder()
                                                                          .groupName("DEFAULT")
                                                                          .workflowId(workflowId)
                                                                          .stateExecutionId(stateExecutionId)
                                                                          .workflowExecutionId(workflowExecutionId)
                                                                          .appId(appId)
                                                                          .serviceId(serviceId)
                                                                          .stateType(DelegateStateType.NEW_RELIC)
                                                                          .dataCollectionMinute(currentEpochMinute)
                                                                          .host("test-host")
                                                                          .level(ClusterLevel.L1)
                                                                          .build(),
        NewRelicMetricDataRecord.builder()
            .groupName("DEFAULT")
            .workflowId(workflowId)
            .stateExecutionId(stateExecutionId)
            .workflowExecutionId(workflowExecutionId)
            .appId(appId)
            .serviceId(serviceId)
            .stateType(DelegateStateType.NEW_RELIC)
            .dataCollectionMinute(currentEpochMinute - 2)
            .host("test-host")
            .level(ClusterLevel.L1)
            .build());
    wingsPersistence.save(TimeSeriesDataRecord.getTimeSeriesDataRecordsFromNewRelicDataRecords(recordsToSave));
    int maxControlMinuteWithData = timeSeriesAnalysisService.getMinControlMinuteWithData(
        StateType.NEW_RELIC, appId, serviceId, workflowId, workflowExecutionId, "DEFAULT", accountId);
    assertThat(maxControlMinuteWithData).isEqualTo(currentEpochMinute - 2);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMinControlMinuteWithNoData() {
    String workflowId = generateUuid();
    int maxControlMinuteWithData = timeSeriesAnalysisService.getMinControlMinuteWithData(
        StateType.NEW_RELIC, appId, serviceId, workflowId, workflowExecutionId, "DEFAULT", accountId);
    assertThat(maxControlMinuteWithData).isEqualTo(-1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetPreviousAnalysis() {
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord = TimeSeriesMLAnalysisRecord.builder().build();
    timeSeriesMLAnalysisRecord.setAppId(appId);
    timeSeriesMLAnalysisRecord.setTag("tag");
    timeSeriesMLAnalysisRecord.setCvConfigId(cvConfigId);
    timeSeriesMLAnalysisRecord.setAnalysisMinute(currentEpochMinute);
    wingsPersistence.save(timeSeriesMLAnalysisRecord);
    TimeSeriesMLAnalysisRecord result =
        timeSeriesAnalysisService.getPreviousAnalysis(appId, cvConfigId, currentEpochMinute, "tag");
    assertThat(result).isNotNull();
  }
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetCVMetricRecords() {
    int numOfHosts = 5;
    int numOfTxns = 40;
    int numOfMinutes = 200;

    List<String> hosts = new ArrayList<>();
    for (int i = 0; i < numOfHosts; i++) {
      hosts.add("host-" + i);
    }

    List<String> txns = new ArrayList<>();
    for (int i = 0; i < numOfTxns; i++) {
      txns.add("txn-" + i);
    }

    List<NewRelicMetricDataRecord> metricDataRecords = new ArrayList<>();
    Map<String, Double> values = new HashMap<>();
    values.put("m1", 1.0);
    hosts.forEach(host -> txns.forEach(txn -> {
      for (int k = 0; k < numOfMinutes; k++) {
        metricDataRecords.add(NewRelicMetricDataRecord.builder()
                                  .cvConfigId(cvConfigId)
                                  .serviceId(serviceId)
                                  .stateType(DelegateStateType.NEW_RELIC)
                                  .name(txn)
                                  .timeStamp(k * 1000)
                                  .dataCollectionMinute(k)
                                  .host(host)
                                  .values(values)
                                  .build());
      }
    }));
    final List<TimeSeriesDataRecord> dataRecords =
        TimeSeriesDataRecord.getTimeSeriesDataRecordsFromNewRelicDataRecords(metricDataRecords);
    dataRecords.forEach(dataRecord -> dataRecord.compress());
    wingsPersistence.save(dataRecords);

    assertThat(wingsPersistence.createQuery(TimeSeriesDataRecord.class, excludeAuthority).asList().size())
        .isEqualTo(numOfHosts * numOfMinutes);

    int analysisStartMinute = randomizer.nextInt(100);
    int analysisEndMinute = analysisStartMinute + randomizer.nextInt(102);
    log.info("start {} end {}", analysisStartMinute, analysisEndMinute);
    final Set<NewRelicMetricDataRecord> metricRecords =
        timeSeriesAnalysisService.getMetricRecords(cvConfigId, analysisStartMinute, analysisEndMinute, null, accountId);
    int numOfMinutesAsked = analysisEndMinute - analysisStartMinute + 1;
    assertThat(metricRecords.size()).isEqualTo(numOfMinutesAsked * numOfTxns * numOfHosts);

    metricRecords.forEach(metricRecord -> metricRecord.setUuid(null));
    Set<NewRelicMetricDataRecord> expectedRecords = new HashSet<>();
    hosts.forEach(host -> txns.forEach(txn -> {
      for (int k = analysisStartMinute; k <= analysisEndMinute; k++) {
        expectedRecords.add(NewRelicMetricDataRecord.builder()
                                .cvConfigId(cvConfigId)
                                .serviceId(serviceId)
                                .stateType(DelegateStateType.NEW_RELIC)
                                .name(txn)
                                .timeStamp(k * 1000)
                                .dataCollectionMinute(k)
                                .host(host)
                                .values(values)
                                .build());
      }
    }));
    assertThat(metricRecords).isEqualTo(expectedRecords);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCompressTimeSeriesMetricRecords() {
    int numOfTxns = 5;
    int numOfMetrics = 40;

    HashBasedTable<String, String, Double> values = HashBasedTable.create();
    HashBasedTable<String, String, String> deeplinkMetadata = HashBasedTable.create();

    List<String> txns = new ArrayList<>();
    for (int i = 0; i < numOfTxns; i++) {
      for (int j = 0; j < numOfMetrics; j++) {
        values.put("txn-" + i, "metric-" + j, randomizer.nextDouble());
        deeplinkMetadata.put("txn-" + i, "metric-" + j, generateUuid());
      }
    }

    final TimeSeriesDataRecord timeSeriesDataRecord = TimeSeriesDataRecord.builder()
                                                          .cvConfigId(cvConfigId)
                                                          .serviceId(serviceId)
                                                          .stateType(DelegateStateType.NEW_RELIC)
                                                          .timeStamp(1000)
                                                          .dataCollectionMinute(100)
                                                          .host(generateUuid())
                                                          .values(values)
                                                          .deeplinkMetadata(deeplinkMetadata)
                                                          .build();

    timeSeriesDataRecord.compress();

    final String recordId = wingsPersistence.save(timeSeriesDataRecord);

    TimeSeriesDataRecord savedRecord = wingsPersistence.get(TimeSeriesDataRecord.class, recordId);

    assertThat(savedRecord.getValues()).isEqualTo(TreeBasedTable.create());
    assertThat(savedRecord.getDeeplinkMetadata()).isEqualTo(TreeBasedTable.create());
    assertThat(savedRecord.getValuesBytes()).isNotNull();

    savedRecord.decompress();

    assertThat(savedRecord.getValues()).isNotNull();
    assertThat(savedRecord.getDeeplinkMetadata()).isNotNull();
    assertThat(savedRecord.getValuesBytes()).isNull();

    assertThat(savedRecord.getValues()).isEqualTo(values);
    assertThat(savedRecord.getDeeplinkMetadata()).isEqualTo(deeplinkMetadata);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetHeartBeat() {
    List<NewRelicMetricDataRecord> recordsToSave = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      recordsToSave.add(NewRelicMetricDataRecord.builder()
                            .stateType(DelegateStateType.NEW_RELIC)
                            .appId(appId)
                            .stateExecutionId(stateExecutionId)
                            .workflowExecutionId(workflowExecutionId)
                            .serviceId(serviceId)
                            .groupName(NewRelicMetricDataRecord.DEFAULT_GROUP_NAME)
                            .dataCollectionMinute(i)
                            .level(ClusterLevel.HF)
                            .build());
    }

    wingsPersistence.save(TimeSeriesDataRecord.getTimeSeriesDataRecordsFromNewRelicDataRecords(recordsToSave));

    NewRelicMetricDataRecord heartBeat = timeSeriesAnalysisService.getHeartBeat(StateType.NEW_RELIC, stateExecutionId,
        workflowExecutionId, serviceId, NewRelicMetricDataRecord.DEFAULT_GROUP_NAME, OrderType.ASC, accountId);
    assertThat(heartBeat.getDataCollectionMinute()).isEqualTo(0);
    heartBeat = timeSeriesAnalysisService.getHeartBeat(StateType.NEW_RELIC, stateExecutionId, workflowExecutionId,
        serviceId, NewRelicMetricDataRecord.DEFAULT_GROUP_NAME, OrderType.DESC, accountId);
    assertThat(heartBeat.getDataCollectionMinute()).isEqualTo(9);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLastCVAnalysisMinuteIfEmpty() {
    assertThat(timeSeriesAnalysisService.getLastCVAnalysisMinute(appId, cvConfigId)).isEqualTo(-1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLastCVAnalysisMinuteIfAvailable() {
    TimeSeriesMLAnalysisRecord analysisRecord = TimeSeriesMLAnalysisRecord.builder().build();
    analysisRecord.setTransactions(new HashMap<>());
    analysisRecord.setAppId(appId);
    analysisRecord.setCvConfigId(cvConfigId);
    analysisRecord.setStateType(StateType.NEW_RELIC);
    analysisRecord.setAnalysisMinute(currentEpochMinute);
    analysisRecord.setTag("testTag");
    wingsPersistence.save(analysisRecord);
    analysisRecord = TimeSeriesMLAnalysisRecord.builder().build();
    analysisRecord.setTransactions(new HashMap<>());
    analysisRecord.setAppId(appId);
    analysisRecord.setCvConfigId(cvConfigId);
    analysisRecord.setStateType(StateType.NEW_RELIC);
    analysisRecord.setAnalysisMinute(currentEpochMinute - 1);
    analysisRecord.setTag("testTag");
    assertThat(timeSeriesAnalysisService.getLastCVAnalysisMinute(appId, cvConfigId)).isEqualTo(currentEpochMinute);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMaxCVCollectionMinuteIfEmpty() {
    assertThat(timeSeriesAnalysisService.getMaxCVCollectionMinute(appId, cvConfigId, accountId)).isEqualTo(-1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMaxCVCollectionMinuteIfAvailable() {
    TimeSeriesDataRecord analysisRecord =
        TimeSeriesDataRecord.builder().dataCollectionMinute(currentEpochMinute).cvConfigId(cvConfigId).build();
    wingsPersistence.save(analysisRecord);
    wingsPersistence.save(
        TimeSeriesDataRecord.builder().dataCollectionMinute(currentEpochMinute - 1).cvConfigId(cvConfigId).build());
    assertThat(timeSeriesAnalysisService.getMaxCVCollectionMinute(appId, cvConfigId, accountId))
        .isEqualTo(currentEpochMinute);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSetTagInAnomRecords() {
    Map<String, Map<String, List<TimeSeriesMLHostSummary>>> anomMap = new HashMap<>();
    anomMap.put("txn1", new HashMap<>());
    MetricAnalysisRecord analysisRecord = TimeSeriesMLAnalysisRecord.builder().build();
    analysisRecord.setTransactions(new HashMap<>());
    analysisRecord.setMessage("test message");
    analysisRecord.setAppId(appId);
    analysisRecord.setStateExecutionId(stateExecutionId);
    analysisRecord.setStateType(StateType.NEW_RELIC);
    analysisRecord.setAnalysisMinute(12345);
    analysisRecord.setAnomalies(anomMap);
    analysisRecord.setTag("testTag");

    LearningEngineAnalysisTask task = LearningEngineAnalysisTask.builder()
                                          .state_execution_id(generateUuid())
                                          .executionStatus(ExecutionStatus.RUNNING)
                                          .cluster_level(2)
                                          .build();
    task.setUuid("taskID1");
    wingsPersistence.save(task);

    CVConfiguration cvConfiguration = new CVConfiguration();
    cvConfiguration.setStateType(StateType.NEW_RELIC);
    cvConfiguration.setServiceId(serviceId);
    cvConfiguration.setEnvId(generateUuid());
    cvConfiguration.setAccountId(accountId);

    cvConfiguration.setUuid(cvConfigId);
    wingsPersistence.save(cvConfiguration);

    timeSeriesAnalysisService.saveAnalysisRecordsML(accountId, StateType.NEW_RELIC, appId, stateExecutionId,
        workflowExecutionId, "default", 12345, "taskID1", null, cvConfigId, analysisRecord, "testTag");

    TimeSeriesAnomaliesRecord anomaliesRecord = wingsPersistence.createQuery(TimeSeriesAnomaliesRecord.class)
                                                    .filter(TimeSeriesAnomaliesRecordKeys.cvConfigId, cvConfigId)
                                                    .get();

    assertThat(anomaliesRecord).isNotNull();
    assertThat(anomaliesRecord.getTag()).isEqualTo("testTag");

    task = wingsPersistence.get(LearningEngineAnalysisTask.class, "taskID1");
    assertThat(task.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetCumulativeSumsWithTimeRange() {
    String cvConfigId = generateUuid();
    String appId = generateUuid();
    String tag = "tag";
    int currentMinute = (int) TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary());
    IntStream.range(0, 10).forEach(minute
        -> wingsPersistence.save(TimeSeriesCumulativeSums.builder()
                                     .cvConfigId(cvConfigId)
                                     .tag(tag)
                                     .analysisMinute(currentMinute + minute)
                                     .build()));

    assertThat(timeSeriesAnalysisService.getCumulativeSumsForRange(
                   appId, cvConfigId, currentMinute - 2, currentMinute - 1, tag))
        .isEmpty();
    assertThat(timeSeriesAnalysisService.getCumulativeSumsForRange(appId, cvConfigId, currentMinute, currentMinute, tag)
                   .size())
        .isEqualTo(1);
    assertThat(
        timeSeriesAnalysisService.getCumulativeSumsForRange(appId, cvConfigId, currentMinute, currentMinute + 7, tag)
            .size())
        .isEqualTo(8);
    assertThat(timeSeriesAnalysisService
                   .getCumulativeSumsForRange(appId, cvConfigId, currentMinute - 1, currentMinute + 11, tag)
                   .size())
        .isEqualTo(10);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetCumulativeSumsWithTag() {
    String cvConfigId = generateUuid();
    String appId = generateUuid();
    int currentMinute = (int) TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary());
    wingsPersistence.save(
        TimeSeriesCumulativeSums.builder().cvConfigId(cvConfigId).tag("tag1").analysisMinute(currentMinute).build());
    wingsPersistence.save(
        TimeSeriesCumulativeSums.builder().cvConfigId(cvConfigId).tag("tag2").analysisMinute(currentMinute).build());

    Set<TimeSeriesCumulativeSums> result = timeSeriesAnalysisService.getCumulativeSumsForRange(
        appId, cvConfigId, currentMinute, currentMinute, "test-tag");
    assertThat(result).isEmpty();
    result =
        timeSeriesAnalysisService.getCumulativeSumsForRange(appId, cvConfigId, currentMinute, currentMinute, "tag1");
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.iterator().next().getTag()).isEqualTo("tag1");
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetCumulativeSumsForRangeWhenNoCumulativeSumAvailable() {
    Set<TimeSeriesCumulativeSums> timeSeriesCumulativeSums =
        timeSeriesAnalysisService.getCumulativeSumsForRange(generateUuid(), generateUuid(), 10, 20, "test-tag");
    assertThat(timeSeriesCumulativeSums).isEmpty();
  }

  @Test(expected = WingsException.class)
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetCumulativeSumsForRangeInCaseOfInvalidParams() {
    timeSeriesAnalysisService.getCumulativeSumsForRange(generateUuid(), null, 10, 20, "test-tag");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetCumulativeSumsForRangeInCaseOfInvalidTimeRange() {
    timeSeriesAnalysisService.getCumulativeSumsForRange(generateUuid(), generateUuid(), 21, 20, "test-tag");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLastDataCollectedMinuteWhenRecordDoesNotExist() {
    long minute = timeSeriesAnalysisService.getLastDataCollectedMinute(appId, stateExecutionId, StateType.APP_DYNAMICS);
    assertThat(minute).isEqualTo(-1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLastDataCollectedMinuteWhenRecordDoesExists() {
    int dataCollectionMinute = (int) TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis());

    wingsPersistence.save(NewRelicMetricDataRecord.builder()
                              .dataCollectionMinute(dataCollectionMinute)
                              .stateExecutionId(stateExecutionId)
                              .appId(appId)
                              .host("test-host")
                              .workflowId(UUID.randomUUID().toString())
                              .workflowExecutionId(workflowExecutionId)
                              .stateType(DelegateStateType.APP_DYNAMICS)
                              .build());

    wingsPersistence.save(NewRelicMetricDataRecord.builder()
                              .dataCollectionMinute(dataCollectionMinute + 1)
                              .stateExecutionId(stateExecutionId)
                              .appId(appId)
                              .host("test-host")
                              .workflowId(UUID.randomUUID().toString())
                              .workflowExecutionId(workflowExecutionId)
                              .stateType(DelegateStateType.APP_DYNAMICS)
                              .build());
    long minute = timeSeriesAnalysisService.getLastDataCollectedMinute(appId, stateExecutionId, StateType.APP_DYNAMICS);
    assertThat(minute).isEqualTo(dataCollectionMinute + 1);
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulWorkflowExecutionIdWithDataInCaseOfNoData() {
    List<String> lastSuccessfulWorkflowExecutionIds = Lists.newArrayList(generateUuid(), generateUuid());
    NewRelicMetricDataRecord newRelicMetricDataRecord =
        NewRelicMetricDataRecord.builder()
            .level(ClusterLevel.HF)
            .stateType(DelegateStateType.NEW_RELIC)
            .serviceId(serviceId)
            .workflowExecutionId(lastSuccessfulWorkflowExecutionIds.get(0))
            .stateExecutionId(generateUuid())
            .cvConfigId(generateUuid())
            .build();
    wingsPersistence.save(newRelicMetricDataRecord);

    wingsPersistence.save(NewRelicMetricDataRecord.builder()
                              .level(ClusterLevel.H0)
                              .stateType(DelegateStateType.NEW_RELIC)
                              .serviceId(serviceId)
                              .workflowExecutionId(lastSuccessfulWorkflowExecutionIds.get(1))
                              .stateExecutionId(generateUuid())
                              .cvConfigId(generateUuid())
                              .build());
    doReturn(lastSuccessfulWorkflowExecutionIds)
        .when(timeSeriesAnalysisService)
        .getLastSuccessfulWorkflowExecutionIds(anyString(), anyString(), anyString());
    String workflowId = generateUuid();
    String workflowExecutionIdsWithData = timeSeriesAnalysisService.getLastSuccessfulWorkflowExecutionIdWithData(
        StateType.NEW_RELIC, appId, workflowId, serviceId);
    assertThat(workflowExecutionIdsWithData).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulWorkflowExecutionIdWithDataInCaseData() {
    List<String> lastSuccessfulWorkflowExecutionIds = Lists.newArrayList(generateUuid(), generateUuid());
    NewRelicMetricDataRecord newRelicMetricDataRecord =
        NewRelicMetricDataRecord.builder()
            .level(ClusterLevel.L1)
            .stateType(DelegateStateType.NEW_RELIC)
            .serviceId(serviceId)
            .workflowExecutionId(lastSuccessfulWorkflowExecutionIds.get(0))
            .stateExecutionId(generateUuid())
            .cvConfigId(generateUuid())
            .build();
    wingsPersistence.save(newRelicMetricDataRecord);

    wingsPersistence.save(NewRelicMetricDataRecord.builder()
                              .level(ClusterLevel.H0)
                              .stateType(DelegateStateType.NEW_RELIC)
                              .serviceId(serviceId)
                              .workflowExecutionId(lastSuccessfulWorkflowExecutionIds.get(1))
                              .stateExecutionId(generateUuid())
                              .cvConfigId(generateUuid())
                              .build());
    doReturn(lastSuccessfulWorkflowExecutionIds)
        .when(timeSeriesAnalysisService)
        .getLastSuccessfulWorkflowExecutionIds(anyString(), anyString(), anyString());
    String workflowId = generateUuid();
    String workflowExecutionIdsWithData = timeSeriesAnalysisService.getLastSuccessfulWorkflowExecutionIdWithData(
        StateType.NEW_RELIC, appId, workflowId, serviceId);
    assertThat(workflowExecutionIdsWithData).isEqualTo(lastSuccessfulWorkflowExecutionIds.get(0));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulWorkflowExecutionIdWithDataIfNoRecords() {
    List<String> lastSuccessfulWorkflowExecutionIds = Lists.newArrayList(generateUuid(), generateUuid());
    doReturn(lastSuccessfulWorkflowExecutionIds)
        .when(timeSeriesAnalysisService)
        .getLastSuccessfulWorkflowExecutionIds(anyString(), anyString(), anyString());
    String workflowId = generateUuid();
    String workflowExecutionIdsWithData = timeSeriesAnalysisService.getLastSuccessfulWorkflowExecutionIdWithData(
        StateType.NEW_RELIC, appId, workflowId, serviceId);
    assertThat(workflowExecutionIdsWithData).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMetricTemplateWithCategorizedThresholds_whenOnlyDefaultThresholdsAreDefinedForNewRelic() {
    Map<String, Map<String, TimeSeriesMetricDefinition>> metricTemplateWithCategorizedThresholds =
        timeSeriesAnalysisService.getMetricTemplateWithCategorizedThresholds(appId, StateType.NEW_RELIC,
            stateExecutionId, serviceId, cvConfigId, NewRelicMetricDataRecord.DEFAULT_GROUP_NAME, Version.PROD);
    // if this becomes unmaintainable. Please remove this test and find some other way to test this method.
    @Language("JSON")
    String expectedJson = "{\n"
        + "  \"default\": {\n"
        + "    \"averageResponseTime\": {\n"
        + "      \"metricName\": \"averageResponseTime\",\n"
        + "      \"metricType\": \"RESP_TIME\",\n"
        + "      \"categorizedThresholds\": {\n"
        + "        \"DEFAULT\": [\n"
        + "          {\n"
        + "            \"thresholdType\": \"ALERT_WHEN_HIGHER\",\n"
        + "            \"comparisonType\": \"RATIO\",\n"
        + "            \"ml\": 0.2\n"
        + "          },\n"
        + "          {\n"
        + "            \"thresholdType\": \"ALERT_WHEN_HIGHER\",\n"
        + "            \"comparisonType\": \"DELTA\",\n"
        + "            \"ml\": 20\n"
        + "          }\n"
        + "        ]\n"
        + "      },\n"
        + "      \"thresholds\": [\n"
        + "        {\n"
        + "          \"thresholdType\": \"ALERT_WHEN_HIGHER\",\n"
        + "          \"comparisonType\": \"RATIO\",\n"
        + "          \"ml\": 0.2\n"
        + "        },\n"
        + "        {\n"
        + "          \"thresholdType\": \"ALERT_WHEN_HIGHER\",\n"
        + "          \"comparisonType\": \"DELTA\",\n"
        + "          \"ml\": 20\n"
        + "        }\n"
        + "      ]\n"
        + "    },\n"
        + "    \"requestsPerMinute\": {\n"
        + "      \"metricName\": \"requestsPerMinute\",\n"
        + "      \"metricType\": \"THROUGHPUT\",\n"
        + "      \"categorizedThresholds\": {\n"
        + "        \"DEFAULT\": [\n"
        + "          {\n"
        + "            \"thresholdType\": \"ALERT_WHEN_LOWER\",\n"
        + "            \"comparisonType\": \"RATIO\",\n"
        + "            \"ml\": 0.2\n"
        + "          },\n"
        + "          {\n"
        + "            \"thresholdType\": \"ALERT_WHEN_LOWER\",\n"
        + "            \"comparisonType\": \"DELTA\",\n"
        + "            \"ml\": 20\n"
        + "          }\n"
        + "        ]\n"
        + "      },\n"
        + "      \"thresholds\": [\n"
        + "        {\n"
        + "          \"thresholdType\": \"ALERT_WHEN_LOWER\",\n"
        + "          \"comparisonType\": \"RATIO\",\n"
        + "          \"ml\": 0.2\n"
        + "        },\n"
        + "        {\n"
        + "          \"thresholdType\": \"ALERT_WHEN_LOWER\",\n"
        + "          \"comparisonType\": \"DELTA\",\n"
        + "          \"ml\": 20\n"
        + "        }\n"
        + "      ]\n"
        + "    },\n"
        + "    \"error\": {\n"
        + "      \"metricName\": \"error\",\n"
        + "      \"metricType\": \"ERROR\",\n"
        + "      \"categorizedThresholds\": {\n"
        + "        \"DEFAULT\": [\n"
        + "          {\n"
        + "            \"thresholdType\": \"ALERT_WHEN_HIGHER\",\n"
        + "            \"comparisonType\": \"RATIO\",\n"
        + "            \"ml\": 0.01\n"
        + "          },\n"
        + "          {\n"
        + "            \"thresholdType\": \"ALERT_WHEN_HIGHER\",\n"
        + "            \"comparisonType\": \"DELTA\",\n"
        + "            \"ml\": 0.01\n"
        + "          }\n"
        + "        ]\n"
        + "      },\n"
        + "      \"thresholds\": [\n"
        + "        {\n"
        + "          \"thresholdType\": \"ALERT_WHEN_HIGHER\",\n"
        + "          \"comparisonType\": \"RATIO\",\n"
        + "          \"ml\": 0.01\n"
        + "        },\n"
        + "        {\n"
        + "          \"thresholdType\": \"ALERT_WHEN_HIGHER\",\n"
        + "          \"comparisonType\": \"DELTA\",\n"
        + "          \"ml\": 0.01\n"
        + "        }\n"
        + "      ]\n"
        + "    },\n"
        + "    \"apdexScore\": {\n"
        + "      \"metricName\": \"apdexScore\",\n"
        + "      \"metricType\": \"APDEX\",\n"
        + "      \"categorizedThresholds\": {\n"
        + "        \"DEFAULT\": [\n"
        + "          {\n"
        + "            \"thresholdType\": \"ALERT_WHEN_LOWER\",\n"
        + "            \"comparisonType\": \"RATIO\",\n"
        + "            \"ml\": 0.2\n"
        + "          },\n"
        + "          {\n"
        + "            \"thresholdType\": \"ALERT_WHEN_LOWER\",\n"
        + "            \"comparisonType\": \"DELTA\",\n"
        + "            \"ml\": 0.01\n"
        + "          }\n"
        + "        ]\n"
        + "      },\n"
        + "      \"thresholds\": [\n"
        + "        {\n"
        + "          \"thresholdType\": \"ALERT_WHEN_LOWER\",\n"
        + "          \"comparisonType\": \"RATIO\",\n"
        + "          \"ml\": 0.2\n"
        + "        },\n"
        + "        {\n"
        + "          \"thresholdType\": \"ALERT_WHEN_LOWER\",\n"
        + "          \"comparisonType\": \"DELTA\",\n"
        + "          \"ml\": 0.01\n"
        + "        }\n"
        + "      ]\n"
        + "    }\n"
        + "  }\n"
        + "}";

    JsonParser parser = new JsonParser();
    // if this becomes unmaintainable. Please remove this test and find some other way to test this method.
    assertThat(parser.parse(JsonUtils.asJson(metricTemplateWithCategorizedThresholds)))
        .isEqualTo(parser.parse(expectedJson));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricTemplate_WithoutCustomThresholds() {
    Map<String, Map<String, TimeSeriesMetricDefinition>> metricTemplates =
        timeSeriesAnalysisService.getMetricTemplate(appId, StateType.NEW_RELIC, stateExecutionId, serviceId, cvConfigId,
            NewRelicMetricDataRecord.DEFAULT_GROUP_NAME);
    assertThat(metricTemplates).isNotEmpty();
    assertThat(metricTemplates.keySet().size()).isEqualTo(1);
    assertThat(metricTemplates.get(DEFAULT_GROUP_NAME).keySet())
        .isEqualTo(
            new HashSet<>(Lists.newArrayList("averageResponseTime", "error", "requestsPerMinute", "apdexScore")));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricTemplate_WithCustomThresholdsAndGroupName() {
    String metricName = "metric1";
    String txnName = "txn1";
    TimeSeriesMLTransactionThresholds transactionThresholds =
        TimeSeriesMLTransactionThresholds.builder()
            .serviceId(serviceId)
            .stateType(StateType.NEW_RELIC)
            .cvConfigId(cvConfigId)
            .groupName(DEFAULT_GROUP_NAME)
            .transactionName(txnName)
            .metricName(metricName)
            .thresholds(TimeSeriesMetricDefinition.builder()
                            .metricName(metricName)
                            .metricType(MetricType.INFRA)
                            .customThresholds(Lists.newArrayList(Threshold.builder().ml(0.1).build()))
                            .build())
            .build();
    transactionThresholds.setAppId(appId);
    wingsPersistence.save(transactionThresholds);
    Map<String, Map<String, TimeSeriesMetricDefinition>> metricTemplates =
        timeSeriesAnalysisService.getMetricTemplate(appId, StateType.NEW_RELIC, stateExecutionId, serviceId, cvConfigId,
            NewRelicMetricDataRecord.DEFAULT_GROUP_NAME);
    assertThat(metricTemplates).isNotEmpty();
    assertThat(metricTemplates.keySet().size()).isEqualTo(2);
    assertThat(metricTemplates.get(DEFAULT_GROUP_NAME).keySet())
        .isEqualTo(
            new HashSet<>(Lists.newArrayList("averageResponseTime", "error", "requestsPerMinute", "apdexScore")));
    assertThat(metricTemplates.get(txnName).values().size()).isEqualTo(1);
    assertThat(metricTemplates.get(txnName).get(metricName).getCustomThresholds().size()).isEqualTo(1);
    assertThat(metricTemplates.get(txnName).get(metricName).getCustomThresholds().get(0).getMl()).isEqualTo(0.1);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricTemplate_WithCustomThresholdsAndWithoutGroupName() {
    String metricName = "metric1";
    String txnName = "txn1";
    TimeSeriesMLTransactionThresholds transactionThresholds =
        TimeSeriesMLTransactionThresholds.builder()
            .serviceId(serviceId)
            .stateType(StateType.NEW_RELIC)
            .cvConfigId(cvConfigId)
            .transactionName(txnName)
            .metricName(metricName)
            .thresholds(TimeSeriesMetricDefinition.builder()
                            .metricName(metricName)
                            .metricType(MetricType.INFRA)
                            .customThresholds(Lists.newArrayList(Threshold.builder().ml(0.1).build()))
                            .build())
            .build();
    transactionThresholds.setAppId(appId);
    wingsPersistence.save(transactionThresholds);
    Map<String, Map<String, TimeSeriesMetricDefinition>> metricTemplates =
        timeSeriesAnalysisService.getMetricTemplate(appId, StateType.NEW_RELIC, stateExecutionId, serviceId, cvConfigId,
            NewRelicMetricDataRecord.DEFAULT_GROUP_NAME);
    assertThat(metricTemplates).isNotEmpty();
    assertThat(metricTemplates.keySet().size()).isEqualTo(2);
    assertThat(metricTemplates.get(DEFAULT_GROUP_NAME).keySet())
        .isEqualTo(
            new HashSet<>(Lists.newArrayList("averageResponseTime", "error", "requestsPerMinute", "apdexScore")));
    assertThat(metricTemplates.get(txnName).values().size()).isEqualTo(1);
    assertThat(metricTemplates.get(txnName).get(metricName).getCustomThresholds().size()).isEqualTo(1);
    assertThat(metricTemplates.get(txnName).get(metricName).getCustomThresholds().get(0).getMl()).isEqualTo(0.1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveMetricTemplates() {
    TimeSeriesMetricDefinition timeSeriesMetricDefinition =
        TimeSeriesMetricDefinition.builder().metricName("test").build();
    Map<String, TimeSeriesMetricDefinition> timeSeriesMetricDefinitionMap = new HashMap<>();
    timeSeriesMetricDefinitionMap.put(timeSeriesMetricDefinition.getMetricName(), timeSeriesMetricDefinition);
    timeSeriesAnalysisService.saveMetricTemplates(
        accountId, appId, StateType.NEW_RELIC, stateExecutionId, timeSeriesMetricDefinitionMap);
    TimeSeriesMetricTemplates timeSeriesMetricTemplates = wingsPersistence.createQuery(TimeSeriesMetricTemplates.class)
                                                              .filter("stateExecutionId", stateExecutionId)
                                                              .get();
    assertThat(timeSeriesMetricTemplates.getMetricTemplates()).isEqualTo(timeSeriesMetricDefinitionMap);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMetricTemplates() {
    TimeSeriesMetricDefinition timeSeriesMetricDefinition =
        TimeSeriesMetricDefinition.builder().metricName("test").build();
    Map<String, TimeSeriesMetricDefinition> timeSeriesMetricDefinitionMap = new HashMap<>();
    timeSeriesMetricDefinitionMap.put(timeSeriesMetricDefinition.getMetricName(), timeSeriesMetricDefinition);
    TimeSeriesMetricTemplates timeSeriesMetricTemplates = TimeSeriesMetricTemplates.builder()
                                                              .stateExecutionId(stateExecutionId)
                                                              .cvConfigId(cvConfigId)
                                                              .stateType(StateType.NEW_RELIC)
                                                              .metricTemplates(timeSeriesMetricDefinitionMap)
                                                              .build();

    wingsPersistence.save(timeSeriesMetricTemplates);
    Map<String, TimeSeriesMetricDefinition> timeSeriesMetricDefinitionResult =
        timeSeriesAnalysisService.getMetricTemplates(accountId, StateType.NEW_RELIC, stateExecutionId, cvConfigId);

    assertThat(timeSeriesMetricDefinitionResult).isEqualTo(timeSeriesMetricDefinitionMap);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetMetricTemplatesWithDotsInName() {
    String metricName = "metric.with.dots.in.name";
    TimeSeriesMetricDefinition timeSeriesMetricDefinition =
        TimeSeriesMetricDefinition.builder().metricName(replaceDotWithUnicode(metricName)).build();
    Map<String, TimeSeriesMetricDefinition> timeSeriesMetricDefinitionMap = new HashMap<>();
    timeSeriesMetricDefinitionMap.put(timeSeriesMetricDefinition.getMetricName(), timeSeriesMetricDefinition);
    TimeSeriesMetricTemplates timeSeriesMetricTemplates = TimeSeriesMetricTemplates.builder()
                                                              .stateExecutionId(stateExecutionId)
                                                              .cvConfigId(cvConfigId)
                                                              .stateType(StateType.NEW_RELIC)
                                                              .metricTemplates(timeSeriesMetricDefinitionMap)
                                                              .build();

    wingsPersistence.save(timeSeriesMetricTemplates);
    Map<String, TimeSeriesMetricDefinition> timeSeriesMetricDefinitionResult =
        timeSeriesAnalysisService.getMetricTemplates(accountId, StateType.NEW_RELIC, stateExecutionId, cvConfigId);

    Map<String, TimeSeriesMetricDefinition> expectedDefinitionMap = new HashMap<>();
    expectedDefinitionMap.put(metricName, timeSeriesMetricDefinition);
    assertThat(timeSeriesMetricDefinitionResult).isEqualTo(expectedDefinitionMap);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMetricGroups() {
    TimeSeriesMlAnalysisGroupInfo timeSeriesMlAnalysisGroupInfo =
        TimeSeriesMlAnalysisGroupInfo.builder()
            .groupName("default")
            .mlAnalysisType(TimeSeriesMlAnalysisType.PREDICTIVE)
            .dependencyPath("path")
            .build();
    Map<String, TimeSeriesMlAnalysisGroupInfo> groupMap = new HashMap<>();
    groupMap.put(timeSeriesMlAnalysisGroupInfo.getGroupName(), timeSeriesMlAnalysisGroupInfo);
    TimeSeriesMetricGroup timeSeriesMetricGroup = TimeSeriesMetricGroup.builder()
                                                      .stateExecutionId(stateExecutionId)
                                                      .stateType(StateType.NEW_RELIC)
                                                      .appId(appId)
                                                      .groups(groupMap)
                                                      .build();
    wingsPersistence.save(timeSeriesMetricGroup);
    Map<String, TimeSeriesMlAnalysisGroupInfo> results =
        timeSeriesAnalysisService.getMetricGroups(appId, stateExecutionId);
    assertThat(results).isEqualTo(groupMap);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMetricGroupsIfNoTimeSeriesMetricGroup() {
    Map<String, TimeSeriesMlAnalysisGroupInfo> defaultMap =
        new ImmutableMap.Builder<String, TimeSeriesMlAnalysisGroupInfo>()
            .put(DEFAULT_GROUP_NAME,
                TimeSeriesMlAnalysisGroupInfo.builder()
                    .groupName(DEFAULT_GROUP_NAME)
                    .dependencyPath(DEFAULT_GROUP_NAME)
                    .mlAnalysisType(TimeSeriesMlAnalysisType.COMPARATIVE)
                    .build())
            .build();
    Map<String, TimeSeriesMlAnalysisGroupInfo> results =
        timeSeriesAnalysisService.getMetricGroups(appId, stateExecutionId);
    assertThat(results).isEqualTo(defaultMap);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testBumpCollectionMinuteToProcess() {
    List<NewRelicMetricDataRecord> recordsToSave = new ArrayList<>();
    recordsToSave.add(NewRelicMetricDataRecord.builder()
                          .stateType(DelegateStateType.NEW_RELIC)
                          .level(ClusterLevel.H0)
                          .stateExecutionId(stateExecutionId)
                          .appId(appId)
                          .dataCollectionMinute(currentEpochMinute)
                          .build());
    recordsToSave.add(NewRelicMetricDataRecord.builder()
                          .stateType(DelegateStateType.NEW_RELIC)
                          .level(ClusterLevel.H0)
                          .stateExecutionId(stateExecutionId)
                          .appId(appId)
                          .dataCollectionMinute(currentEpochMinute - 1)
                          .build());
    recordsToSave.add(NewRelicMetricDataRecord.builder()
                          .stateType(DelegateStateType.NEW_RELIC)
                          .level(ClusterLevel.H0)
                          .stateExecutionId(stateExecutionId)
                          .appId(appId)
                          .dataCollectionMinute(currentEpochMinute - 10)
                          .build());
    recordsToSave.add(NewRelicMetricDataRecord.builder()
                          .stateType(DelegateStateType.NEW_RELIC)
                          .level(ClusterLevel.H0)
                          .stateExecutionId(stateExecutionId)
                          .appId(appId)
                          .dataCollectionMinute(currentEpochMinute + 1)
                          .build());
    wingsPersistence.save(TimeSeriesDataRecord.getTimeSeriesDataRecordsFromNewRelicDataRecords(recordsToSave));
    timeSeriesAnalysisService.bumpCollectionMinuteToProcess(
        appId, stateExecutionId, workflowExecutionId, DEFAULT_GROUP_NAME, currentEpochMinute, accountId);
    PageRequest<TimeSeriesDataRecord> pageRequest =
        aPageRequest()
            .withLimit(UNLIMITED)
            .addFilter(NewRelicMetricDataRecordKeys.stateExecutionId, Operator.EQ, stateExecutionId)
            .addFilter(NewRelicMetricDataRecordKeys.groupName, Operator.EQ, DEFAULT_GROUP_NAME)
            .addFilter(NewRelicMetricDataRecordKeys.dataCollectionMinute, Operator.LT_EQ, currentEpochMinute)
            .addOrder(NewRelicMetricDataRecordKeys.dataCollectionMinute, OrderType.DESC)
            .build();
    PageResponse<TimeSeriesDataRecord> dataRecords =
        wingsPersistence.query(TimeSeriesDataRecord.class, pageRequest, excludeAuthority);
    List<TimeSeriesDataRecord> timeSeriesDataRecords = dataRecords.getResponse();
    assertThat(timeSeriesDataRecords.size()).isEqualTo(3);
    timeSeriesDataRecords.forEach(record -> assertThat(record.getLevel()).isEqualTo(ClusterLevel.HF));
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetAnalysisMinuteForLastHeartbeatRecord() {
    List<NewRelicMetricDataRecord> recordsToSave = Lists.newArrayList(NewRelicMetricDataRecord.builder()
                                                                          .stateType(DelegateStateType.NEW_RELIC)
                                                                          .serviceId(serviceId)
                                                                          .level(ClusterLevel.L1)
                                                                          .stateExecutionId(stateExecutionId)
                                                                          .appId(appId)
                                                                          .dataCollectionMinute(currentEpochMinute)
                                                                          .build(),
        NewRelicMetricDataRecord.builder()
            .stateType(DelegateStateType.NEW_RELIC)
            .serviceId(serviceId)
            .level(ClusterLevel.HF)
            .stateExecutionId(stateExecutionId)
            .appId(appId)
            .dataCollectionMinute(currentEpochMinute - 1)
            .build(),
        NewRelicMetricDataRecord.builder()
            .stateType(DelegateStateType.NEW_RELIC)
            .serviceId(serviceId)
            .level(ClusterLevel.H0)
            .stateExecutionId(stateExecutionId)
            .appId(appId)
            .dataCollectionMinute(currentEpochMinute - 10)
            .build(),
        NewRelicMetricDataRecord.builder()
            .stateType(DelegateStateType.NEW_RELIC)
            .serviceId(serviceId)
            .level(ClusterLevel.H0)
            .stateExecutionId(stateExecutionId)
            .appId(appId)
            .dataCollectionMinute(currentEpochMinute + 1)
            .build());
    wingsPersistence.save(TimeSeriesDataRecord.getTimeSeriesDataRecordsFromNewRelicDataRecords(recordsToSave));
    NewRelicMetricDataRecord newRelicMetricDataRecord = timeSeriesAnalysisService.getAnalysisMinute(
        StateType.NEW_RELIC, appId, stateExecutionId, workflowExecutionId, serviceId, DEFAULT_GROUP_NAME, accountId);
    assertThat(newRelicMetricDataRecord.getLevel()).isEqualTo(ClusterLevel.H0);
    assertThat(newRelicMetricDataRecord.getDataCollectionMinute()).isEqualTo(currentEpochMinute + 1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetAnalysisMinuteIfNoHeartbeatAvailable() {
    wingsPersistence.save(NewRelicMetricDataRecord.builder()
                              .stateType(DelegateStateType.NEW_RELIC)
                              .serviceId(serviceId)
                              .level(ClusterLevel.L1)
                              .stateExecutionId(stateExecutionId)
                              .appId(appId)
                              .dataCollectionMinute(currentEpochMinute)
                              .build());
    wingsPersistence.save(NewRelicMetricDataRecord.builder()
                              .stateType(DelegateStateType.NEW_RELIC)
                              .serviceId(serviceId)
                              .level(ClusterLevel.HF)
                              .stateExecutionId(stateExecutionId)
                              .appId(appId)
                              .dataCollectionMinute(currentEpochMinute - 1)
                              .build());

    NewRelicMetricDataRecord newRelicMetricDataRecord = timeSeriesAnalysisService.getAnalysisMinute(
        StateType.NEW_RELIC, appId, stateExecutionId, workflowExecutionId, serviceId, DEFAULT_GROUP_NAME, accountId);
    assertThat(newRelicMetricDataRecord).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetHistoricalAnalysis() {
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord = TimeSeriesMLAnalysisRecord.builder().build();
    timeSeriesMLAnalysisRecord.setCvConfigId(cvConfigId);
    timeSeriesMLAnalysisRecord.setAnalysisMinute((int) (currentEpochMinute - TimeUnit.DAYS.toMinutes(7)));
    timeSeriesMLAnalysisRecord.setTag("tag");
    timeSeriesMLAnalysisRecord.setStateExecutionId(stateExecutionId);
    timeSeriesMLAnalysisRecord.setAppId(appId);
    wingsPersistence.save(timeSeriesMLAnalysisRecord);
    List<TimeSeriesMLAnalysisRecord> records = timeSeriesAnalysisService.getHistoricalAnalysis(
        accountId, appId, serviceId, cvConfigId, currentEpochMinute, "tag");
    assertThat(records.size()).isEqualTo(1);
    assertThat(records.get(0).getAnalysisMinute()).isEqualTo(timeSeriesMLAnalysisRecord.getAnalysisMinute());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetPreviousAnomaliesWithFiltering() {
    Map<String, Map<String, List<TimeSeriesMLHostSummary>>> anomalies = new HashMap<>();
    List<Double> testData = new ArrayList<>();
    testData.add(1.0);
    testData.add(2.0);
    TimeSeriesMLHostSummary timeSeriesMLHostSummary =
        TimeSeriesMLHostSummary.builder().test_data(testData).risk(RiskLevel.HIGH.getRisk()).build();
    Map<String, List<TimeSeriesMLHostSummary>> summaryMap = new HashMap<>();
    summaryMap.put("metric", Lists.newArrayList(timeSeriesMLHostSummary));
    anomalies.put("txn", summaryMap);
    TimeSeriesAnomaliesRecord timeSeriesAnomaliesRecord =
        TimeSeriesAnomaliesRecord.builder().cvConfigId(cvConfigId).tag("tag").anomalies(anomalies).build();
    timeSeriesAnomaliesRecord.compressAnomalies();
    wingsPersistence.save(timeSeriesAnomaliesRecord);
    Map<String, List<String>> metrics = new HashMap<>();
    metrics.put("txn", Lists.newArrayList("metric"));
    TimeSeriesAnomaliesRecord result =
        timeSeriesAnalysisService.getPreviousAnomalies(appId, cvConfigId, metrics, "tag");
    assertThat(result).isNotNull();
    assertThat(result.getAnomalies()).isEqualTo(anomalies);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetPreviousAnomaliesWhenNoData() {
    Map<String, List<String>> metrics = new HashMap<>();
    metrics.put("txn", Lists.newArrayList("metric"));
    TimeSeriesAnomaliesRecord result =
        timeSeriesAnalysisService.getPreviousAnomalies(appId, cvConfigId, metrics, "tag");
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetPreviousAnomaliesWhenMetricsIsEmpty() {
    Map<String, Map<String, List<TimeSeriesMLHostSummary>>> anomalies = new HashMap<>();
    List<Double> testData = new ArrayList<>();
    testData.add(1.0);
    testData.add(2.0);
    TimeSeriesMLHostSummary timeSeriesMLHostSummary =
        TimeSeriesMLHostSummary.builder().test_data(testData).risk(RiskLevel.HIGH.getRisk()).build();
    Map<String, List<TimeSeriesMLHostSummary>> summaryMap = new HashMap<>();
    summaryMap.put("metric", Lists.newArrayList(timeSeriesMLHostSummary));
    anomalies.put("txn", summaryMap);
    TimeSeriesAnomaliesRecord timeSeriesAnomaliesRecord =
        TimeSeriesAnomaliesRecord.builder().cvConfigId(cvConfigId).tag("tag").anomalies(anomalies).build();
    timeSeriesAnomaliesRecord.compressAnomalies();
    wingsPersistence.save(timeSeriesAnomaliesRecord);
    TimeSeriesAnomaliesRecord result =
        timeSeriesAnalysisService.getPreviousAnomalies(appId, cvConfigId, new HashMap<>(), "tag");
    assertThat(result.getAnomalies()).isEqualTo(anomalies);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testSaveAnalysisRecordsIgnoringDuplicate_NoExistingEntries() {
    NewRelicMetricAnalysisRecord record = NewRelicMetricAnalysisRecord.builder()
                                              .stateExecutionId(stateExecutionId)
                                              .workflowExecutionId(workflowExecutionId)
                                              .groupName(DEFAULT_GROUP_NAME)
                                              .analysisMinute(1)
                                              .build();
    timeSeriesAnalysisService.saveAnalysisRecordsIgnoringDuplicate(record);

    List<NewRelicMetricAnalysisRecord> savedRecord =
        wingsPersistence.createQuery(NewRelicMetricAnalysisRecord.class).asList();
    assertThat(savedRecord.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testSaveAnalysisRecordsIgnoringDuplicate_WithDuplicate() {
    NewRelicMetricAnalysisRecord record = NewRelicMetricAnalysisRecord.builder()
                                              .stateExecutionId(stateExecutionId)
                                              .workflowExecutionId(workflowExecutionId)
                                              .groupName(DEFAULT_GROUP_NAME)
                                              .analysisMinute(1)
                                              .build();
    wingsPersistence.save(record);

    timeSeriesAnalysisService.saveAnalysisRecordsIgnoringDuplicate(record);

    List<NewRelicMetricAnalysisRecord> savedRecord =
        wingsPersistence.createQuery(NewRelicMetricAnalysisRecord.class).asList();
    assertThat(savedRecord.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testSaveAnalysisRecordsIgnoringDuplicate_WithoutDuplicate() {
    NewRelicMetricAnalysisRecord record = NewRelicMetricAnalysisRecord.builder()
                                              .stateExecutionId(stateExecutionId)
                                              .workflowExecutionId(workflowExecutionId)
                                              .groupName(DEFAULT_GROUP_NAME)
                                              .analysisMinute(1)
                                              .build();
    wingsPersistence.save(record);

    NewRelicMetricAnalysisRecord newRecord = NewRelicMetricAnalysisRecord.builder()
                                                 .stateExecutionId(stateExecutionId)
                                                 .workflowExecutionId(workflowExecutionId)
                                                 .groupName(DEFAULT_GROUP_NAME)
                                                 .analysisMinute(2)
                                                 .build();
    timeSeriesAnalysisService.saveAnalysisRecordsIgnoringDuplicate(newRecord);

    List<NewRelicMetricAnalysisRecord> savedRecord =
        wingsPersistence.createQuery(NewRelicMetricAnalysisRecord.class).asList();
    assertThat(savedRecord.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetKeyTransactionsHappyCase() {
    TimeSeriesKeyTransactions keyTransactions = TimeSeriesKeyTransactions.builder()
                                                    .cvConfigId(cvConfigId)
                                                    .keyTransactions(Sets.newHashSet("transaction1", "transaction2"))
                                                    .build();
    wingsPersistence.save(keyTransactions);

    Set<String> transactions = timeSeriesAnalysisService.getKeyTransactions(cvConfigId);

    assertThat(transactions).isNotNull();
    assertThat(transactions.size()).isEqualTo(2);
    assertThat(transactions.containsAll(Arrays.asList("transaction1", "transaction2"))).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetKeyTransactionsNullInput() {
    Set<String> transactions = timeSeriesAnalysisService.getKeyTransactions(null);

    assertThat(transactions).isNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetKeyTransactionsNoKeyTransactionsAvailable() {
    Set<String> transactions = timeSeriesAnalysisService.getKeyTransactions(cvConfigId);
    assertThat(transactions).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveAndGetMetrics() throws IOException {
    File file = new File("270-verification/src/test/resources/new_relic_metric_records.json");
    final Gson gson = new Gson();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<List<NewRelicMetricDataRecord>>() {}.getType();
      List<NewRelicMetricDataRecord> metricDataRecords = gson.fromJson(br, type);
      metricDataRecords.forEach(metricDataRecord -> {
        metricDataRecord.setAppId(appId);
        metricDataRecord.setAccountId(accountId);
        metricDataRecord.setStateExecutionId(stateExecutionId);
      });
      Collections.shuffle(metricDataRecords);
      timeSeriesAnalysisService.saveMetricData(accountId, appId, stateExecutionId, null, metricDataRecords);
    }

    Set<NewRelicMetricDataRecord> records = timeSeriesAnalysisService.getRecords(appId, stateExecutionId, "default",
        Sets.newHashSet("webapp-deployment-v2-canary-64b946f89c-2pmv9"), 5, 0, accountId);

    AtomicBoolean hasErrors = new AtomicBoolean(false);
    records.forEach(record -> {
      if (record.getValues().containsKey("Errors")) {
        hasErrors.set(true);
      }
    });
    assertThat(hasErrors.get()).isTrue();
  }
}
