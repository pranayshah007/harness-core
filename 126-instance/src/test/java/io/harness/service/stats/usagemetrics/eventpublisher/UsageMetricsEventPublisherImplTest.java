/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.stats.usagemetrics.eventpublisher;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.entities.InstanceType;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
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
  public void publishInstanceStatsTimeSeriesTest() {
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
                                   .infrastructureMappingId(INFRASTRUCTURE_ID_2)
                                   .connectorRef(CONNECTOR_REF)
                                   .instanceType(InstanceType.K8S_INSTANCE)
                                   .build();
    usageMetricsEventPublisher.publishInstanceStatsTimeSeries(
        ACCOUNT_ID, TIMESTAMP, Arrays.asList(instanceDTO1, instanceDTO2));
    verify(eventProducer, times(1)).send(any(Message.class));
  }
}
