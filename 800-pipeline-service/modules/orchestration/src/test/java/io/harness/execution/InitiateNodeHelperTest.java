/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.InitiateNodeEvent;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InitiateNodeHelperTest extends OrchestrationTestBase {
  @Mock Producer producer;
  @Inject @InjectMocks InitiateNodeHelper initiateNodeHelper;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testPublishEvent() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build();
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    initiateNodeHelper.publishEvent(ambiance, setupId, runtimeId);

    ArgumentCaptor<Message> mCaptor = ArgumentCaptor.forClass(Message.class);
    verify(producer).send(mCaptor.capture());

    InitiateNodeEvent event =
        InitiateNodeEvent.newBuilder().setAmbiance(ambiance).setNodeId(setupId).setRuntimeId(runtimeId).build();
    assertThat(mCaptor.getValue().getData()).isEqualTo(event.toByteString());
  }
}