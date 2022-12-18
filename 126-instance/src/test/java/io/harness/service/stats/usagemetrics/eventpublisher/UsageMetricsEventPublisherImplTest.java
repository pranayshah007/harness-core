/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.stats.usagemetrics.eventpublisher;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.entities.InstanceType;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.instancestatstimeseriesevent.DataPoint;
import io.harness.eventsframework.schemas.instancestatstimeseriesevent.TimeseriesBatchEventInfo;
import io.harness.models.constants.TimescaleConstants;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.rule.Owner;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class UsageMetricsEventPublisherImplTest extends InstancesTestBase {
  private final String ACCOUNT_ID = "acc";
  private final String ACCOUNT_ID_1 = "acc1";
  private final String ACCOUNT_ID_2 = "acc2";
  private final String ORG_IDENTIFIER = "org";
  private final String PROJECT_IDENTIFIER = "proj";
  private final String SERVICE_IDENTIFIER = "serv";
  private final String ENVIRONMENT_IDENTIFIER = "env";
  private final String INFRASTRUCTURE_ID_1 = "infraid1";
  private final String INFRASTRUCTURE_ID_2 = "infraid2";
  private final String CONNECTOR_REF = "conn";
  private final long TIMESTAMP = 123L;

  @Mock Producer eventProducer;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void publishInstanceStatsTimeSeriesTest() throws InvalidProtocolBufferException {
    UsageMetricsEventPublisherImpl usageMetricsEventPublisher = new UsageMetricsEventPublisherImpl(eventProducer);
    InstanceDTO instanceDTO1 = InstanceDTO.builder()
                                   .accountIdentifier(ACCOUNT_ID_1)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJECT_IDENTIFIER)
                                   .serviceIdentifier(SERVICE_IDENTIFIER)
                                   .envIdentifier(ENVIRONMENT_IDENTIFIER)
                                   .infrastructureMappingId(INFRASTRUCTURE_ID_1)
                                   .connectorRef(CONNECTOR_REF)
                                   .instanceType(InstanceType.K8S_INSTANCE)
                                   .build();
    InstanceDTO instanceDTO2 = InstanceDTO.builder()
                                   .accountIdentifier(ACCOUNT_ID_2)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJECT_IDENTIFIER)
                                   .serviceIdentifier(SERVICE_IDENTIFIER)
                                   .envIdentifier(ENVIRONMENT_IDENTIFIER)
                                   .infrastructureKind(InfrastructureKind.GITOPS)
                                   .connectorRef(CONNECTOR_REF)
                                   .instanceType(InstanceType.K8S_INSTANCE)
                                   .build();
    usageMetricsEventPublisher.publishInstanceStatsTimeSeries(
        ACCOUNT_ID, TIMESTAMP, Collections.singletonList(instanceDTO1));
    usageMetricsEventPublisher.publishInstanceStatsTimeSeries(
        ACCOUNT_ID, TIMESTAMP, Collections.singletonList(instanceDTO2));

    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(2)).send(messageArgumentCaptor.capture());
    List<Message> messages = messageArgumentCaptor.getAllValues();
    assertEquals(2, messages.size());

    TimeseriesBatchEventInfo eventInfo = TimeseriesBatchEventInfo.parseFrom(messages.get(0).getData());
    List<DataPoint> dataPoints = eventInfo.getDataPointListList();
    assertEquals(1, dataPoints.size());
    verifyDataPoint(instanceDTO1, 1, dataPoints.get(0));

    eventInfo = TimeseriesBatchEventInfo.parseFrom(messages.get(1).getData());
    dataPoints = eventInfo.getDataPointListList();
    assertEquals(1, dataPoints.size());
    verifyDataPoint(instanceDTO2, 1, dataPoints.get(0));
  }

  private void verifyDataPoint(InstanceDTO instance, int count, DataPoint dataPoint) {
    Map<String, String> data = dataPoint.getDataMap();
    assertEquals(instance.getAccountIdentifier(), data.get(TimescaleConstants.ACCOUNT_ID.getKey()));
    assertEquals(instance.getOrgIdentifier(), data.get(TimescaleConstants.ORG_ID.getKey()));
    assertEquals(instance.getProjectIdentifier(), data.get(TimescaleConstants.PROJECT_ID.getKey()));
    assertEquals(instance.getServiceIdentifier(), data.get(TimescaleConstants.SERVICE_ID.getKey()));
    assertEquals(instance.getEnvIdentifier(), data.get(TimescaleConstants.ENV_ID.getKey()));
    assertEquals(instance.getInstanceType().toString(), data.get(TimescaleConstants.INSTANCE_TYPE.getKey()));
    assertEquals(String.valueOf(count), data.get(TimescaleConstants.INSTANCECOUNT.getKey()));
    if (instance.getConnectorRef() != null) {
      assertEquals(instance.getConnectorRef(), data.get(TimescaleConstants.CLOUDPROVIDER_ID.getKey()));
    } else {
      assertEquals(StringUtils.EMPTY, data.get(TimescaleConstants.CLOUDPROVIDER_ID.getKey()));
    }
  }
}
