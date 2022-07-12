/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.stats.usagemetrics.eventconsumer;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.entities.InstanceType;
import io.harness.event.timeseries.processor.EventProcessor;
import io.harness.eventsframework.schemas.instancestatstimeseriesevent.DataPoint;
import io.harness.eventsframework.schemas.instancestatstimeseriesevent.TimeseriesBatchEventInfo;
import io.harness.models.constants.TimescaleConstants;
import io.harness.rule.Owner;
import io.harness.service.stats.usagemetrics.eventconsumer.instanceaggregator.DailyAggregator;
import io.harness.service.stats.usagemetrics.eventconsumer.instanceaggregator.HourlyAggregator;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InstanceEventAggregatorTest extends InstancesTestBase {
  private final String ORG_IDENTIFIER = "org";
  private final String PROJECT_IDENTIFIER = "proj";
  private final String SERVICE_IDENTIFIER = "serv";
  private final String ENVIRONMENT_IDENTIFIER = "env";
  private final String CLOUD_PROVIDER_ID = "cloud";
  private final Long TIMESTAMP = 123L;
  private static final String NUM_OF_RECORDS = "NUM_OF_RECORDS";
  public static final String DATA_MAP_KEY = "DATA_MAP_KEY";

  @Mock Connection dbConnection;
  @Mock PreparedStatement preparedStatement;
  @Mock ResultSetMetaData resultSetMetaData;
  @Mock ResultSet resultSet;
  @Mock private TimeScaleDBService timeScaleDBService;
  @InjectMocks InstanceEventAggregator instanceEventAggregator;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void doHourlyAggregationTest() throws SQLException {
    DataPoint dataPoint = DataPoint.newBuilder()
                              .putData(TimescaleConstants.ORG_ID.getKey(), ORG_IDENTIFIER)
                              .putData(TimescaleConstants.PROJECT_ID.getKey(), PROJECT_IDENTIFIER)
                              .putData(TimescaleConstants.SERVICE_ID.getKey(), SERVICE_IDENTIFIER)
                              .putData(TimescaleConstants.ENV_ID.getKey(), ENVIRONMENT_IDENTIFIER)
                              .putData(TimescaleConstants.CLOUDPROVIDER_ID.getKey(), CLOUD_PROVIDER_ID)
                              .putData(TimescaleConstants.INSTANCE_TYPE.getKey(), InstanceType.K8S_INSTANCE.toString())
                              .putData(TimescaleConstants.INSTANCECOUNT.getKey(), "5")
                              .build();
    TimeseriesBatchEventInfo eventInfo =
        TimeseriesBatchEventInfo.newBuilder().addDataPointList(dataPoint).setTimestamp(TIMESTAMP).build();
    when(timeScaleDBService.isValid()).thenReturn(true);
    HourlyAggregator aggregator = new HourlyAggregator(eventInfo);
    DailyAggregator dailyAggregator = new DailyAggregator(eventInfo);
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(aggregator.getUpsertParentTableSQL())).thenReturn(preparedStatement);
    when(dbConnection.prepareStatement(dailyAggregator.getUpsertParentTableSQL())).thenReturn(preparedStatement);
    when(dbConnection.prepareStatement(aggregator.getFetchChildDataPointsSQL())).thenReturn(preparedStatement);
    when(dbConnection.prepareStatement(dailyAggregator.getFetchChildDataPointsSQL())).thenReturn(preparedStatement);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
    when(resultSetMetaData.getColumnCount()).thenReturn(3);
    when(resultSet.getBoolean(EventProcessor.SANITYSTATUS)).thenReturn(true);
    when(resultSet.getInt(EventProcessor.INSTANCECOUNT)).thenReturn(2);
    when(resultSet.getInt(NUM_OF_RECORDS)).thenReturn(3);
    when(resultSet.next()).thenReturn(true).thenReturn(false);
    instanceEventAggregator.doHourlyAggregation(eventInfo);
    verify(preparedStatement, times(2)).setString(3, eventInfo.getAccountId());
    verify(preparedStatement, times(2)).setString(4, ORG_IDENTIFIER);
    verify(preparedStatement, times(2)).setString(5, PROJECT_IDENTIFIER);
    verify(preparedStatement, times(2)).setString(6, SERVICE_IDENTIFIER);
    verify(preparedStatement, times(2)).setString(7, ENVIRONMENT_IDENTIFIER);
    verify(preparedStatement, times(2)).setString(8, CLOUD_PROVIDER_ID);
    verify(preparedStatement, times(2)).setString(9, InstanceType.K8S_INSTANCE.toString());
    verify(preparedStatement, times(2)).addBatch();
    verify(preparedStatement, times(2)).executeBatch();
  }
}
