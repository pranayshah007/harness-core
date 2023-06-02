/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.events;

import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationEventEmitterTest extends OrchestrationTestBase {
  @Mock private PmsEventSender eventSender;

  private OrchestrationEventEmitter orchestrationEventEmitter;

  @Before
  public void setUp() {
    orchestrationEventEmitter = new OrchestrationEventEmitter();
    Reflect.on(orchestrationEventEmitter).set("eventSender", eventSender);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void emitEvent() {
    OrchestrationEvent orchestrationEvent = OrchestrationEvent.newBuilder()
                                                .setAmbiance(Ambiance.newBuilder().build())
                                                .setEventType(OrchestrationEventType.ORCHESTRATION_START)
                                                .setServiceName("serviceName")
                                                .build();
    when(eventSender.sendEvent(any(Ambiance.class), any(ByteString.class), eq(PmsEventCategory.ORCHESTRATION_EVENT),
             eq("serviceName"), eq(true)))
        .thenReturn(null);

    orchestrationEventEmitter.emitEvent(orchestrationEvent);

    verify(eventSender)
        .sendEvent(any(Ambiance.class), any(ByteString.class), eq(PmsEventCategory.ORCHESTRATION_EVENT),
            eq("serviceName"), eq(true));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void emitEventShouldThrowException() {
    OrchestrationEvent orchestrationEvent = OrchestrationEvent.newBuilder()
                                                .setAmbiance(Ambiance.newBuilder().build())
                                                .setEventType(OrchestrationEventType.ORCHESTRATION_START)
                                                .setServiceName("serviceName")
                                                .build();

    when(eventSender.sendEvent(any(Ambiance.class), any(ByteString.class), eq(PmsEventCategory.ORCHESTRATION_EVENT),
             eq("serviceName"), eq(true)))
        .thenAnswer(invocation -> { throw new Exception(); });

    assertThatThrownBy(() -> orchestrationEventEmitter.emitEvent(orchestrationEvent)).isInstanceOf(Exception.class);

    verify(eventSender)
        .sendEvent(any(Ambiance.class), any(ByteString.class), eq(PmsEventCategory.ORCHESTRATION_EVENT),
            eq("serviceName"), eq(true));
  }
}
