/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.service.impl;

import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.ROHIT;
import static io.harness.rule.OwnerRule.SHUBHANSHU;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.billing.timeseries.data.InstanceBillingData;
import io.harness.batch.processing.billing.timeseries.service.support.BillingDataTableNameProvider;
import io.harness.batch.processing.ccm.ActualIdleCostWriterData;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.utils.TimeUtils;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import jersey.repackaged.com.google.common.collect.ImmutableList;
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
public class BillingDataServiceImplTest extends CategoryTest {
  @InjectMocks private BillingDataServiceImpl billingDataService;
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private PreparedStatement statement;
  @Mock private TimeUtils utils;
  @Mock private ResultSet resultSet;

  private final Instant NOW = Instant.now();
  private final long START_TIME_MILLIS = NOW.minus(1, ChronoUnit.HOURS).toEpochMilli();
  private final long END_TIME_MILLIS = NOW.toEpochMilli();
  private static final String ACCOUNT_ID = "accountId";
  private static final String CLUSTER_ID = "clusterId";
  private static final String PARENT_INSTANCE_ID = "parentInstanceId";

  final int[] count = {0};

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
    Connection mockConnection = mock(Connection.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.prepareStatement(BillingDataTableNameProvider.replaceTableName(
             billingDataService.INSERT_STATEMENT, BatchJobType.INSTANCE_BILLING)))
        .thenReturn(statement);
    when(mockConnection.prepareStatement(BillingDataTableNameProvider.replaceTableName(
             billingDataService.INSERT_STATEMENT, BatchJobType.INSTANCE_BILLING_HOURLY)))
        .thenReturn(statement);
    when(mockConnection.prepareStatement(
             BillingDataTableNameProvider.replaceTableName(
                 billingDataService.PREAGG_QUERY_PREFIX, BatchJobType.INSTANCE_BILLING_AGGREGATION)
             + BillingDataTableNameProvider.replaceTableName(
                 billingDataService.PREAGG_QUERY_SUFFIX, BatchJobType.INSTANCE_BILLING)))
        .thenReturn(statement);
    when(mockConnection.prepareStatement(BillingDataTableNameProvider.replaceTableName(
             billingDataService.DELETE_EXISTING_PREAGG, BatchJobType.INSTANCE_BILLING_AGGREGATION)))
        .thenReturn(statement);

    when(mockConnection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(any())).thenReturn(resultSet);
    when(utils.getDefaultCalendar()).thenReturn(Calendar.getInstance(TimeZone.getTimeZone("UTC")));
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testCreateBillingData() throws SQLException {
    when(statement.execute()).thenReturn(true);
    InstanceBillingData instanceBillingData = instanceBillingData();
    boolean insert = billingDataService.create(ImmutableList.of(instanceBillingData), BatchJobType.INSTANCE_BILLING);
    assertThat(insert).isTrue();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testUpdateBillingData() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.prepareStatement(BillingDataTableNameProvider.replaceTableName(
             billingDataService.UPDATE_STATEMENT, BatchJobType.ACTUAL_IDLE_COST_BILLING)))
        .thenReturn(statement);
    when(statement.execute()).thenReturn(true);
    ActualIdleCostWriterData actualIdleCostWriterData = mockActualIdleCostWriterData();
    boolean update = billingDataService.update(actualIdleCostWriterData, BatchJobType.ACTUAL_IDLE_COST_BILLING);
    assertThat(update).isTrue();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testSqlExceptionUpdateBillingData() throws SQLException {
    when(timeScaleDBService.getDBConnection()).thenThrow(SQLException.class);
    ActualIdleCostWriterData actualIdleCostWriterData = mockActualIdleCostWriterData();
    assertThat(billingDataService.update(actualIdleCostWriterData, BatchJobType.ACTUAL_IDLE_COST_BILLING_HOURLY))
        .isEqualTo(false);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testTimescaleDbInvalidUpdateBillingData() {
    when(timeScaleDBService.isValid()).thenReturn(false);
    ActualIdleCostWriterData actualIdleCostWriterData = mockActualIdleCostWriterData();
    assertThat(billingDataService.update(actualIdleCostWriterData, BatchJobType.ACTUAL_IDLE_COST_BILLING))
        .isEqualTo(false);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testNullCreateBillingData() throws SQLException {
    when(statement.executeBatch()).thenThrow(new SQLException());
    InstanceBillingData instanceBillingData = instanceBillingData();
    boolean insert =
        billingDataService.create(ImmutableList.of(instanceBillingData), BatchJobType.INSTANCE_BILLING_HOURLY);
    assertThat(insert).isFalse();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testPurgeOldHourlyBillingData() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    boolean insert = billingDataService.purgeOldHourlyBillingData(BatchJobType.INSTANCE_BILLING_HOURLY);
    assertThat(insert).isTrue();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void readBillingData() throws SQLException {
    mockResultSet();
    List<InstanceBillingData> instanceBillingData =
        billingDataService.read(ACCOUNT_ID, Instant.ofEpochMilli(START_TIME_MILLIS),
            Instant.ofEpochMilli(END_TIME_MILLIS), 500, 0, BatchJobType.CLUSTER_DATA_TO_BIG_QUERY);
    assertThat(instanceBillingData.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGeneratePreAggBillingDataDBUnavailable() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(false);
    boolean result = billingDataService.generatePreAggBillingData(ACCOUNT_ID, Instant.ofEpochMilli(START_TIME_MILLIS),
        Instant.ofEpochMilli(END_TIME_MILLIS), BatchJobType.INSTANCE_BILLING_HOURLY_AGGREGATION,
        BatchJobType.INSTANCE_BILLING_HOURLY);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGeneratePreAggBillingData() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    boolean result = billingDataService.generatePreAggBillingData(ACCOUNT_ID, Instant.ofEpochMilli(START_TIME_MILLIS),
        Instant.ofEpochMilli(END_TIME_MILLIS), BatchJobType.INSTANCE_BILLING_AGGREGATION,
        BatchJobType.INSTANCE_BILLING);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testCleanPreAggBillingDataWithError() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(false);
    boolean result = billingDataService.cleanPreAggBillingData(ACCOUNT_ID, Instant.ofEpochMilli(START_TIME_MILLIS),
        Instant.ofEpochMilli(END_TIME_MILLIS), BatchJobType.INSTANCE_BILLING_AGGREGATION);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testCleanPreAggBillingData() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    boolean result = billingDataService.cleanPreAggBillingData(ACCOUNT_ID, Instant.ofEpochMilli(START_TIME_MILLIS),
        Instant.ofEpochMilli(END_TIME_MILLIS), BatchJobType.INSTANCE_BILLING_AGGREGATION);
    assertThat(result).isTrue();
  }

  private void mockResultSet() throws SQLException {
    when(resultSet.getDouble(anyString())).thenAnswer((Answer<Double>) invocation -> Double.valueOf(10));
    when(resultSet.getString(anyString())).thenAnswer((Answer<String>) invocation -> "stringValue");
    when(resultSet.getBigDecimal(anyString())).thenAnswer((Answer<BigDecimal>) invocation -> BigDecimal.TEN);
    when(resultSet.getTimestamp(anyString()))
        .thenAnswer((Answer<Timestamp>) invocation -> Timestamp.from(Instant.now()));
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

  private InstanceBillingData instanceBillingData() {
    return InstanceBillingData.builder()
        .startTimestamp(1546281000000l)
        .endTimestamp(1546367400000l)
        .accountId("ACCOUNT_ID")
        .instanceType(InstanceType.EC2_INSTANCE.name())
        .cpuUnitSeconds(1024)
        .memoryMbSeconds(1024)
        .usageDurationSeconds(3600)
        .build();
  }

  private ActualIdleCostWriterData mockActualIdleCostWriterData() {
    return ActualIdleCostWriterData.builder()
        .accountId(ACCOUNT_ID)
        .clusterId(CLUSTER_ID)
        .parentInstanceId(PARENT_INSTANCE_ID)
        .actualIdleCost(BigDecimal.valueOf(8))
        .cpuActualIdleCost(BigDecimal.valueOf(4))
        .memoryActualIdleCost(BigDecimal.valueOf(4))
        .unallocatedCost(BigDecimal.valueOf(8))
        .cpuUnallocatedCost(BigDecimal.valueOf(4))
        .memoryUnallocatedCost(BigDecimal.valueOf(4))
        .startTime(START_TIME_MILLIS)
        .endTime(END_TIME_MILLIS)
        .build();
  }
}
