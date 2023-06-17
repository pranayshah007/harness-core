/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.service.impl;

import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.ccm.ActualIdleCostData;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ActualIdleBillingDataServiceImplTest extends CategoryTest {
  @InjectMocks private ActualIdleBillingDataServiceImpl actualIdleBillingDataService;
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private PreparedStatement statement;
  @Mock ResultSet resultSet;
  final int[] count = {0};

  private final Instant NOW = Instant.now();
  private static final String ACCOUNT_ID = "accountId";
  private static final String CLUSTER_ID = "clusterId";
  private static final String INSTANCE_ID = "instanceId";
  private static final String PARENT_INSTANCE_ID = "parentInstanceId";
  private static final double COST = 1.6;
  private static final double CPU_COST = 0.8;
  private static final double MEMORY_COST = 0.8;
  private static final double IDLE_COST = 0.8;
  private static final double CPU_IDLE_COST = 0.4;
  private static final double MEMORY_IDLE_COST = 0.4;
  private static final double SYSTEM_COST = 0.4;
  private static final double CPU_SYSTEM_COST = 0.2;
  private static final double MEMORY_SYSTEM_COST = 0.2;

  private final long START_TIME = NOW.minus(1, ChronoUnit.HOURS).toEpochMilli();
  private final long END_TIME = NOW.toEpochMilli();

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
    Connection mockConnection = mock(Connection.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(mockConnection.prepareStatement(actualIdleBillingDataService.GET_UNALLOCATED_AND_IDLE_COST_DATA_FOR_NODES))
        .thenReturn(statement);
    mockResultSet();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testActualIdleCostDataServiceForNodes() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(statement.execute()).thenReturn(true);
    List<ActualIdleCostData> actualIdleCostDataList = actualIdleBillingDataService.getActualIdleCostDataForNodes(
        ACCOUNT_ID, START_TIME, END_TIME, BatchJobType.ACTUAL_IDLE_COST_BILLING_HOURLY);
    assertThat(actualIdleCostDataList.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(actualIdleCostDataList.get(0).getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(actualIdleCostDataList.get(0).getInstanceId()).isEqualTo(INSTANCE_ID);
    assertThat(actualIdleCostDataList.get(0).getParentInstanceId()).isEqualTo("PARENT_INSTANCE_ID");
    assertThat(actualIdleCostDataList.get(0).getCost()).isEqualTo(COST);
    assertThat(actualIdleCostDataList.get(0).getCpuCost()).isEqualTo(CPU_COST);
    assertThat(actualIdleCostDataList.get(0).getMemoryCost()).isEqualTo(MEMORY_COST);
    assertThat(actualIdleCostDataList.get(0).getIdleCost()).isEqualTo(IDLE_COST);
    assertThat(actualIdleCostDataList.get(0).getCpuIdleCost()).isEqualTo(CPU_IDLE_COST);
    assertThat(actualIdleCostDataList.get(0).getMemoryIdleCost()).isEqualTo(MEMORY_IDLE_COST);
    assertThat(actualIdleCostDataList.get(0).getSystemCost()).isEqualTo(SYSTEM_COST);
    assertThat(actualIdleCostDataList.get(0).getCpuSystemCost()).isEqualTo(CPU_SYSTEM_COST);
    assertThat(actualIdleCostDataList.get(0).getMemorySystemCost()).isEqualTo(MEMORY_SYSTEM_COST);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testNullActualIdleCostDataServiceForNodes() throws SQLException {
    when(timeScaleDBService.getDBConnection()).thenThrow(SQLException.class);
    assertThat(actualIdleBillingDataService.getActualIdleCostDataForNodes(
                   ACCOUNT_ID, START_TIME, END_TIME, BatchJobType.ACTUAL_IDLE_COST_BILLING_HOURLY))
        .isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testActualIdleCostDataServiceForPods() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(statement.execute()).thenReturn(true);
    List<ActualIdleCostData> actualIdleCostDataList = actualIdleBillingDataService.getActualIdleCostDataForPods(
        ACCOUNT_ID, START_TIME, END_TIME, BatchJobType.ACTUAL_IDLE_COST_BILLING_HOURLY);
    assertThat(actualIdleCostDataList.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(actualIdleCostDataList.get(0).getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(actualIdleCostDataList.get(0).getInstanceId()).isEqualTo(null);
    assertThat(actualIdleCostDataList.get(0).getParentInstanceId()).isEqualTo(PARENT_INSTANCE_ID);
    assertThat(actualIdleCostDataList.get(0).getCost()).isEqualTo(COST);
    assertThat(actualIdleCostDataList.get(0).getCpuCost()).isEqualTo(CPU_COST);
    assertThat(actualIdleCostDataList.get(0).getMemoryCost()).isEqualTo(MEMORY_COST);
    assertThat(actualIdleCostDataList.get(0).getIdleCost()).isEqualTo(IDLE_COST);
    assertThat(actualIdleCostDataList.get(0).getCpuIdleCost()).isEqualTo(CPU_IDLE_COST);
    assertThat(actualIdleCostDataList.get(0).getMemoryIdleCost()).isEqualTo(MEMORY_IDLE_COST);
    assertThat(actualIdleCostDataList.get(0).getSystemCost()).isEqualTo(SYSTEM_COST);
    assertThat(actualIdleCostDataList.get(0).getCpuSystemCost()).isEqualTo(CPU_SYSTEM_COST);
    assertThat(actualIdleCostDataList.get(0).getMemorySystemCost()).isEqualTo(MEMORY_SYSTEM_COST);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testNullActualIdleCostDataServiceForPods() throws SQLException {
    when(timeScaleDBService.getDBConnection()).thenThrow(SQLException.class);
    assertThat(actualIdleBillingDataService.getActualIdleCostDataForPods(
                   ACCOUNT_ID, START_TIME, END_TIME, BatchJobType.ACTUAL_IDLE_COST_BILLING_HOURLY))
        .isEqualTo(Collections.emptyList());
  }

  private void mockResultSet() throws SQLException {
    Connection connection = mock(Connection.class);
    resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);
    when(resultSet.getString("ACCOUNTID")).thenAnswer((Answer<String>) invocation -> ACCOUNT_ID);
    when(resultSet.getString("CLUSTERID")).thenAnswer((Answer<String>) invocation -> CLUSTER_ID);
    when(resultSet.getString("INSTANCEID")).thenAnswer((Answer<String>) invocation -> INSTANCE_ID);
    when(resultSet.getString("PARENTINSTANCEID")).thenAnswer((Answer<String>) invocation -> PARENT_INSTANCE_ID);
    when(resultSet.getDouble("COST")).thenAnswer((Answer<Double>) invocation -> COST);
    when(resultSet.getDouble("CPUCOST")).thenAnswer((Answer<Double>) invocation -> CPU_COST);
    when(resultSet.getDouble("MEMORYCOST")).thenAnswer((Answer<Double>) invocation -> MEMORY_COST);
    when(resultSet.getDouble("SYSTEMCOST")).thenAnswer((Answer<Double>) invocation -> SYSTEM_COST);
    when(resultSet.getDouble("CPUSYSTEMCOST")).thenAnswer((Answer<Double>) invocation -> CPU_SYSTEM_COST);
    when(resultSet.getDouble("MEMORYSYSTEMCOST")).thenAnswer((Answer<Double>) invocation -> MEMORY_SYSTEM_COST);
    when(resultSet.getDouble("IDLECOST")).thenAnswer((Answer<Double>) invocation -> IDLE_COST);
    when(resultSet.getDouble("CPUIDLECOST")).thenAnswer((Answer<Double>) invocation -> CPU_IDLE_COST);
    when(resultSet.getDouble("MEMORYIDLECOST")).thenAnswer((Answer<Double>) invocation -> MEMORY_IDLE_COST);
    returnResultSet(1);
  }

  private void returnResultSet(int limit) throws SQLException {
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] < limit) {
        count[0]++;
        return true;
      }
      return false;
    });
  }
}
