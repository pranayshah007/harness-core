/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.instancestatsiterator;

import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.models.InstanceStatsIterator;
import io.harness.repositories.instancestats.InstanceStatsFields;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InstanceStatsIteratorRepositoryImplTest extends InstancesTestBase {
  private final String ACCOUNT_ID = "acc";
  private final String ORG_ID = "org";
  private final String PROJECT_ID = "proj";
  private final String SERVICE_ID = "service";
  private final Timestamp timestamp = new Timestamp(1234l);
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private PreparedStatement statement;
  @Mock private Connection dbConnection;
  @Mock private ResultSet resultSet;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @InjectMocks InstanceStatsIteratorRepositoryImpl instanceStatsIteratorRepository;

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void getLatestRecordTest() throws Exception {
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(InstanceStatsIteratorQuery.FETCH_LATEST_RECORD_PROJECT_LEVEL.query()))
        .thenReturn(statement);
    statement.setString(1, ACCOUNT_ID);
    statement.setString(2, ORG_ID);
    statement.setString(3, PROJECT_ID);
    statement.setString(4, SERVICE_ID);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getString(InstanceStatsFields.ACCOUNTID.fieldName())).thenReturn(ACCOUNT_ID);
    when(resultSet.getString(InstanceStatsFields.SERVICEID.fieldName())).thenReturn(SERVICE_ID);
    when(resultSet.getTimestamp(InstanceStatsFields.REPORTEDAT.fieldName())).thenReturn(timestamp);
    InstanceStatsIterator instanceStats =
        instanceStatsIteratorRepository.getLatestRecord(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
    assertThat(instanceStats.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(instanceStats.getServiceId()).isEqualTo(SERVICE_ID);
    assertThat(instanceStats.getReportedAt()).isEqualTo(timestamp);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void updateTimestampForIteratorTest() throws Exception {
    when(cdFeatureFlagHelper.isEnabled(eq(ACCOUNT_ID), any())).thenReturn(true);
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(InstanceStatsIteratorQuery.UPDATE_RECORD.query())).thenReturn(statement);
    statement.setTimestamp(1, new Timestamp(timestamp.getTime()), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
    statement.setString(2, ACCOUNT_ID);
    statement.setString(3, ORG_ID);
    statement.setString(4, PROJECT_ID);
    statement.setString(5, SERVICE_ID);
    statement.setTimestamp(6, new Timestamp(timestamp.getTime()), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
    when(statement.execute()).thenReturn(true);
    instanceStatsIteratorRepository.updateTimestampForIterator(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, timestamp.getTime());
    verify(dbConnection, times(1)).prepareStatement(InstanceStatsIteratorQuery.UPDATE_RECORD.query());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void getLatestRecordTestThrowException() throws SQLException {
    when(cdFeatureFlagHelper.isEnabled(eq(ACCOUNT_ID), any())).thenReturn(true);
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(InstanceStatsIteratorQuery.UPDATE_RECORD.query())).thenReturn(statement);
    statement.setTimestamp(1, new Timestamp(timestamp.getTime()), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
    statement.setString(2, ACCOUNT_ID);
    statement.setString(3, ORG_ID);
    statement.setString(4, PROJECT_ID);
    statement.setString(5, SERVICE_ID);
    statement.setTimestamp(6, new Timestamp(timestamp.getTime()), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
    when(statement.execute()).thenThrow(new SQLTimeoutException());
    assertThatCode(()
                       -> instanceStatsIteratorRepository.updateTimestampForIterator(
                           ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, timestamp.getTime()))
        .doesNotThrowAnyException();
    verify(dbConnection, times(4)).prepareStatement(InstanceStatsIteratorQuery.UPDATE_RECORD.query());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void updateTimestampForIteratorTestFFDisabled() throws Exception {
    when(cdFeatureFlagHelper.isEnabled(eq(ACCOUNT_ID), any())).thenReturn(false);
    instanceStatsIteratorRepository.updateTimestampForIterator(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, timestamp.getTime());
    verify(timeScaleDBService, times(0)).getDBConnection();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void getLatestRecordTestShouldPassOnRetry() throws SQLException {
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(InstanceStatsIteratorQuery.FETCH_LATEST_RECORD_PROJECT_LEVEL.query()))
        .thenReturn(statement);
    statement.setString(1, ACCOUNT_ID);
    statement.setString(2, ORG_ID);
    statement.setString(3, PROJECT_ID);
    statement.setString(4, SERVICE_ID);
    when(statement.executeQuery()).thenThrow(new SQLException()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getString(InstanceStatsFields.ACCOUNTID.fieldName())).thenReturn(ACCOUNT_ID);
    when(resultSet.getString(InstanceStatsFields.SERVICEID.fieldName())).thenReturn(SERVICE_ID);
    when(resultSet.getTimestamp(InstanceStatsFields.REPORTEDAT.fieldName())).thenReturn(timestamp);
    InstanceStatsIterator instanceStats = null;
    try {
      instanceStats = instanceStatsIteratorRepository.getLatestRecord(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
    } catch (Exception e) {
      e.printStackTrace();
    }
    assertThat(instanceStats.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(instanceStats.getServiceId()).isEqualTo(SERVICE_ID);
    assertThat(instanceStats.getReportedAt()).isEqualTo(timestamp);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void getLatestRecordTestShouldFailAfterAllRetries() throws SQLException {
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(InstanceStatsIteratorQuery.FETCH_LATEST_RECORD_PROJECT_LEVEL.query()))
        .thenReturn(statement);
    statement.setString(1, ACCOUNT_ID);
    statement.setString(2, ORG_ID);
    statement.setString(3, PROJECT_ID);
    statement.setString(4, SERVICE_ID);
    when(statement.executeQuery()).thenThrow(new SQLException());
    InstanceStatsIterator instanceStats = null;
    try {
      instanceStats = instanceStatsIteratorRepository.getLatestRecord(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
    } catch (Exception e) {
      e.printStackTrace();
    }
    assertThat(instanceStats).isNull();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void getLatestRecordTestShouldFailAfterThrowingException() throws SQLException {
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(InstanceStatsIteratorQuery.FETCH_LATEST_RECORD_PROJECT_LEVEL.query()))
        .thenReturn(statement);
    statement.setString(1, ACCOUNT_ID);
    statement.setString(2, ORG_ID);
    statement.setString(3, PROJECT_ID);
    statement.setString(4, SERVICE_ID);
    when(statement.executeQuery()).thenThrow(new SQLException("Sample Exception"));
    InstanceStatsIterator instanceStats = null;
    try {
      instanceStats = instanceStatsIteratorRepository.getLatestRecord(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
    } catch (Exception e) {
      assertThat(e.getMessage()).isEqualTo("Sample Exception");
    }
    assertThat(instanceStats).isNull();
  }
}
