/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.interrupt;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.pms.sdk.core.supporter.children.TestChildChainStep;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class InterruptEventHandlerTest extends PmsSdkCoreTestBase {
  @Mock private PMSInterruptService pmsInterruptService;
  @Mock private StepRegistry stepRegistry;

  @InjectMocks InterruptEventHandler interruptEventHandler;

  @Before
  public void setup() {
    Mockito.when(stepRegistry.obtain(TestChildChainStep.STEP_TYPE)).thenReturn(new TestChildChainStep());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractLogProperties() {
    InterruptEvent event = InterruptEvent.newBuilder()
                               .setAmbiance(AmbianceTestUtils.buildAmbiance())
                               .setType(InterruptType.ABORT_ALL)
                               .setInterruptUuid("interruptUuid")
                               .build();
    Map<String, String> autoLogMap = ImmutableMap.<String, String>builder()
                                         .put("interruptType", event.getType().name())
                                         .put("interruptUuid", event.getInterruptUuid())
                                         .put("notifyId", event.getNotifyId())
                                         .build();
    assertThat(interruptEventHandler.extraLogProperties(event)).isEqualTo(autoLogMap);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractAmbiance() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    InterruptEvent event = InterruptEvent.newBuilder()
                               .setAmbiance(ambiance)
                               .setType(InterruptType.ABORT_ALL)
                               .setInterruptUuid("interruptUuid")
                               .build();
    assertThat(interruptEventHandler.extractAmbiance(event)).isEqualTo(ambiance);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractMetricContext() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    InterruptEvent event = InterruptEvent.newBuilder()
                               .setAmbiance(ambiance)
                               .setType(InterruptType.ABORT_ALL)
                               .setInterruptUuid("interruptUuid")
                               .build();
    assertThat(interruptEventHandler.extractMetricContext(new HashMap<>(), event)).isEqualTo(ImmutableMap.of());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractMetricPrefix() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    InterruptEvent event = InterruptEvent.newBuilder()
                               .setAmbiance(ambiance)
                               .setType(InterruptType.ABORT_ALL)
                               .setInterruptUuid("interruptUuid")
                               .build();
    assertThat(interruptEventHandler.getMetricPrefix(event)).isNull();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleAbort() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    InterruptEvent event = InterruptEvent.newBuilder()
                               .setAmbiance(AmbianceUtils.cloneForFinish(ambiance,
                                   ambiance.getLevelsList()
                                       .get(ambiance.getLevelsList().size() - 1)
                                       .toBuilder()
                                       .setStepType(TestChildChainStep.STEP_TYPE)
                                       .build()))

                               .setType(InterruptType.ABORT)
                               .setInterruptUuid("interruptUuid")
                               .setNotifyId("notifyId")
                               .build();
    interruptEventHandler.handleEventWithContext(event);
    Mockito.verify(pmsInterruptService).handleAbort("notifyId");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleFailure() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    InterruptEvent event = InterruptEvent.newBuilder()
                               .setAmbiance(AmbianceUtils.cloneForFinish(ambiance,
                                   ambiance.getLevelsList()
                                       .get(ambiance.getLevelsList().size() - 1)
                                       .toBuilder()
                                       .setStepType(TestChildChainStep.STEP_TYPE)
                                       .build()))
                               .setType(InterruptType.CUSTOM_FAILURE)
                               .setInterruptUuid("interruptUuid")
                               .setNotifyId("notifyId")
                               .build();
    interruptEventHandler.handleEventWithContext(event);
    Mockito.verify(pmsInterruptService).handleFailure("notifyId");
  }
}
