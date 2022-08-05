/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SuspendChainRequest;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.function.Consumer;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.query.Update;

public class SuspendChainRequestProcessorTest extends OrchestrationTestBase {
  @InjectMocks private SuspendChainRequestProcessor processor;

  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private OrchestrationEngine engine;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleEvent() {
    SdkResponseEventProto event = createEvent();
    processor.handleEvent(event);

    ArgumentCaptor<Consumer<Update>> captorConsumer = ArgumentCaptor.forClass(Consumer.class);
    verify(nodeExecutionService).updateV2(eq("runtimeId"), captorConsumer.capture());
    verify(engine).resumeNodeExecution(notNull(), eq(Collections.EMPTY_MAP), eq(true));
    assertThat(captorConsumer.getValue()).isNotNull();

    Update updateOperation = new Update();
    captorConsumer.getValue().accept(updateOperation);

    Document addToSet = updateOperation.getUpdateObject().get("$addToSet", Document.class);
    assertThat(addToSet).isNotNull();
    assertThat(addToSet.get("executableResponses")).isEqualTo(event.getSuspendChainRequest().getExecutableResponse());
  }

  // CREATES A VALID EVENT
  private SdkResponseEventProto createEvent() {
    return SdkResponseEventProto.newBuilder()
        .setAmbiance(Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("runtimeId").build()).build())
        .setSuspendChainRequest(SuspendChainRequest.newBuilder()
                                    .setExecutableResponse(ExecutableResponse.newBuilder().build())
                                    .setIsError(true)
                                    .putAllResponse(Collections.EMPTY_MAP)
                                    .build())
        .build();
  }
}