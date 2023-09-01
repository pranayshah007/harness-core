/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.consumers.graph;

import static io.harness.consumers.graph.GraphUpdateRedisConsumer.ReadResult;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.pms.contracts.visualisation.log.OrchestrationLogEvent;
import io.harness.rule.Owner;
import io.harness.serializer.ProtoUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class GraphUpdateRedisConsumerTest extends OrchestrationVisualizationTestBase {
  @Inject @InjectMocks GraphUpdateRedisConsumer consumer;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testMapPlanExecutionToMessages() {
    String id1 = System.currentTimeMillis() + "-0";
    String id2 = System.currentTimeMillis() + "-1";
    String id3 = System.currentTimeMillis() + "-2";
    String id4 = System.currentTimeMillis() + "-3";

    String planExecutionId1 = generateUuid();
    String planExecutionId2 = generateUuid();
    io.harness.eventsframework.producer.Message logMessage1 =
        io.harness.eventsframework.producer.Message.newBuilder()
            .setData(OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId1).build().toByteString())
            .build();
    io.harness.eventsframework.producer.Message logMessage2 =
        io.harness.eventsframework.producer.Message.newBuilder()
            .setData(OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId2).build().toByteString())
            .build();
    List<Message> messages =
        ImmutableList.of(Message.newBuilder()
                             .setId(id1)
                             .setTimestamp(ProtoUtils.unixMillisToTimestamp(System.currentTimeMillis()))
                             .setMessage(logMessage1)
                             .build(),
            Message.newBuilder()
                .setId(id2)
                .setTimestamp(ProtoUtils.unixMillisToTimestamp(System.currentTimeMillis()))
                .setMessage(logMessage1)
                .build(),
            Message.newBuilder()
                .setId(id3)
                .setTimestamp(ProtoUtils.unixMillisToTimestamp(System.currentTimeMillis()))
                .setMessage(logMessage1)
                .build(),
            Message.newBuilder()
                .setId(id4)
                .setTimestamp(ProtoUtils.unixMillisToTimestamp(System.currentTimeMillis()))
                .setMessage(logMessage2)
                .build());
    ReadResult readResult = consumer.mapPlanExecutionToMessages(messages);

    assertThat(readResult.tobeAcked.length).isEqualTo(4);
    assertThat(readResult.tobeAcked).containsExactlyInAnyOrder(id1, id2, id3, id4);

    assertThat(readResult.planExecutionIds).hasSize(2);

    assertThat(readResult.planExecutionIds).containsExactlyInAnyOrder(planExecutionId1, planExecutionId2);
  }
}
