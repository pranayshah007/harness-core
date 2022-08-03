/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.EndPlanAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.AdviserResponseRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.rule.Owner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class AdviserResponseRequestProcessorTest extends CategoryTest {
  @Mock private OrchestrationEngine orchestrationEngine;
  @InjectMocks private AdviserResponseRequestProcessor adviserEventResponseHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void verifyInteractions() {
    Mockito.verifyNoMoreInteractions(orchestrationEngine);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleAdviseEvent() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    AdviserResponseRequest request = AdviserResponseRequest.newBuilder()
                                         .setAdviserResponse(AdviserResponse.newBuilder()
                                                                 .setType(AdviseType.END_PLAN)
                                                                 .setEndPlanAdvise(EndPlanAdvise.newBuilder().build())
                                                                 .build())
                                         .build();
    SdkResponseEventProto sdkResponseEventProto =
        SdkResponseEventProto.newBuilder()
            .setAmbiance(ambiance)
            .setSdkResponseEventType(SdkResponseEventType.HANDLE_ADVISER_RESPONSE)
            .setAdviserResponseRequest(request)
            .build();
    adviserEventResponseHandler.handleEvent(sdkResponseEventProto);

    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<AdviserResponse> responseCaptor = ArgumentCaptor.forClass(AdviserResponse.class);
    verify(orchestrationEngine).processAdviserResponse(ambianceCaptor.capture(), responseCaptor.capture());

    assertThat(ambianceCaptor.getValue()).isEqualTo(ambiance);
    assertThat(responseCaptor.getValue().getType()).isEqualTo(AdviseType.END_PLAN);
  }
}
