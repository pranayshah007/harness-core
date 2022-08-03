/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_FACILITATOR_EVENT_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_INTERRUPT_EVENT_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_ADVISE_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_RESUME_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_START_EVENT_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_ORCHESTRATION_EVENT_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_PROGRESS_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.START_PARTIAL_PLAN_CREATOR_MAX_TOPIC_SIZE;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
public class OrchestrationRedisEventsConfig {
  @Builder.Default
  RedisEventConfig pipelineInterruptEvent =
      RedisEventConfig.builder().maxTopicSize(PIPELINE_INTERRUPT_EVENT_MAX_TOPIC_SIZE).build();
  @Builder.Default
  RedisEventConfig pipelineOrchestrationEvent =
      RedisEventConfig.builder().maxTopicSize(PIPELINE_ORCHESTRATION_EVENT_MAX_TOPIC_SIZE).build();
  @Builder.Default
  RedisEventConfig pipelineFacilitatorEvent =
      RedisEventConfig.builder().maxTopicSize(PIPELINE_FACILITATOR_EVENT_MAX_TOPIC_SIZE).build();
  @Builder.Default
  RedisEventConfig pipelineNodeStartEvent =
      RedisEventConfig.builder().maxTopicSize(PIPELINE_NODE_START_EVENT_MAX_TOPIC_SIZE).build();
  @Builder.Default
  RedisEventConfig pipelineProgressEvent =
      RedisEventConfig.builder().maxTopicSize(PIPELINE_PROGRESS_MAX_TOPIC_SIZE).build();
  @Builder.Default
  RedisEventConfig pipelineNodeAdviseEvent =
      RedisEventConfig.builder().maxTopicSize(PIPELINE_NODE_ADVISE_MAX_TOPIC_SIZE).build();
  @Builder.Default
  RedisEventConfig pipelineNodeResumeEvent =
      RedisEventConfig.builder().maxTopicSize(PIPELINE_NODE_RESUME_MAX_TOPIC_SIZE).build();
  @Builder.Default
  RedisEventConfig pipelineStartPartialPlanCreator =
      RedisEventConfig.builder().maxTopicSize(START_PARTIAL_PLAN_CREATOR_MAX_TOPIC_SIZE).build();
}
