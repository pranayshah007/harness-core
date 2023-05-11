/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.commons.events;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.DUMMY_REDIS_URL;
import static io.harness.pms.events.PmsEventFrameworkConstants.PIPELINE_MONITORING_ENABLED;
import static io.harness.pms.events.PmsEventFrameworkConstants.SERVICE_NAME;
import static io.harness.steps.StepSpecTypeConstants.INIT_CONTAINER_V2_STEP_TYPE;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.ModuleType;
import io.harness.OrchestrationModuleConfig;
import io.harness.OrchestrationRedisEventsConfig;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.RedisProducerFactory;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ConsumerConfig;
import io.harness.pms.contracts.plan.ConsumerConfig.ConfigCase;
import io.harness.pms.contracts.plan.Redis;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstance.PmsSdkInstanceKeys;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.RetryUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PmsEventSender {
  private static final RetryPolicy<Object> retryPolicy = RetryUtils.getRetryPolicy("Error Getting Producer..Retrying",
      "Failed to obtain producer", Collections.singletonList(ExecutionException.class), Duration.ofMillis(10), 3, log);
  @Inject private MongoTemplate mongoTemplate;
  @Inject private OrchestrationModuleConfig moduleConfig;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private RedisProducerFactory redisProducerFactory;

  private final LoadingCache<ProducerCacheKey, Producer> producerCache =
      CacheBuilder.newBuilder()
          .maximumSize(100)
          .expireAfterAccess(300, TimeUnit.MINUTES)
          .build(new CacheLoader<ProducerCacheKey, Producer>() {
            @Override
            public Producer load(@NotNull ProducerCacheKey cacheKey) {
              return obtainProducer(cacheKey);
            }
          });

  public String sendEvent(Ambiance ambiance, ByteString eventData, PmsEventCategory eventCategory, String serviceName,
      boolean isMonitored) {
    StepType stepType = AmbianceUtils.getCurrentStepType(ambiance);
    if (stepType != null && INIT_CONTAINER_V2_STEP_TYPE.getType().equals(stepType.getType())) {
      serviceName = ModuleType.PMS.name().toLowerCase();
    }
    log.info("Sending {} event for {} to the producer", eventCategory, serviceName);
    ImmutableMap.Builder<String, String> metadataBuilder = ImmutableMap.<String, String>builder()
                                                               .put(SERVICE_NAME, serviceName)
                                                               .putAll(AmbianceUtils.logContextMap(ambiance));
    Producer producer = obtainProducer(eventCategory, serviceName);
    if (isMonitored) {
      metadataBuilder.put(PIPELINE_MONITORING_ENABLED, "true");
    }

    String messageId =
        producer.send(Message.newBuilder().putAllMetadata(metadataBuilder.build()).setData(eventData).build());
    log.info("Successfully Sent {} event for {} to the producer. MessageId {}", eventCategory, serviceName, messageId);
    return messageId;
  }

  public String sendEvent(
      ByteString eventData, Map<String, String> metadataMap, PmsEventCategory eventCategory, String serviceName) {
    log.info("Sending {} event for {} to the producer", eventCategory, serviceName);
    Producer producer = obtainProducer(eventCategory, serviceName);
    metadataMap.put(SERVICE_NAME, serviceName);
    String messageId = producer.send(Message.newBuilder().putAllMetadata(metadataMap).setData(eventData).build());
    log.info("Successfully Sent {} event for {} to the producer. MessageId {}", eventCategory, serviceName, messageId);
    return messageId;
  }

  @VisibleForTesting
  Producer obtainProducer(PmsEventCategory eventCategory, String serviceName) {
    Producer producer =
        Failsafe.with(retryPolicy)
            .get(()
                     -> producerCache.get(
                         ProducerCacheKey.builder().eventCategory(eventCategory).serviceName(serviceName).build()));
    if (producer == null) {
      throw new RuntimeException("Cannot create Event Framework producer");
    }
    return producer;
  }

  @VisibleForTesting
  Producer obtainProducer(ProducerCacheKey cacheKey) {
    PmsSdkInstance instance = getPmsSdkInstance(cacheKey.getServiceName());
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig = moduleConfig.getOrchestrationRedisEventsConfig();
    switch (cacheKey.getEventCategory()) {
      case INTERRUPT_EVENT:
        return extractProducer(instance.getInterruptConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineInterruptEvent().getMaxTopicSize());
      case ORCHESTRATION_EVENT:
        return extractProducer(instance.getOrchestrationEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineOrchestrationEvent().getMaxTopicSize());
      case FACILITATOR_EVENT:
        return extractProducer(instance.getFacilitatorEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineFacilitatorEvent().getMaxTopicSize());
      case NODE_START:
        return extractProducer(instance.getNodeStartEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineNodeStartEvent().getMaxTopicSize());
      case PROGRESS_EVENT:
        return extractProducer(instance.getProgressEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineProgressEvent().getMaxTopicSize());
      case NODE_ADVISE:
        return extractProducer(instance.getNodeAdviseEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineNodeAdviseEvent().getMaxTopicSize());
      case NODE_RESUME:
        return extractProducer(instance.getNodeResumeEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineNodeResumeEvent().getMaxTopicSize());
      case CREATE_PARTIAL_PLAN:
        return extractProducer(instance.getStartPlanCreationEventConsumerConfig(),
            orchestrationRedisEventsConfig.getPipelineStartPartialPlanCreator().getMaxTopicSize());
      default:
        throw new InvalidRequestException("Invalid Event Category while obtaining Producer");
    }
  }

  private Producer extractProducer(ConsumerConfig consumerConfig, int topicSize) {
    ConfigCase configCase = consumerConfig.getConfigCase();
    switch (configCase) {
      case REDIS:
        Redis redis = consumerConfig.getRedis();
        return buildRedisProducer(redis.getTopicName(), PIPELINE_SERVICE.getServiceId(), topicSize);
      case CONFIG_NOT_SET:
      default:
        throw new InvalidRequestException("No producer found for Config Case " + configCase.name());
    }
  }

  private Producer buildRedisProducer(String topicName, String serviceId, int topicSize) {
    RedisConfig redisConfig = moduleConfig.getEventsFrameworkConfiguration().getRedisConfig();
    return redisConfig.getRedisUrl().equals(DUMMY_REDIS_URL)
        ? NoOpProducer.of(topicName)
        : redisProducerFactory.createRedisProducer(topicName, RedissonClientFactory.getClient(redisConfig), topicSize,
            serviceId, redisConfig.getEnvNamespace());
  }

  PmsSdkInstance getPmsSdkInstance(String serviceName) {
    Query query = query(where(PmsSdkInstanceKeys.name).is(serviceName));
    PmsSdkInstance instance = mongoTemplate.findOne(query, PmsSdkInstance.class);
    if (instance == null) {
      throw new InvalidRequestException("Sdk Not registered for Service name" + serviceName);
    }
    return instance;
  }
}
