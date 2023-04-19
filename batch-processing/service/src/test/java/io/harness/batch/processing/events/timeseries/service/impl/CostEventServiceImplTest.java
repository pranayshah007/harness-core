/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.events.timeseries.service.impl;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.events.timeseries.data.CostEventData;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.utils.TimeUtils;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CostEventServiceImplTest extends CategoryTest {
  @InjectMocks private CostEventServiceImpl costEventService;
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private PreparedStatement statement;
  @Mock private TimeUtils utils;

  @Before
  public void setup() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.prepareStatement(CostEventServiceImpl.INSERT_STATEMENT)).thenReturn(statement);
    when(mockConnection.prepareStatement(CostEventServiceImpl.UPDATE_DEPLOYMENT_STATEMENT)).thenReturn(statement);
    when(utils.getDefaultCalendar()).thenReturn(Calendar.getInstance(TimeZone.getTimeZone("UTC")));
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testCostEventData() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    boolean insert = costEventService.create(Collections.singletonList(costEventData()));
    assertThat(insert).isTrue();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testUpdateDeploymentEventData() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    boolean update = costEventService.updateDeploymentEvent(costEventData());
    assertThat(update).isTrue();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testUpdateDeploymentEventDataWhenException() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(statement.executeBatch()).thenThrow(new SQLException());
    boolean update = costEventService.updateDeploymentEvent(costEventData());
    assertThat(update).isTrue();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testNullCostEventData() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(statement.executeBatch()).thenThrow(new SQLException());
    boolean insert = costEventService.create(Collections.singletonList(costEventData()));
    assertThat(insert).isFalse();
  }

  private CostEventData costEventData() {
    return CostEventData.builder()
        .startTimestamp(1546281000000l)
        .accountId("ACCOUNT_ID")
        .instanceType(InstanceType.EC2_INSTANCE.name())
        .appId("APP_ID")
        .serviceId("SERVICE_ID")
        .envId("ENV_ID")
        .deploymentId("DEPLOYMENT_ID")
        .cloudProviderId("CLOUD_PROVIDE_ID")
        .build();
  }
}
