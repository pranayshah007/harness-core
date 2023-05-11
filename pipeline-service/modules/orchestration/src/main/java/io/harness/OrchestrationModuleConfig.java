/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.waiter.PmsNotifyEventListener.PMS_ORCHESTRATION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.expressions.ExpressionEvaluatorProvider;
import io.harness.event.OrchestrationLogConfiguration;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.repositories.planExecutionJson.ExpandedJsonLockConfig;

import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationModuleConfig {
  @NonNull String serviceName;
  @NonNull ExpressionEvaluatorProvider expressionEvaluatorProvider;
  @Default int corePoolSize = 1;
  @Default int maxPoolSize = 5;
  @Default long idleTimeInSecs = 10;
  @Default String publisherName = PMS_ORCHESTRATION;
  @Default
  EventsFrameworkConfiguration eventsFrameworkConfiguration =
      EventsFrameworkConfiguration.builder()
          .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
          .build();
  boolean withPMS;
  boolean isPipelineService;
  boolean useFeatureFlagService;
  @Nullable io.harness.remote.client.ServiceHttpClientConfig accountServiceHttpClientConfig;
  @Nullable String accountServiceSecret;
  @Nullable String accountClientId;
  @Default
  OrchestrationRedisEventsConfig orchestrationRedisEventsConfig = OrchestrationRedisEventsConfig.builder().build();
  @Default
  OrchestrationLogConfiguration orchestrationLogConfiguration = OrchestrationLogConfiguration.builder().build();
  @Default
  OrchestrationRestrictionConfiguration orchestrationRestrictionConfiguration =
      OrchestrationRestrictionConfiguration.builder().build();
  ServiceHttpClientConfig licenseClientConfig;
  String licenseClientServiceSecret;
  String licenseClientId;

  ExpandedJsonLockConfig expandedJsonLockConfig;
}
