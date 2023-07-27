/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.events.base;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.RedisAbstractConsumer;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.PmsEventFrameworkConstants;

import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.redisson.api.RedissonClient;

@OwnedBy(HarnessTeam.PIPELINE)
public class NoopRedisConsumer extends RedisAbstractConsumer {
  public NoopRedisConsumer(String topicName, String groupName, RedissonClient client, int batchSize) {
    super(topicName, groupName, client, Duration.ofSeconds(10), batchSize, "local");
  }

  @Override
  public List<Message> read(Duration maxWaitTime) {
    return Arrays.asList(Message.newBuilder()
                             .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                             .putMetadata(PmsEventFrameworkConstants.SERVICE_NAME, "RANDOM_SERVICE")
                                             .setData(InterruptEvent.newBuilder()
                                                          .setAmbiance(buildAmbiance())
                                                          .setType(InterruptType.ABORT)
                                                          .build()
                                                          .toByteString())
                                             .build())
                             .build(),
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(PmsEventFrameworkConstants.SERVICE_NAME, "RANDOM_SERVICE")
                            .setData(InterruptEvent.newBuilder()
                                         .setAmbiance(buildAmbiance())
                                         .setType(InterruptType.ABORT)
                                         .build()
                                         .toByteString())
                            .build())
            .build());
  }

  @Override
  public void acknowledge(String messageId) {}

  @Override
  public void shutdown() {}

  public static Ambiance buildAmbiance() {
    Level level = Level.newBuilder()
                      .setRuntimeId("rid")
                      .setSetupId("sid")
                      .setStepType(StepType.newBuilder().setType("RANDOM").setStepCategory(StepCategory.STEP).build())
                      .setGroup("g")
                      .build();
    List<Level> levels = new ArrayList<>();
    levels.add(level);
    return Ambiance.newBuilder()
        .setPlanExecutionId("peid")
        .putAllSetupAbstractions(ImmutableMap.of("accountId", "aid", "orgId", "oid", "projectId", "pid"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234)
        .build();
  }
}
