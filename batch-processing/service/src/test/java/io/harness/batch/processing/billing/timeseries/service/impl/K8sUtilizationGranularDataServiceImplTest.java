/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.service.impl;

import static io.harness.rule.OwnerRule.ROHIT;
import static io.harness.rule.OwnerRule.UTSAV;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.billing.timeseries.data.InstanceUtilizationData;
import io.harness.batch.processing.billing.timeseries.data.K8sGranularUtilizationData;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.utils.TimeUtils;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class K8sUtilizationGranularDataServiceImplTest extends CategoryTest {
  @Inject @InjectMocks private K8sUtilizationGranularDataServiceImpl k8sUtilizationGranularDataService;
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private PreparedStatement statement;
  @Mock(answer = RETURNS_DEEP_STUBS) private DSLContext dslContext;
  @Mock private TimeUtils utils;
  @Mock ResultSet instanceIdsResultSet, aggregatedDataResultSet;

  private final Instant NOW = Instant.now();
  private final long START_DATE = NOW.minus(1, ChronoUnit.HOURS).toEpochMilli();
  private final long END_DATE = NOW.toEpochMilli();
  final int[] count = {0};
  private final String ACCOUNTID = "ACCOUNTID" + this.getClass().getSimpleName();

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
    Connection mockConnection = mock(Connection.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(mockConnection.prepareStatement(k8sUtilizationGranularDataService.INSERT_STATEMENT)).thenReturn(statement);
    when(mockConnection.prepareStatement(k8sUtilizationGranularDataService.UTILIZATION_DATA_QUERY))
        .thenReturn(statement);
    when(mockConnection.prepareStatement(k8sUtilizationGranularDataService.SELECT_DISTINCT_INSTANCEID))
        .thenReturn(statement);
    when(mockConnection.prepareStatement(k8sUtilizationGranularDataService.PURGE_DATA_QUERY)).thenReturn(statement);
    when(mockConnection.createStatement()).thenReturn(statement);
    when(utils.getDefaultCalendar()).thenReturn(Calendar.getInstance(TimeZone.getTimeZone("UTC")));
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetDistinctInstantIds() throws SQLException {
    instanceIdsMockResultSet();
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(statement.executeQuery(any())).thenReturn(instanceIdsResultSet);
    List<String> distinctIds = k8sUtilizationGranularDataService.getDistinctInstantIds(ACCOUNTID, START_DATE, END_DATE);
    assertThat(distinctIds.get(0)).isEqualTo("INSTANCEID");
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetDistinctInstantIdsInvalidDBService() throws SQLException {
    when(timeScaleDBService.getDBConnection()).thenThrow(SQLException.class);
    assertThat(k8sUtilizationGranularDataService.getDistinctInstantIds(ACCOUNTID, START_DATE, END_DATE)).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetAggregatedUtilizationData() throws SQLException {
    aggregatedUtilizationDataMockResultSet();
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(statement.executeQuery(any())).thenReturn(aggregatedDataResultSet);
    Map<String, InstanceUtilizationData> instanceUtilizationDataMap =
        k8sUtilizationGranularDataService.getAggregatedUtilizationData(
            ACCOUNTID, singletonList("INSTANCEID"), START_DATE, END_DATE);
    assertThat(instanceUtilizationDataMap.get("INSTANCEID").getClusterId()).isEqualTo("CLUSTERID");
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetAggregatedUtilizationDataInvalidDBService() throws SQLException {
    when(timeScaleDBService.getDBConnection()).thenThrow(SQLException.class);
    assertThat(k8sUtilizationGranularDataService.getAggregatedUtilizationData(
                   ACCOUNTID, singletonList("instance"), START_DATE, END_DATE))
        .isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testCreateK8sGranularUtilizationData() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    K8sGranularUtilizationData k8sGranularUtilizationData = K8sGranularUtilizationData();
    boolean insert = k8sUtilizationGranularDataService.create(singletonList(k8sGranularUtilizationData));
    assertThat(insert).isTrue();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testPurgeK8sGranularUtilizationData() throws SQLException {
    when(dslContext.query(anyString()).execute()).thenReturn(10);

    int count = k8sUtilizationGranularDataService.purgeOldKubernetesUtilData();
    assertThat(count).isEqualTo(10);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testNullCreateK8sGranularUtilizationData() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(statement.executeBatch()).thenThrow(new SQLException());
    K8sGranularUtilizationData k8sGranularUtilizationData = K8sGranularUtilizationData();
    boolean insert = k8sUtilizationGranularDataService.create(singletonList(k8sGranularUtilizationData));
    assertThat(insert).isFalse();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testInvalidDBService() {
    when(timeScaleDBService.isValid()).thenReturn(false);
    K8sGranularUtilizationData k8sGranularUtilizationData = K8sGranularUtilizationData();
    boolean insert = k8sUtilizationGranularDataService.create(singletonList(k8sGranularUtilizationData));
    assertThat(insert).isFalse();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetAggregatedUtilizationDataOfType() throws Exception {
    Map<String, InstanceUtilizationData> utilizationDataMap =
        k8sUtilizationGranularDataService.getAggregatedUtilizationDataOfType(
            ACCOUNTID, InstanceType.K8S_PVC, Instant.now().toEpochMilli(), Instant.now().toEpochMilli());

    assertThat(utilizationDataMap).isNotNull().isEmpty();
  }

  private K8sGranularUtilizationData K8sGranularUtilizationData() {
    return K8sGranularUtilizationData.builder()
        .clusterId("clusterId")
        .instanceType("instanceType")
        .instanceId("instanceId")
        .memory(2)
        .cpu(2)
        .endTimestamp(12000000000L)
        .startTimestamp(10000000000L)
        .build();
  }

  private void instanceIdsMockResultSet() throws SQLException {
    when(instanceIdsResultSet.getString("INSTANCEID")).thenAnswer((Answer<String>) invocation -> "INSTANCEID");
    returnResultSet(1, instanceIdsResultSet);
  }

  private void aggregatedUtilizationDataMockResultSet() throws SQLException {
    when(aggregatedDataResultSet.getString("INSTANCEID")).thenAnswer((Answer<String>) invocation -> "INSTANCEID");
    when(aggregatedDataResultSet.getString("ACCOUNTID")).thenAnswer((Answer<String>) invocation -> "ACCOUNTID");
    when(aggregatedDataResultSet.getString("INSTANCETYPE")).thenAnswer((Answer<String>) invocation -> "INSTANCETYPE");
    when(aggregatedDataResultSet.getString("CLUSTERID")).thenAnswer((Answer<String>) invocation -> "CLUSTERID");
    when(aggregatedDataResultSet.getString("CPUUTILIZATIONMAX"))
        .thenAnswer((Answer<String>) invocation -> "CPUUTILIZATIONMAX");
    when(aggregatedDataResultSet.getString("MEMORYUTILIZATIONMAX"))
        .thenAnswer((Answer<String>) invocation -> "MEMORYUTILIZATIONMAX");
    when(aggregatedDataResultSet.getString("CPUUTILIZATIONAVG"))
        .thenAnswer((Answer<String>) invocation -> "CPUUTILIZATIONAVG");
    when(aggregatedDataResultSet.getString("MEMORYUTILIZATIONAVG"))
        .thenAnswer((Answer<String>) invocation -> "MEMORYUTILIZATIONAVG");

    returnResultSet(1, aggregatedDataResultSet);
  }

  private void returnResultSet(int limit, ResultSet resultSet) throws SQLException {
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] < limit) {
        count[0]++;
        return true;
      }
      count[0] = 0;
      return false;
    });
  }
}
