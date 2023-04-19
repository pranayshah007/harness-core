/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.service.impl;

import static io.harness.rule.OwnerRule.ROHIT;
import static io.harness.rule.OwnerRule.SHUBHANSHU;
import static io.harness.rule.OwnerRule.TRUNAPUSHPA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.billing.service.UtilizationData;
import io.harness.batch.processing.billing.timeseries.data.InstanceUtilizationData;
import io.harness.batch.processing.ccm.UtilizationInstanceType;
import io.harness.batch.processing.cloudevents.aws.ecs.service.util.ClusterIdAndServiceArn;
import io.harness.batch.processing.cloudevents.aws.ecs.service.util.ECSUtilizationData;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.utils.TimeUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
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
public class UtilizationDataServiceImplTest extends CategoryTest {
  @InjectMocks private UtilizationDataServiceImpl utilizationDataService;
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private PreparedStatement statement;
  @Mock private TimeUtils utils;
  @Mock ResultSet resultSet;

  public static final String SERVICE_ARN = "service_arn";
  public static final String CLUSTER_NAME = "cluster_name";
  private static final String ACCOUNT_ID = "account_id";
  private static final String INSTANCE_ID = "instance_id";
  private static final String CLUSTER_ID = "cluster_id";
  private static final String SERVICE_ID = "service_id";
  private static final String SETTING_ID = "setting_id";
  private static final String APP_ID = "app_id";
  private static final String CLOUD_PROVIDER_ID = "cloud_provider_id";
  private static final String ENV_ID = "env_id";
  private static final String INFRA_MAPPING_ID = "infra_mapping_id";
  private static final String DEPLOYMENT_SUMMARY_ID = "deployment_summary_id";
  private static final Timestamp START_TIME = Timestamp.from(Instant.now().minus(Duration.ofDays(7)));
  private static final Timestamp END_TIME = Timestamp.from(Instant.now());
  private static final double CPU_UTILIZATION = 0.5;
  private static final double MEMORY_UTILIZATION = 0.5;
  final int[] count = {0};

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
    Connection mockConnection = mock(Connection.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(mockConnection.prepareStatement(utilizationDataService.INSERT_STATEMENT)).thenReturn(statement);
    when(utils.getDefaultCalendar()).thenReturn(Calendar.getInstance(TimeZone.getTimeZone("UTC")));
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testCreateInstanceUtilizationData() {
    when(timeScaleDBService.isValid()).thenReturn(true);
    InstanceUtilizationData instanceUtilizationData = instanceUtilizationData();
    boolean insert = utilizationDataService.create(Collections.singletonList(instanceUtilizationData));
    assertThat(insert).isTrue();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testNullCreateInstanceUtilizationData() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(statement.executeBatch()).thenThrow(new SQLException());
    InstanceUtilizationData instanceUtilizationData = instanceUtilizationData();
    boolean insert = utilizationDataService.create(Collections.singletonList(instanceUtilizationData));
    assertThat(insert).isFalse();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testInvalidDBService() {
    when(timeScaleDBService.isValid()).thenReturn(false);
    InstanceUtilizationData instanceUtilizationData = instanceUtilizationData();
    boolean insert = utilizationDataService.create(Collections.singletonList(instanceUtilizationData));
    assertThat(insert).isFalse();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetUtilizationDataForInstances() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(statement.execute()).thenReturn(true);
    mockResultSet();
    Map<String, UtilizationData> utilizationDataMap = utilizationDataService.getUtilizationDataForInstances(
        instanceDataList(), START_TIME.toString(), END_TIME.toString(), ACCOUNT_ID, SETTING_ID, CLUSTER_ID);
    assertThat(utilizationDataMap).isNotNull();
    assertThat(utilizationDataMap.get(INSTANCE_ID)).isNotNull();
    UtilizationData utilizationData = utilizationDataMap.get(INSTANCE_ID);
    assertThat(utilizationData.getMaxCpuUtilization()).isEqualTo(CPU_UTILIZATION);
    assertThat(utilizationData.getMaxMemoryUtilization()).isEqualTo(MEMORY_UTILIZATION);
    assertThat(utilizationData.getAvgCpuUtilization()).isEqualTo(CPU_UTILIZATION);
    assertThat(utilizationData.getAvgMemoryUtilization()).isEqualTo(MEMORY_UTILIZATION);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testGetUtilizationDataForECSClusters() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(statement.execute()).thenReturn(true);
    mockResultSet();
    Map<ClusterIdAndServiceArn, List<ECSUtilizationData>> utilizationMap =
        utilizationDataService.getUtilizationDataForECSClusters(
            ACCOUNT_ID, Collections.singletonList("cluster_id"), START_TIME.toString(), END_TIME.toString());
    ClusterIdAndServiceArn clusterIdAndServiceArn = utilizationMap.entrySet().iterator().next().getKey();
    assertThat(utilizationMap).isNotNull();
    assertThat(utilizationMap.get(clusterIdAndServiceArn)).isNotNull();
    List<ECSUtilizationData> utilizationData = utilizationMap.get(clusterIdAndServiceArn);
    assertThat(utilizationData.get(0).getMaxCpuUtilization()).isEqualTo(CPU_UTILIZATION);
    assertThat(utilizationData.get(0).getMaxMemoryUtilization()).isEqualTo(MEMORY_UTILIZATION);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetUtilizationDataForInstancesWhenDbIsInvalid() {
    when(timeScaleDBService.isValid()).thenReturn(false);
    assertThatThrownBy(()
                           -> utilizationDataService.getUtilizationDataForInstances(instanceDataList(),
                               START_TIME.toString(), END_TIME.toString(), ACCOUNT_ID, SETTING_ID, CLUSTER_ID))
        .isInstanceOf(InvalidRequestException.class);
  }

  private InstanceUtilizationData instanceUtilizationData() {
    return InstanceUtilizationData.builder()
        .instanceId(SERVICE_ARN)
        .clusterId(CLUSTER_ID)
        .instanceType(UtilizationInstanceType.ECS_SERVICE)
        .startTimestamp(1546281000000l)
        .endTimestamp(1546367400000l)
        .cpuUtilizationAvg(40.0)
        .cpuUtilizationMax(65.0)
        .memoryUtilizationAvg(1024.0)
        .memoryUtilizationMax(1650.0)
        .build();
  }

  private List<? extends InstanceData> instanceDataList() {
    Map<String, String> metaDataMap = new HashMap<>();
    metaDataMap.put(InstanceMetaDataConstants.ECS_SERVICE_ARN, SERVICE_ARN);
    InstanceData instanceData = InstanceData.builder()
                                    .instanceType(InstanceType.EC2_INSTANCE)
                                    .metaData(metaDataMap)
                                    .accountId(ACCOUNT_ID)
                                    .instanceId(INSTANCE_ID)
                                    .clusterId(CLUSTER_ID)
                                    .clusterName(CLUSTER_NAME)
                                    .harnessServiceInfo(getHarnessServiceInfo())
                                    .build();
    return Collections.singletonList(instanceData);
  }

  private HarnessServiceInfo getHarnessServiceInfo() {
    return new HarnessServiceInfo(
        SERVICE_ID, APP_ID, CLOUD_PROVIDER_ID, ENV_ID, INFRA_MAPPING_ID, DEPLOYMENT_SUMMARY_ID);
  }

  private void mockResultSet() throws SQLException {
    Connection connection = mock(Connection.class);
    resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);
    when(resultSet.getDouble("MAXCPUUTILIZATION")).thenAnswer((Answer<Double>) invocation -> CPU_UTILIZATION);
    when(resultSet.getDouble("MAXMEMORYUTILIZATION")).thenAnswer((Answer<Double>) invocation -> MEMORY_UTILIZATION);
    when(resultSet.getDouble("AVGCPUUTILIZATION")).thenAnswer((Answer<Double>) invocation -> CPU_UTILIZATION);
    when(resultSet.getDouble("AVGMEMORYUTILIZATION")).thenAnswer((Answer<Double>) invocation -> MEMORY_UTILIZATION);
    when(resultSet.getString("INSTANCEID")).thenAnswer((Answer<String>) invocation -> INSTANCE_ID);
    when(resultSet.getString("CLUSTERID")).thenAnswer((Answer<String>) invocation -> CLUSTER_ID);
    when(resultSet.getString("SERVICEID")).thenAnswer((Answer<String>) invocation -> SERVICE_ID);
    when(resultSet.getTimestamp("STARTTIME")).thenAnswer((Answer<Timestamp>) invocation -> START_TIME);
    when(resultSet.getTimestamp("ENDTIME")).thenAnswer((Answer<Timestamp>) invocation -> END_TIME);
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
