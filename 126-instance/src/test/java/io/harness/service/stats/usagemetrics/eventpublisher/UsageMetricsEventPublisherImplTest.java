/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.stats.usagemetrics.eventpublisher;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.entities.Instance;
import io.harness.entities.InstanceType;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.ng.core.entities.Project;
import io.harness.rule.Owner;
import io.harness.service.stats.model.InstanceCountByServiceAndEnv;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class UsageMetricsEventPublisherImplTest extends InstancesTestBase {
  private final String ACCOUNT_ID = "acc";
  private final String ACCOUNT_ID_1 = "acc1";
  private final String ORG_IDENTIFIER = "org";
  private final String PROJECT_IDENTIFIER = "proj";
  private final String SERVICE_IDENTIFIER = "serv";
  private final String ENVIRONMENT_IDENTIFIER1 = "env1";
  private final String ENVIRONMENT_IDENTIFIER2 = "env2";
  private final String CONNECTOR_REF = "conn";
  private final long TIMESTAMP = 123L;

  @Mock Producer eventProducer;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void publishInstanceStatsTimeSeriesTest() {
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
                             .build();
    InstanceCountByServiceAndEnv instanceCountByServiceAndEnv1 = InstanceCountByServiceAndEnv.builder()
                                                                     .serviceIdentifier(SERVICE_IDENTIFIER)
                                                                     .envIdentifier(ENVIRONMENT_IDENTIFIER1)
                                                                     .count(5)
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
                             .build();
    InstanceCountByServiceAndEnv instanceCountByServiceAndEnv2 = InstanceCountByServiceAndEnv.builder()
                                                                     .serviceIdentifier(SERVICE_IDENTIFIER)
                                                                     .envIdentifier(ENVIRONMENT_IDENTIFIER2)
                                                                     .count(10)
                                                                     .firstDocument(instance2)
                                                                     .build();
    usageMetricsEventPublisher.publishInstanceStatsTimeSeries(
        project, TIMESTAMP, Arrays.asList(instanceCountByServiceAndEnv1, instanceCountByServiceAndEnv2));
    verify(eventProducer).send(any(Message.class));
  }
}
