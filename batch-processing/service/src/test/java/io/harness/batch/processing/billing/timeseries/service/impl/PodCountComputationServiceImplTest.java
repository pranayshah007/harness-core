/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.service.impl;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.billing.timeseries.data.InstanceLifecycleInfo;
import io.harness.batch.processing.billing.timeseries.data.NodePodId;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.utils.TimeUtils;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class PodCountComputationServiceImplTest extends CategoryTest {
  @Spy @InjectMocks private PodCountComputationServiceImpl podCountComputationService;
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private PreparedStatement statement;
  @Mock private InstanceDataService instanceDataService;
  @Mock private TimeUtils utils;
  @Mock ResultSet nodeIdResultSet, podDataResultSet;

  final int[] count = {0};
  private final Instant NOW = Instant.now().truncatedTo(ChronoUnit.DAYS);
  private final Instant START_TIME = NOW.minus(1, ChronoUnit.DAYS);
  private final Instant END_TIME = NOW;
  private static final String POD_ID = "podId";
  private static final String NODE_ID = "nodeId";
  private static final String ACCOUNT_ID = "accountId";
  private static final String CLUSTER_ID = "clusterId";

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
    Connection mockConnection = mock(Connection.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.prepareStatement(podCountComputationService.GET_NODE_QUERY)).thenReturn(statement);
    when(mockConnection.prepareStatement(podCountComputationService.GET_PODS_QUERY)).thenReturn(statement);
    when(mockConnection.prepareStatement(podCountComputationService.INSERT_STATEMENT)).thenReturn(statement);
    when(mockConnection.createStatement()).thenReturn(statement);
    when(utils.getDefaultCalendar()).thenReturn(Calendar.getInstance(TimeZone.getTimeZone("UTC")));
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetNodes() throws SQLException {
    nodeDataMockResultSet();
    when(statement.execute()).thenReturn(true);
    when(statement.executeQuery(any())).thenReturn(nodeIdResultSet);
    List<NodePodId> nodes = podCountComputationService.getNodes(ACCOUNT_ID, START_TIME, END_TIME);
    NodePodId nodePodId = nodes.get(0);
    assertThat(nodePodId.getClusterId()).isEqualTo("CLUSTERID");
    assertThat(nodePodId.getNodeId()).isEqualTo("INSTANCEID");
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetPods() throws SQLException {
    podDataMockResultSet();
    when(statement.execute()).thenReturn(true);
    when(statement.executeQuery(any())).thenReturn(podDataResultSet);
    NodePodId pods = podCountComputationService.getPods(ACCOUNT_ID, CLUSTER_ID, NODE_ID, START_TIME, END_TIME);
    assertThat(pods.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(pods.getNodeId()).isEqualTo(NODE_ID);
    assertThat(pods.getPodId()).containsExactly(POD_ID);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testComputePodCountForNodes() throws SQLException {
    Set<String> podId = new HashSet<>();
    podId.add(POD_ID);
    NodePodId nodePodId = NodePodId.builder().nodeId(NODE_ID).clusterId(CLUSTER_ID).podId(podId).build();
    when(instanceDataService.fetchInstanceDataForGivenInstances(ACCOUNT_ID, podId))
        .thenReturn(getInstanceLifeCycleInfo());
    boolean verified = podCountComputationService.computePodCountForNodes(
        ACCOUNT_ID, START_TIME.toEpochMilli(), END_TIME.toEpochMilli(), nodePodId);
    assertThat(verified).isTrue();
  }

  private List<InstanceLifecycleInfo> getInstanceLifeCycleInfo() {
    InstanceLifecycleInfo instanceLifecycleInfo = InstanceLifecycleInfo.builder()
                                                      .instanceId(POD_ID)
                                                      .usageStartTime(START_TIME.plus(6, ChronoUnit.HOURS))
                                                      .usageStopTime(END_TIME.minus(6, ChronoUnit.HOURS))
                                                      .build();
    return Arrays.asList(instanceLifecycleInfo);
  }

  private void nodeDataMockResultSet() throws SQLException {
    when(nodeIdResultSet.getString("INSTANCEID")).thenAnswer((Answer<String>) invocation -> "INSTANCEID");
    when(nodeIdResultSet.getString("CLUSTERID")).thenAnswer((Answer<String>) invocation -> "CLUSTERID");
    returnResultSet(1, nodeIdResultSet);
  }

  private void podDataMockResultSet() throws SQLException {
    when(podDataResultSet.getString("PODID")).thenAnswer((Answer<String>) invocation -> POD_ID);
    returnResultSet(1, podDataResultSet);
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
