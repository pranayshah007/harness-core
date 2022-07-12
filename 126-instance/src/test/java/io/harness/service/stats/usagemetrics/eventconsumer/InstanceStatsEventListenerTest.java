/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.stats.usagemetrics.eventconsumer;

import static io.harness.eventsframework.EventsFrameworkConstants.INSTANCE_STATS;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.entities.InstanceType;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.instancestatstimeseriesevent.DataPoint;
import io.harness.eventsframework.schemas.instancestatstimeseriesevent.TimeseriesBatchEventInfo;
import io.harness.models.constants.TimescaleConstants;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InstanceStatsEventListenerTest extends InstancesTestBase {
  private final String ORG_IDENTIFIER = "org";
  private final String PROJECT_IDENTIFIER = "proj";
  private final String SERVICE_IDENTIFIER = "serv";
  private final String ENVIRONMENT_IDENTIFIER = "env";
  private final String CLOUD_PROVIDER_ID = "cloud";
  private final Long TIMESTAMP = 123L;

  private static final String insert_prepared_statement_sql =
      "INSERT INTO NG_INSTANCE_STATS (REPORTEDAT, ACCOUNTID, ORGID, PROJECTID, SERVICEID, ENVID, CLOUDPROVIDERID, INSTANCETYPE, INSTANCECOUNT) VALUES (?,?,?,?,?,?,?,?,?)";

  @Mock Connection connection;
  @Mock PreparedStatement preparedStatement;
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private InstanceEventAggregator instanceEventAggregator;
  @InjectMocks InstanceStatsEventListener instanceStatsEventListener;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void handleMessageTest() throws SQLException {
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
    io.harness.eventsframework.producer.Message producerMessage =
        io.harness.eventsframework.producer.Message.newBuilder()
            .setData(eventInfo.toByteString())
            .putMetadata(ENTITY_TYPE, INSTANCE_STATS)
            .build();
    Message consumerMessage = Message.newBuilder().setId("id").setMessage(producerMessage).build();
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.prepareStatement(insert_prepared_statement_sql)).thenReturn(preparedStatement);
    assertThat(instanceStatsEventListener.handleMessage(consumerMessage)).isTrue();
    verify(preparedStatement, times(1)).setString(2, eventInfo.getAccountId());
    verify(preparedStatement, times(1)).setString(3, ORG_IDENTIFIER);
    verify(preparedStatement, times(1)).setString(4, PROJECT_IDENTIFIER);
    verify(preparedStatement, times(1)).setString(5, SERVICE_IDENTIFIER);
    verify(preparedStatement, times(1)).setString(6, ENVIRONMENT_IDENTIFIER);
    verify(preparedStatement, times(1)).setString(7, CLOUD_PROVIDER_ID);
    verify(preparedStatement, times(1)).setString(8, InstanceType.K8S_INSTANCE.toString());
    verify(preparedStatement, times(1)).setInt(9, 5);
    verify(preparedStatement, times(1)).addBatch();
    verify(preparedStatement, times(1)).executeBatch();
    verify(instanceEventAggregator, times(1)).doHourlyAggregation(eventInfo);
  }
}
