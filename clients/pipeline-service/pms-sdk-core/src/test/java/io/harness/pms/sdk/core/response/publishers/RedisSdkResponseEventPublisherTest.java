/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.response.publishers;

import static io.harness.pms.events.PmsEventFrameworkConstants.PIPELINE_MONITORING_ENABLED;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.producer.Message;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.events.EventErrorRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class RedisSdkResponseEventPublisherTest {
  private static String PLAN_EXECUTION_ID = "planExecutionId";
  private static String NODE_EXECUTION_ID = "nodeExecutionId";

  RedisSdkResponseEventPublisher sdkResponseEventPublisher;
  Producer producer;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    producer = Mockito.mock(NoOpProducer.class);
    sdkResponseEventPublisher = new RedisSdkResponseEventPublisher(producer);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void publishEvent() {
    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder()
            .setSdkResponseEventType(SdkResponseEventType.ADD_EXECUTABLE_RESPONSE)
            .setEventErrorRequest(EventErrorRequest.newBuilder().build())
            .setAmbiance(Ambiance.newBuilder()
                             .setPlanExecutionId(PLAN_EXECUTION_ID)
                             .addLevels(Level.newBuilder().setRuntimeId(NODE_EXECUTION_ID).build())
                             .build())
            .build();
    Map<String, String> metadataMap = new HashMap<>();
    metadataMap.put(PIPELINE_MONITORING_ENABLED, "false");
    metadataMap.put("eventType", SdkResponseEventType.ADD_EXECUTABLE_RESPONSE.name());
    metadataMap.put("nodeExecutionId", NODE_EXECUTION_ID);
    metadataMap.put("planExecutionId", PLAN_EXECUTION_ID);
    sdkResponseEventPublisher.publishEvent(event);
    verify(producer).send(Message.newBuilder().putAllMetadata(metadataMap).setData(event.toByteString()).build());
  }
}
