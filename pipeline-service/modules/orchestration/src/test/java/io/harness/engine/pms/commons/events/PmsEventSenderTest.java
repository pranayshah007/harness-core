/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.commons.events;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_FACILITATOR_EVENT_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_ADVISE_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_RESUME_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_START_EVENT_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_PROGRESS_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.START_PARTIAL_PLAN_CREATOR_MAX_TOPIC_SIZE;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.ModuleType;
import io.harness.OrchestrationModuleConfig;
import io.harness.OrchestrationRedisEventsConfig;
import io.harness.OrchestrationTestBase;
import io.harness.RedisEventConfig;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.expressions.AmbianceExpressionEvaluatorProvider;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ConsumerConfig;
import io.harness.pms.contracts.plan.Redis;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.redis.RedisConfig;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsEventSenderTest extends OrchestrationTestBase {
  @Mock private OrchestrationModuleConfig moduleConfig;
  @Spy @InjectMocks PmsEventSender eventSenderMock;
  @Inject PmsEventSender eventSender;
  @Inject MongoTemplate mongoTemplate;

  private static final String TOPIC1 = "topic1";
  private static final String TOPIC2 = "topic2";
  public static final String ACCOUNT_ID = generateUuid();
  public static String ORG_ID = "orgId";
  public static String PROJECT_ID = "projectId";
  public static final String APP_ID = generateUuid();
  public static final String RUNTIME_ID = generateUuid();
  public static final String EXECUTION_ID = generateUuid();

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

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testSendEventWithAmbiance() {
    Ambiance ambiance = getAmbiance();
    ByteString eventData = ByteString.fromHex("f98a3ba29c834cf4b50eac5a0c3e4b49");
    Producer producer = new NoOpProducer("dummy_producer");
    doReturn(producer).when(eventSenderMock).obtainProducer(any(), any());

    String messageID = eventSenderMock.sendEvent(ambiance, eventData, PmsEventCategory.ORCHESTRATION_EVENT, "cd", true);
    assertThat(messageID).isEqualTo("dummy-message-id");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testSendEvent() {
    ByteString eventData = ByteString.fromHex("f98a3ba29c834cf4b50eac5a0c3e4b49");
    Producer producer = new NoOpProducer("dummy_producer");
    doReturn(producer).when(eventSenderMock).obtainProducer(any(), any());

    String messageID =
        eventSenderMock.sendEvent(eventData, new HashMap<>(), PmsEventCategory.ORCHESTRATION_EVENT, "cd");
    assertThat(messageID).isEqualTo("dummy-message-id");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testObtainProducerForFacilitatorEvent() {
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .facilitatorEventConsumerConfig(ConsumerConfig.newBuilder().setRedis(Redis.getDefaultInstance()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.FACILITATOR_EVENT).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineFacilitatorEvent(
                RedisEventConfig.builder().maxTopicSize(PIPELINE_FACILITATOR_EVENT_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    assertThatCode(() -> eventSenderMock.obtainProducer(producerCacheKey)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testObtainProducerForProgressEvent() {
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .progressEventConsumerConfig(ConsumerConfig.newBuilder().setRedis(Redis.getDefaultInstance()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.PROGRESS_EVENT).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineProgressEvent(RedisEventConfig.builder().maxTopicSize(PIPELINE_PROGRESS_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    assertThatCode(() -> eventSenderMock.obtainProducer(producerCacheKey)).doesNotThrowAnyException();
  }
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testObtainProducerForNodeStartEvent() {
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .nodeStartEventConsumerConfig(ConsumerConfig.newBuilder().setRedis(Redis.getDefaultInstance()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.NODE_START).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineNodeStartEvent(
                RedisEventConfig.builder().maxTopicSize(PIPELINE_NODE_START_EVENT_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    assertThatCode(() -> eventSenderMock.obtainProducer(producerCacheKey)).doesNotThrowAnyException();
  }
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testObtainProducerNodeAdviseEvent() {
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .nodeAdviseEventConsumerConfig(ConsumerConfig.newBuilder().setRedis(Redis.getDefaultInstance()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.NODE_ADVISE).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineNodeAdviseEvent(
                RedisEventConfig.builder().maxTopicSize(PIPELINE_NODE_ADVISE_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    assertThatCode(() -> eventSenderMock.obtainProducer(producerCacheKey)).doesNotThrowAnyException();
  }
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testObtainProducerForNodeResumeEvent() {
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .nodeResumeEventConsumerConfig(ConsumerConfig.newBuilder().setRedis(Redis.getDefaultInstance()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.NODE_RESUME).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineNodeResumeEvent(
                RedisEventConfig.builder().maxTopicSize(PIPELINE_NODE_RESUME_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    assertThatCode(() -> eventSenderMock.obtainProducer(producerCacheKey)).doesNotThrowAnyException();
  }
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testObtainProducerForPartialPlanEvent() {
    PmsSdkInstance pmsSdkInstance = PmsSdkInstance.builder()
                                        .startPlanCreationEventConsumerConfig(
                                            ConsumerConfig.newBuilder().setRedis(Redis.getDefaultInstance()).build())
                                        .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.CREATE_PARTIAL_PLAN).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineStartPartialPlanCreator(
                RedisEventConfig.builder().maxTopicSize(START_PARTIAL_PLAN_CREATOR_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    assertThatCode(() -> eventSenderMock.obtainProducer(producerCacheKey)).doesNotThrowAnyException();
  }

  private static Ambiance getAmbiance() {
    Level sectionLevel = Level.newBuilder()
                             .setRuntimeId(RUNTIME_ID)
                             .setSetupId(RUNTIME_ID)
                             .setStepType(StepType.newBuilder()
                                              .setType(StepSpecTypeConstants.INIT_CONTAINER_STEP_V2)
                                              .setStepCategory(StepCategory.STEP)
                                              .build())
                             .setGroup("SECTION")
                             .build();
    List<Level> levels = new ArrayList<>();
    levels.add(sectionLevel);
    return Ambiance.newBuilder()
        .setPlanExecutionId(EXECUTION_ID)
        .putAllSetupAbstractions(ImmutableMap.of(
            "accountId", ACCOUNT_ID, "appId", APP_ID, "orgIdentifier", ORG_ID, "projectIdentifier", PROJECT_ID))
        .addAllLevels(levels)
        .build();
  }
}
