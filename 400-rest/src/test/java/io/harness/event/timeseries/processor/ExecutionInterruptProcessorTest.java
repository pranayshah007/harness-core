/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.timeseries.processor;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.WingsBaseTest;
import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ExecutionInterruptProcessorTest extends WingsBaseTest {
  @Mock TimeScaleDBService timeScaleService;
  @Inject @InjectMocks ExecutionInterruptProcessor executionInterruptProcessor;

  private Connection dbConnection = mock(Connection.class);
  private PreparedStatement preparedStatement = mock(PreparedStatement.class);

  private int MAX_RETRY = 5;

  @Before
  public void setup() throws SQLException {
    when(timeScaleService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testTimescaleInvalid() throws SQLException {
    when(timeScaleService.isValid()).thenReturn(false);
    TimeSeriesEventInfo timeSeriesEventInfo = TimeSeriesEventInfo.builder().build();
    executionInterruptProcessor.processEvent(timeSeriesEventInfo);
    verify(timeScaleService, never()).getDBConnection();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testNullProperties() throws SQLException {
    when(timeScaleService.isValid()).thenReturn(true);

    TimeSeriesEventInfo timeSeriesEventInfo =
        TimeSeriesEventInfo.builder().longData(new HashMap<>()).stringData(new HashMap<>()).build();
    executionInterruptProcessor.processEvent(timeSeriesEventInfo);
    verify(timeScaleService, never()).getDBConnection();

    timeSeriesEventInfo = TimeSeriesEventInfo.builder().longData(new HashMap<>()).accountId("abc").build();
    executionInterruptProcessor.processEvent(timeSeriesEventInfo);
    verify(timeScaleService, never()).getDBConnection();

    timeSeriesEventInfo = TimeSeriesEventInfo.builder().stringData(new HashMap<>()).accountId("abc").build();
    executionInterruptProcessor.processEvent(timeSeriesEventInfo);
    verify(timeScaleService, never()).getDBConnection();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testSuccessFullExecution() throws SQLException {
    when(timeScaleService.isValid()).thenReturn(true);
    TimeSeriesEventInfo timeSeriesEventInfo =
        TimeSeriesEventInfo.builder().longData(new HashMap<>()).stringData(new HashMap<>()).accountId("abc").build();
    ExecutionInterruptProcessor executionInterruptProcessor1 = spy(executionInterruptProcessor);
    doNothing().when(executionInterruptProcessor1).upsertDataToTimescaleDB(timeSeriesEventInfo, preparedStatement);

    executionInterruptProcessor1.processEvent(timeSeriesEventInfo);

    verify(executionInterruptProcessor1).upsertDataToTimescaleDB(timeSeriesEventInfo, preparedStatement);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testWhenThrowsExecution() throws SQLException {
    when(timeScaleService.isValid()).thenReturn(true);
    TimeSeriesEventInfo timeSeriesEventInfo =
        TimeSeriesEventInfo.builder().longData(new HashMap<>()).stringData(new HashMap<>()).accountId("abc").build();
    ExecutionInterruptProcessor executionInterruptProcessor1 = spy(executionInterruptProcessor);
    doThrow(new SQLException())
        .when(executionInterruptProcessor1)
        .upsertDataToTimescaleDB(timeSeriesEventInfo, preparedStatement);

    executionInterruptProcessor1.processEvent(timeSeriesEventInfo);

    verify(executionInterruptProcessor1, times(MAX_RETRY))
        .upsertDataToTimescaleDB(timeSeriesEventInfo, preparedStatement);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testUpsertData() throws SQLException {
    when(timeScaleService.isValid()).thenReturn(true);
    TimeSeriesEventInfo timeSeriesEventInfo = TimeSeriesEventInfo.builder()
                                                  .longData(new HashMap<>())
                                                  .stringData(new HashMap<>())
                                                  .booleanData(new HashMap<>())
                                                  .accountId("abc")
                                                  .build();

    doReturn(true).when(preparedStatement).execute();

    executionInterruptProcessor.upsertDataToTimescaleDB(timeSeriesEventInfo, preparedStatement);

    verify(preparedStatement).execute();
  }
}
