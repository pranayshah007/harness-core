/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.HandleStepResponseRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.rule.Owner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class HandleStepResponseRequestProcessorTest extends OrchestrationTestBase {
  @Mock OrchestrationEngine engine;
  @InjectMocks HandleStepResponseRequestProcessor handleStepResponseEventHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void verifyMocks() {
    Mockito.verifyNoMoreInteractions(engine);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEvent() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    HandleStepResponseRequest handleStepResponseRequest = HandleStepResponseRequest.newBuilder().build();
    handleStepResponseEventHandler.handleEvent(SdkResponseEventProto.newBuilder()
                                                   .setAmbiance(ambiance)
                                                   .setHandleStepResponseRequest(handleStepResponseRequest)
                                                   .setSdkResponseEventType(SdkResponseEventType.HANDLE_STEP_RESPONSE)
                                                   .build());
    verify(engine).processStepResponse(ambiance, handleStepResponseRequest.getStepResponse());
  }
}
