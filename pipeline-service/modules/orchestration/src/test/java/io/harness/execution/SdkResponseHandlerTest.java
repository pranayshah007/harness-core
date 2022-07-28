/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
@OwnedBy(HarnessTeam.PIPELINE)
public class SdkResponseHandlerTest extends OrchestrationTestBase {
  String planExecutionId = generateUuid();
  SdkResponseEventProto event = SdkResponseEventProto.newBuilder()
                                    .setSdkResponseEventType(SdkResponseEventType.ADD_EXECUTABLE_RESPONSE)
                                    .setAmbiance(Ambiance.newBuilder()
                                                     .setPlanExecutionId(planExecutionId)
                                                     .addLevels(Level.newBuilder().setRuntimeId("RID").build())

                                                     .build())
                                    .build();
  @Mock OrchestrationEngine engine;
  @Inject @InjectMocks SdkResponseHandler sdkResponseHandler;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testExtraLogProperties() {
    Map<String, String> map = sdkResponseHandler.extraLogProperties(event);
    assertEquals(map.get("eventType"), SdkResponseEventType.ADD_EXECUTABLE_RESPONSE.name());
    assertEquals(map.get("nodeExecutionId"), "RID");
    assertEquals(map.get("planExecutionId"), planExecutionId);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testExtractAmbiance() {
    Ambiance ambiance = sdkResponseHandler.extractAmbiance(event);
    assertEquals(ambiance.getPlanExecutionId(), planExecutionId);
    assertEquals(ambiance.getLevels(0).getRuntimeId(), "RID");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testExtractMetricContext() {
    Map<String, String> map = sdkResponseHandler.extractMetricContext(new HashMap<>(), event);
    assertEquals(map.get("eventType"), SdkResponseEventType.ADD_EXECUTABLE_RESPONSE.name());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetMetricPrefix() {
    assertEquals(sdkResponseHandler.getMetricPrefix(event), "sdk_response_event");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleEventWithContext() {
    sdkResponseHandler.handleEventWithContext(event);
    ArgumentCaptor<SdkResponseEventProto> mCaptor = ArgumentCaptor.forClass(SdkResponseEventProto.class);
    verify(engine).handleSdkResponseEvent(mCaptor.capture());
    assertEquals(mCaptor.getValue().toByteString(), event.toByteString());
  }
}
