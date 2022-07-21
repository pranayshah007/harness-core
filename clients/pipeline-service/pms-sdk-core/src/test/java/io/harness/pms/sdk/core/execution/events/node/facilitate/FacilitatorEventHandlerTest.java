/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.events.node.facilitate;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.facilitators.FacilitatorEvent;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.NodeExecutionEventType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.execution.EngineObtainmentHelper;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.registries.FacilitatorRegistry;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.PIPELINE)
public class FacilitatorEventHandlerTest extends PmsSdkCoreTestBase {
  private static String NOTIFY_ID = "notifyId";

  @Mock FacilitatorRegistry facilitatorRegistry;
  @Mock EngineObtainmentHelper engineObtainmentHelper;
  @Mock SdkNodeExecutionService sdkNodeExecutionService;

  @InjectMocks FacilitatorEventHandler facilitatorEventHandler;

  private FacilitatorEvent facilitatorEvent;
  private Ambiance ambiance;

  @Before
  public void setup() {
    FacilitatorType facilitatorType = FacilitatorType.newBuilder().setType("Type1").build();
    Mockito.when(engineObtainmentHelper.obtainInputPackage(ambiance, new ArrayList<>()))
        .thenReturn(StepInputPackage.builder().build());
    Mockito.when(facilitatorRegistry.obtain(facilitatorType)).thenReturn(new Type1Facilitator());
    FacilitatorObtainment facilitatorObtainment = FacilitatorObtainment.newBuilder().setType(facilitatorType).build();
    ambiance = AmbianceTestUtils.buildAmbiance();
    facilitatorEvent = FacilitatorEvent.newBuilder()
                           .setAmbiance(ambiance)
                           .addFacilitatorObtainments(facilitatorObtainment)
                           .setNotifyId(NOTIFY_ID)
                           .setNodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                           .build();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testMetricPrefix() {
    assertThat(facilitatorEventHandler.getMetricPrefix(facilitatorEvent)).isEqualTo("facilitator_event");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractLogProperties() {
    Map<String, String> metricsMap = facilitatorEventHandler.extraLogProperties(facilitatorEvent);
    assertThat(metricsMap.isEmpty()).isFalse();
    assertThat(metricsMap.size()).isEqualTo(2);
    assertThat(metricsMap.get("eventType")).isEqualTo(NodeExecutionEventType.FACILITATE.name());
    assertThat(metricsMap.get("notifyId")).isEqualTo(NOTIFY_ID);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractAmbiance() {
    assertThat(facilitatorEventHandler.extractAmbiance(facilitatorEvent)).isEqualTo(ambiance);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEventWithContextWithNullResponse() {
    facilitatorEventHandler.handleEventWithContext(facilitatorEvent);
    Mockito.verify(sdkNodeExecutionService)
        .handleFacilitationResponse(ambiance, NOTIFY_ID, FacilitatorResponseProto.newBuilder().build());
  }

  private class Type1Facilitator implements Facilitator {
    Type1Facilitator() {}

    @Override
    public FacilitatorResponse facilitate(
        Ambiance ambiance, StepParameters stepParameters, byte[] parameters, StepInputPackage inputPackage) {
      return null;
    }
  }
}
