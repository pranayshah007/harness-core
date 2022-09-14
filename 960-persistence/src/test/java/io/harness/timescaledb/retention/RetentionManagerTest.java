/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.timescaledb.retention;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.commons.dbcp.DelegatingResultSet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class RetentionManagerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private static final String TEST_TABLE = "test";
  private static final String TEST_RETENTION_PERIOD = "7 months";
  private static final String ADD_QUERY = "SELECT add_retention_policy('test', INTERVAL '7 months');";
  @InjectMocks RetentionManagerImpl retentionManager;
  @Mock TimeScaleDBService timeScaleDBService;

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testAddPolicyWhenRetentionPeriodIsUnSupported() throws SQLException {
    retentionManager.addPolicy(TEST_TABLE, "7 hours");
    verify(timeScaleDBService, times(0)).getDBConnection();
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testAddPolicyWhenExistingPolicyIsSame() throws SQLException {
    Connection dbConnection = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(anyString())).thenReturn(statement);
    when(dbConnection.createStatement()).thenReturn(statement);
    ResultSet resultSet = mock(DelegatingResultSet.class);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getString(eq(1))).thenReturn("{\"drop_after\": \"7 mons\", \"hypertable_id\": 5}");

    retentionManager.addPolicy(TEST_TABLE, TEST_RETENTION_PERIOD);
    verify(statement, times(0)).executeQuery(anyString());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testAddPolicyWhenThereIsNoExistingPolicy() throws SQLException {
    Connection dbConnection = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(anyString())).thenReturn(statement);
    when(dbConnection.createStatement()).thenReturn(statement);

    retentionManager.addPolicy(TEST_TABLE, TEST_RETENTION_PERIOD);
    verify(statement).executeQuery(ADD_QUERY);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testAddPolicyWhenExistingPolicyIsDifferent() throws SQLException {
    Connection dbConnection = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(anyString())).thenReturn(statement);
    when(dbConnection.createStatement()).thenReturn(statement);
    ResultSet resultSet = mock(DelegatingResultSet.class);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getString(eq(1))).thenReturn("{\"drop_after\": \"2 mons\", \"hypertable_id\": 5}");

    retentionManager.addPolicy(TEST_TABLE, TEST_RETENTION_PERIOD);
    verify(statement, times(2)).executeQuery();
    verify(statement).executeQuery(ADD_QUERY);
  }
}
