/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.timeseries.processor;

import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.ORIGINAL_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.WingsBaseTest;
import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;
import software.wings.utils.WingsTestConstants;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

/**
 * @author rktummala
 */
public class DeploymentEventProcessorTest extends WingsBaseTest {
  @Mock TimeScaleDBService timeScaleService;
  @Spy @Inject @InjectMocks DeploymentEventProcessor deploymentEventProcessor;

  private Connection dbConnection = mock(Connection.class);
  private PreparedStatement preparedStatement = mock(PreparedStatement.class);

  @Before
  public void setup() throws SQLException {
    when(timeScaleService.isValid()).thenReturn(true);
    when(timeScaleService.getDBConnection()).thenReturn(dbConnection);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldListExecutions() throws SQLException {
    Map<String, String> stringData = new HashMap<>();
    populateStringData(stringData);

    Map<String, Long> longData = new HashMap<>();
    populateLongData(123L, 233L, 244L, 33L, longData);

    Map<String, Integer> integerData = new HashMap<>();
    populateIntegerData(1, integerData);

    Map<String, List<String>> listData = new HashMap<>();
    populateListData(listData);

    Map<String, Boolean> booleanData = new HashMap<>();
    populateBooleanData(booleanData);

    Map<String, Object> objectData = new HashMap<>();
    populateObjectData(objectData);

    TimeSeriesEventInfo timeSeriesEventInfo = TimeSeriesEventInfo.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .longData(longData)
                                                  .integerData(integerData)
                                                  .stringData(stringData)
                                                  .listData(listData)
                                                  .booleanData(booleanData)
                                                  .data(objectData)
                                                  .build();

    when(dbConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
    deploymentEventProcessor.processEvent(timeSeriesEventInfo);
    verify(preparedStatement).execute();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldThrowSQLException() throws SQLException {
    TimeSeriesEventInfo timeSeriesEventInfo = TimeSeriesEventInfo.builder().build();
    when(dbConnection.prepareStatement(anyString())).thenThrow(new SQLException());

    deploymentEventProcessor.processEvent(timeSeriesEventInfo);
    verify(dbConnection, times(5)).prepareStatement(anyString());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldProcessRunningEvent() throws SQLException {
    Map<String, String> stringData = new HashMap<>();
    populateStringData(stringData);

    Map<String, Long> longData = new HashMap<>();
    populateLongData(null, 233L, null, null, longData);

    Map<String, Integer> integerData = new HashMap<>();
    populateIntegerData(null, integerData);

    Map<String, List<String>> listData = new HashMap<>();
    populateListData(listData);

    Map<String, Boolean> booleanData = new HashMap<>();
    populateBooleanData(booleanData);

    Map<String, Object> objectData = new HashMap<>();
    populateObjectData(objectData);

    TimeSeriesEventInfo timeSeriesEventInfo = TimeSeriesEventInfo.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .longData(longData)
                                                  .integerData(integerData)
                                                  .stringData(stringData)
                                                  .listData(listData)
                                                  .booleanData(booleanData)
                                                  .data(objectData)
                                                  .build();

    when(dbConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
    deploymentEventProcessor.processEvent(timeSeriesEventInfo);
    verify(preparedStatement).execute();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldProcessEventWithoutListData() throws SQLException {
    Map<String, String> stringData = new HashMap<>();
    populateStringData(stringData);

    Map<String, Long> longData = new HashMap<>();
    populateLongData(123L, 233L, 244L, 33L, longData);

    Map<String, Integer> integerData = new HashMap<>();
    populateIntegerData(1, integerData);

    Map<String, List<String>> listData = null;
    populateListData(listData);

    Map<String, Boolean> booleanData = new HashMap<>();
    populateBooleanData(booleanData);

    Map<String, Object> objectData = new HashMap<>();
    populateObjectData(objectData);

    TimeSeriesEventInfo timeSeriesEventInfo = TimeSeriesEventInfo.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .longData(longData)
                                                  .integerData(integerData)
                                                  .stringData(stringData)
                                                  .listData(listData)
                                                  .booleanData(booleanData)
                                                  .data(objectData)
                                                  .build();

    when(dbConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
    deploymentEventProcessor.processEvent(timeSeriesEventInfo);
    verify(preparedStatement).execute();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_batchIntervalMigrationQuery() throws SQLException {
    when(dbConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
    ResultSet resultSet = mock(ResultSet.class);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);

    List<Map<String, Object>> eventInfoList = new ArrayList<>();
    Map<String, Object> eventInfo = new HashMap<>();
    eventInfo.put(EventProcessor.EXECUTIONID, "uuid");
    eventInfo.put(EventProcessor.STARTTIME, 100000025L);
    eventInfo.put(EventProcessor.ENDTIME, 100001120L);
    eventInfo.put(EventProcessor.ACCOUNTID, "accountId");
    eventInfo.put(EventProcessor.APPID, "appId");
    eventInfo.put(EventProcessor.TRIGGERED_BY, null);
    eventInfo.put(EventProcessor.TRIGGER_ID, null);
    eventInfo.put(EventProcessor.STATUS, "SUCCESS");
    eventInfo.put(EventProcessor.SERVICE_LIST, null);
    eventInfo.put(EventProcessor.WORKFLOW_LIST, null);
    eventInfo.put(EventProcessor.CLOUD_PROVIDER_LIST, null);
    eventInfo.put(EventProcessor.ENV_LIST, null);
    eventInfo.put(EventProcessor.PIPELINE, null);
    eventInfo.put(EventProcessor.DURATION, 1095L);
    eventInfo.put(EventProcessor.ARTIFACT_LIST, null);
    eventInfo.put(EventProcessor.ENVTYPES, null);
    eventInfo.put(EventProcessor.PARENT_EXECUTION, null);
    eventInfo.put(EventProcessor.FAILURE_DETAILS, null);
    eventInfo.put(EventProcessor.FAILED_STEP_NAMES, null);
    eventInfo.put(EventProcessor.FAILED_STEP_TYPES, null);
    eventInfo.put(EventProcessor.STAGENAME, null);
    eventInfo.put(EventProcessor.ROLLBACK_DURATION, 0L);
    eventInfo.put(EventProcessor.ON_DEMAND_ROLLBACK, false);
    eventInfo.put(EventProcessor.ORIGINAL_EXECUTION_ID, null);
    eventInfo.put(EventProcessor.MANUALLY_ROLLED_BACK, false);
    eventInfo.put(EventProcessor.INSTANCES_DEPLOYED, 1);
    eventInfo.put(EventProcessor.TAGS, null);
    eventInfo.put(EventProcessor.PARENT_PIPELINE_ID, null);
    eventInfo.put(EventProcessor.CREATED_BY_TYPE, null);
    eventInfoList.add(eventInfo);

    doReturn(eventInfoList).when(deploymentEventProcessor).parseFetchResults(resultSet);
    deploymentEventProcessor.handleBatchIntervalMigration("accountId", 100000024L, 100001124L, 1, 1);
    verify(preparedStatement).executeQuery();
    verify(preparedStatement).setString(29, null);
    verify(preparedStatement, times(2)).execute();
  }

  // Inner methods
  private void populateStringData(Map<String, String> stringData) {
    stringData.put(EventProcessor.ACCOUNTID, ACCOUNT_ID);
    stringData.put(EventProcessor.EXECUTIONID, WORKFLOW_EXECUTION_ID);
    stringData.put(EventProcessor.APPID, APP_ID);
    stringData.put(EventProcessor.TRIGGERED_BY, WingsTestConstants.USER_ID);
    stringData.put(EventProcessor.TRIGGER_ID, WingsTestConstants.TRIGGER_ID);
    stringData.put(EventProcessor.STATUS, ExecutionStatus.SUCCESS.name());
    stringData.put(EventProcessor.PARENT_EXECUTION, WingsTestConstants.PIPELINE_WORKFLOW_EXECUTION_ID);
    stringData.put(EventProcessor.STAGENAME, WingsTestConstants.WORKFLOW_NAME);
    stringData.put(EventProcessor.PIPELINE, WingsTestConstants.PIPELINE_NAME);
    stringData.put(EventProcessor.ORIGINAL_EXECUTION_ID, ORIGINAL_EXECUTION_ID);
  }

  private void populateLongData(
      Long rollbackDuration, Long startTime, Long endTime, Long duration, Map<String, Long> longData) {
    longData.put(EventProcessor.ROLLBACK_DURATION, rollbackDuration);
    longData.put(EventProcessor.STARTTIME, startTime);
    longData.put(EventProcessor.ENDTIME, endTime);
    longData.put(EventProcessor.DURATION, duration);
  }

  private void populateIntegerData(Integer deployedCount, Map<String, Integer> integerData) {
    integerData.put(EventProcessor.INSTANCES_DEPLOYED, deployedCount);
  }

  private void populateListData(Map<String, List<String>> listData) {
    if (listData != null) {
      listData.put(EventProcessor.SERVICE_LIST, asList(SERVICE_ID));
      listData.put(EventProcessor.WORKFLOW_LIST, asList(WORKFLOW_ID));
      listData.put(EventProcessor.CLOUD_PROVIDER_LIST, asList(WingsTestConstants.COMPUTE_PROVIDER_ID));
      listData.put(EventProcessor.ENV_LIST, asList(ENV_ID));
      listData.put(EventProcessor.ARTIFACT_LIST, asList(ARTIFACT_ID));
      listData.put(EventProcessor.ENVTYPES, asList(EnvironmentType.PROD.name()));
      listData.put(EventProcessor.INFRA_DEFINITIONS, asList(INFRA_DEFINITION_ID));
      listData.put(EventProcessor.INFRA_MAPPINGS, asList(INFRA_MAPPING_ID));
    }
  }

  private void populateBooleanData(Map<String, Boolean> booleanData) {
    if (booleanData != null) {
      booleanData.put(EventProcessor.ON_DEMAND_ROLLBACK, false);
      booleanData.put(EventProcessor.MANUALLY_ROLLED_BACK, false);
    }
  }

  private void populateObjectData(Map<String, Object> objectData) {
    Map<String, String> tags = new HashMap<>();
    tags.put("env", "QA");
    tags.put("description", "this is a really really long description");
    objectData.put(EventProcessor.TAGS, tags);
  }
}
