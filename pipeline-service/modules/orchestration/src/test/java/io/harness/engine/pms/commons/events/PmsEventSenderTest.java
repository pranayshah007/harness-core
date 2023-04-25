/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.commons.events;

import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.ModuleType;
import io.harness.OrchestrationModuleConfig;
import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.expressions.AmbianceExpressionEvaluatorProvider;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.pms.contracts.plan.ConsumerConfig;
import io.harness.pms.contracts.plan.Redis;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsEventSenderTest extends OrchestrationTestBase {
  @Inject PmsEventSender eventSender;
  @Inject MongoTemplate mongoTemplate;

  private static final String TOPIC1 = "topic1";
  private static final String TOPIC2 = "topic2";

  @Before
  public void setup() {
    mongoTemplate.save(
        PmsSdkInstance.builder()
            .name(ModuleType.PMS.name())
            .supportedTypes(new HashMap<>())
            .supportedSdkSteps(new ArrayList<>())
            .interruptConsumerConfig(
                ConsumerConfig.newBuilder().setRedis(Redis.newBuilder().setTopicName(TOPIC1).build()).build())
            .orchestrationEventConsumerConfig(
                ConsumerConfig.newBuilder().setRedis(Redis.newBuilder().setTopicName(TOPIC2).build()).build())
            .build());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestObtainProducer() {
    Producer producer = eventSender.obtainProducer(PmsEventCategory.INTERRUPT_EVENT, ModuleType.PMS.name());
    assertThat(((NoOpProducer) producer).getTopicName()).isEqualTo(TOPIC1);

    producer = eventSender.obtainProducer(PmsEventCategory.ORCHESTRATION_EVENT, ModuleType.PMS.name());
    assertThat(((NoOpProducer) producer).getTopicName()).isEqualTo(TOPIC2);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestCache() {
    PmsEventSender spyEventsFrameworkUtils = spy(PmsEventSender.class);
    Reflect.on(spyEventsFrameworkUtils)
        .set("moduleConfig",
            OrchestrationModuleConfig.builder()
                .serviceName(ModuleType.PMS.name())
                .expressionEvaluatorProvider(new AmbianceExpressionEvaluatorProvider())
                .build());
    doReturn(PmsSdkInstance.builder()
                 .name(ModuleType.PMS.name())
                 .supportedTypes(new HashMap<>())
                 .supportedSdkSteps(new ArrayList<>())
                 .interruptConsumerConfig(
                     ConsumerConfig.newBuilder().setRedis(Redis.newBuilder().setTopicName(TOPIC1).build()).build())
                 .orchestrationEventConsumerConfig(
                     ConsumerConfig.newBuilder().setRedis(Redis.newBuilder().setTopicName(TOPIC2).build()).build())
                 .build())
        .when(spyEventsFrameworkUtils)
        .getPmsSdkInstance(ModuleType.PMS.name());
    spyEventsFrameworkUtils.obtainProducer(PmsEventCategory.ORCHESTRATION_EVENT, ModuleType.PMS.name());
    spyEventsFrameworkUtils.obtainProducer(PmsEventCategory.ORCHESTRATION_EVENT, ModuleType.PMS.name());

    verify(spyEventsFrameworkUtils, times(1)).obtainProducer(any(ProducerCacheKey.class));
  }
}
