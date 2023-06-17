/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdlicense.impl;

import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdlicense.exception.CgLicenseUsageException;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CgCdLicenseUsageQueryHelperTest extends CategoryTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private WingsPersistence wingsPersistence;
  @InjectMocks @Inject private CgCdLicenseUsageQueryHelper cgCdLicenseUsageQueryHelper;

  @Mock PreparedStatement preparedStatement;
  @Mock ResultSet resultSet;
  @Mock Connection connection;

  private static final String accountIdentifier = "ACCOUNT_ID";
  private static final int timePeriod = 30;
  private static final double percentile = 0.95;

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testFetchDistinctSvcIdWithNullResultSet() throws SQLException {
    when(preparedStatement.executeQuery()).thenReturn(null);
    List<String> svcIdUsedInDeployments =
        cgCdLicenseUsageQueryHelper.fetchDistinctSvcIdUsedInDeployments(accountIdentifier, timePeriod);
    assertThat(svcIdUsedInDeployments).isEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testFetchDistinctSvcIdEmptyResultSet() throws SQLException {
    when(resultSet.next()).thenReturn(false);
    List<String> svcIdUsedInDeployments =
        cgCdLicenseUsageQueryHelper.fetchDistinctSvcIdUsedInDeployments(accountIdentifier, timePeriod);
    assertThat(svcIdUsedInDeployments).isEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testFetchDistinctSvcIdException() throws SQLException {
    when(preparedStatement.executeQuery()).thenThrow(new SQLException());
    assertThatThrownBy(
        () -> cgCdLicenseUsageQueryHelper.fetchDistinctSvcIdUsedInDeployments(accountIdentifier, timePeriod))
        .isInstanceOf(CgLicenseUsageException.class)
        .hasMessageContaining(
            "MAX RETRY FAILURE: Failed to fetch serviceIds within interval for last [30 days] deployments for accountId ACCOUNT_ID after 3 retries");
    verify(preparedStatement, times(4)).executeQuery();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testFetchDistinctSvcId() throws SQLException {
    when(resultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
    when(resultSet.getString(1)).thenReturn("svc1").thenReturn("svc2");
    List<String> svcIdUsedInDeployments =
        cgCdLicenseUsageQueryHelper.fetchDistinctSvcIdUsedInDeployments(accountIdentifier, timePeriod);
    assertThat(svcIdUsedInDeployments).isNotEmpty();
    assertThat(svcIdUsedInDeployments).containsExactlyInAnyOrder("svc1", "svc2");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetPercentileInstanceWithNoService() {
    assertThat(cgCdLicenseUsageQueryHelper.getServicesPercentileInstanceCountAndLicenseUsage(
                   accountIdentifier, emptyList(), timePeriod, percentile))
        .isEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetPercentileInstanceWithEmptyResultSet() throws SQLException {
    List<String> svcIds = Arrays.asList("svc1", "svc2");
    when(resultSet.next()).thenReturn(false);
    assertThat(cgCdLicenseUsageQueryHelper.getServicesPercentileInstanceCountAndLicenseUsage(
                   accountIdentifier, svcIds, timePeriod, percentile))
        .isEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetPercentileInstanceException() throws SQLException {
    List<String> svcIds = Arrays.asList("svc1", "svc2");
    when(preparedStatement.executeQuery()).thenThrow(new SQLException());
    assertThatThrownBy(()
                           -> cgCdLicenseUsageQueryHelper.getServicesPercentileInstanceCountAndLicenseUsage(
                               accountIdentifier, svcIds, timePeriod, percentile))
        .isInstanceOf(CgLicenseUsageException.class)
        .hasMessageContaining(
            "MAX RETRY FAILURE: Failed to fetch services percentile instance count and license usage for accountId ACCOUNT_ID after 3 retries");
  }

  @Test
  @Owner(developers = {TATHAGAT, IVAN})
  @Category(UnitTests.class)
  public void testGetPercentileInstance() throws SQLException {
    List<String> svcIds = Arrays.asList("svc1", "svc2");
    when(resultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
    when(resultSet.getString(1)).thenReturn("svc1").thenReturn("svc2");
    when(resultSet.getLong(2)).thenReturn(21L).thenReturn(42L);
    when(resultSet.getInt(3)).thenReturn(2).thenReturn(3);

    Map<String, Pair<Long, Integer>> percentileInstanceForServices =
        cgCdLicenseUsageQueryHelper.getServicesPercentileInstanceCountAndLicenseUsage(
            accountIdentifier, svcIds, timePeriod, percentile);
    assertThat(percentileInstanceForServices).isNotEmpty();
    assertThat(percentileInstanceForServices.keySet()).containsExactlyInAnyOrder("svc1", "svc2");
    assertThat(percentileInstanceForServices.values().stream().map(Pair::getLeft).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(21L, 42L);
    assertThat(percentileInstanceForServices.values().stream().map(Pair::getRight).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(2, 3);
  }
}
