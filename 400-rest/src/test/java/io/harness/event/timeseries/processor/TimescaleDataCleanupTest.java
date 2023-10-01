/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.timeseries.processor;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TimescaleDataCleanupTest {
  private static final String ACCOUNT_ID = "eiO6m-9S-WNMFj";

  @InjectMocks private TimescaleDataCleanup service;
  @Mock private Connection connection;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldCreateStatementForTargetTables() throws SQLException {
    when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
    assertThat(service.prepareQueriesForDeletion(connection, ACCOUNT_ID)).hasSize(11);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldVerifyBothGroupsOfTables() {
    final Pair<List<String>, List<String>> tables = service.initialiseTables();
    assertThat(tables.getLeft()).containsOnly("deployment_step", "execution_interrupt");
    assertThat(tables.getRight())
        .containsOnly("deployment", "deployment_stage", "deployment_parent", "instance_stats", "instance_stats_day",
            "instance_stats_hour", "ng_instance_stats", "ng_instance_stats_day", "ng_instance_stats_hour");
  }
}
