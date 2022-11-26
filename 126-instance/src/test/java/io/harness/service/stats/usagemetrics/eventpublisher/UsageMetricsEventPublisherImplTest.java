/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.stats.usagemetrics.eventpublisher;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.verify;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.entities.Instance;
import io.harness.entities.InstanceType;
import io.harness.entities.instanceinfo.GitopsInstanceInfo;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.instancestatstimeseriesevent.DataPoint;
import io.harness.eventsframework.schemas.instancestatstimeseriesevent.TimeseriesBatchEventInfo;
import io.harness.models.constants.TimescaleConstants;
import io.harness.ng.core.entities.Project;
import io.harness.rule.Owner;
import io.harness.service.stats.model.InstanceCountByServiceAndEnv;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import joptsimple.internal.Strings;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class UsageMetricsEventPublisherImplTest extends InstancesTestBase {
  private final String ACCOUNT_ID = "acc";
  private final String ACCOUNT_ID_1 = "acc1";
  private final String ORG_IDENTIFIER = "org";
  private final String PROJECT_IDENTIFIER = "proj";
  private final String SERVICE_IDENTIFIER = "serv";
  private final String ENVIRONMENT_IDENTIFIER1 = "env1";
  private final String ENVIRONMENT_IDENTIFIER2 = "env2";
  private final String ENVIRONMENT_IDENTIFIER3 = "env3";
  private final String CONNECTOR_REF = "conn";
  private final long TIMESTAMP = 123L;
  private final int INSTANCE_COUNT1 = 5;
  private final int INSTANCE_COUNT2 = 10;
  private final int INSTANCE_COUNT3 = 15;

  @Mock Producer eventProducer;

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void publishInstanceStatsTimeSeriesTest() throws InvalidProtocolBufferException {
    UsageMetricsEventPublisherImpl usageMetricsEventPublisher = new UsageMetricsEventPublisherImpl(eventProducer);
    Project project = Project.builder()
                          .accountIdentifier(ACCOUNT_ID)
                          .orgIdentifier(ORG_IDENTIFIER)
                          .identifier(PROJECT_IDENTIFIER)
                          .build();
    Instance instance1 = Instance.builder()
                             .accountIdentifier(ACCOUNT_ID_1)
                             .orgIdentifier(ORG_IDENTIFIER)
                             .projectIdentifier(PROJECT_IDENTIFIER)
                             .serviceIdentifier(SERVICE_IDENTIFIER)
                             .envIdentifier(ENVIRONMENT_IDENTIFIER1)
                             .connectorRef(CONNECTOR_REF)
                             .instanceType(InstanceType.K8S_INSTANCE)
                             .createdAt(0L)
                             .lastModifiedAt(0L)
                             .instanceInfo(K8sInstanceInfo.builder().build())
                             .build();
    InstanceCountByServiceAndEnv instanceCountByServiceAndEnv1 = InstanceCountByServiceAndEnv.builder()
                                                                     .serviceIdentifier(SERVICE_IDENTIFIER)
                                                                     .envIdentifier(ENVIRONMENT_IDENTIFIER1)
                                                                     .count(INSTANCE_COUNT1)
                                                                     .firstDocument(instance1)
                                                                     .build();
    Instance instance2 = Instance.builder()
                             .accountIdentifier(ACCOUNT_ID)
                             .orgIdentifier(ORG_IDENTIFIER)
                             .projectIdentifier(PROJECT_IDENTIFIER)
                             .serviceIdentifier(SERVICE_IDENTIFIER)
                             .envIdentifier(ENVIRONMENT_IDENTIFIER2)
                             .connectorRef(CONNECTOR_REF)
                             .instanceType(InstanceType.K8S_INSTANCE)
                             .createdAt(0L)
                             .lastModifiedAt(0L)
                             .instanceInfo(K8sInstanceInfo.builder().build())
                             .build();
    InstanceCountByServiceAndEnv instanceCountByServiceAndEnv2 = InstanceCountByServiceAndEnv.builder()
                                                                     .serviceIdentifier(SERVICE_IDENTIFIER)
                                                                     .envIdentifier(ENVIRONMENT_IDENTIFIER2)
                                                                     .count(INSTANCE_COUNT2)
                                                                     .firstDocument(instance2)
                                                                     .build();
    // GitOps use-case with no connectorRef
    Instance instance3 = Instance.builder()
                             .accountIdentifier(ACCOUNT_ID)
                             .orgIdentifier(ORG_IDENTIFIER)
                             .projectIdentifier(PROJECT_IDENTIFIER)
                             .serviceIdentifier(SERVICE_IDENTIFIER)
                             .envIdentifier(ENVIRONMENT_IDENTIFIER3)
                             .instanceType(InstanceType.K8S_INSTANCE)
                             .createdAt(0L)
                             .lastModifiedAt(0L)
                             .instanceInfo(GitopsInstanceInfo.builder().build())
                             .build();
    InstanceCountByServiceAndEnv instanceCountByServiceAndEnv3 = InstanceCountByServiceAndEnv.builder()
                                                                     .serviceIdentifier(SERVICE_IDENTIFIER)
                                                                     .envIdentifier(ENVIRONMENT_IDENTIFIER3)
                                                                     .count(INSTANCE_COUNT3)
                                                                     .firstDocument(instance3)
                                                                     .build();

    usageMetricsEventPublisher.publishInstanceStatsTimeSeries(project, TIMESTAMP,
        Arrays.asList(instanceCountByServiceAndEnv1, instanceCountByServiceAndEnv2, instanceCountByServiceAndEnv3));

    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer).send(messageArgumentCaptor.capture());
    TimeseriesBatchEventInfo eventInfo = TimeseriesBatchEventInfo.parseFrom(messageArgumentCaptor.getValue().getData());
    List<DataPoint> dataPoints = eventInfo.getDataPointListList();
    assertEquals(3, dataPoints.size());
    verifyDataPoint(instance1, INSTANCE_COUNT1, dataPoints.get(0));
    verifyDataPoint(instance2, INSTANCE_COUNT2, dataPoints.get(1));
    verifyDataPoint(instance3, INSTANCE_COUNT3, dataPoints.get(2));
  }

  private void verifyDataPoint(Instance instance, int count, DataPoint dataPoint) {
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
      assertEquals(Strings.EMPTY, data.get(TimescaleConstants.CLOUDPROVIDER_ID.getKey()));
    }
  }
}
