/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_SDK_RESPONSE_EVENT_MAX_TOPIC_SIZE;

import io.harness.RedisEventConfig;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
public class PipelineSdkRedisEventsConfig {
  @Default
  RedisEventConfig pipelineSdkResponseEvent =
      RedisEventConfig.builder().maxTopicSize(PIPELINE_SDK_RESPONSE_EVENT_MAX_TOPIC_SIZE).build();
}
