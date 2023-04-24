/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.InitiateNodeEvent;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.HashMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InitiateNodeHandlerTest extends OrchestrationTestBase {
  @Mock OrchestrationEngine engine;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Inject @InjectMocks private InitiateNodeHandler initiateNodeHandler;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void extractMetricContext() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(generateUuid())
                                  .setRuntimeId(generateUuid())
                                  .build();
    assertThat(initiateNodeHandler.extractMetricContext(new HashMap<>(), event))
        .isEqualTo(ImmutableMap.of("eventType", "TRIGGER_NODE"));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void getMetricPrefix() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(generateUuid())
                                  .setRuntimeId(generateUuid())
                                  .build();
    assertThat(initiateNodeHandler.getMetricPrefix(event)).isEqualTo("trigger_node_event");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void extraLogProperties() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(generateUuid())
                                  .setRuntimeId(generateUuid())
                                  .build();
    assertThat(initiateNodeHandler.extraLogProperties(event)).isEqualTo(ImmutableMap.of());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void extractAmbiance() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(generateUuid())
                                  .setRuntimeId(generateUuid())
                                  .build();
    assertThat(initiateNodeHandler.extractAmbiance(event)).isEqualTo(ambiance);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void handleEventWithContextWithMatrixFeatureEnabled() {
    when(pmsFeatureFlagService.isEnabled(eq("accountId"), any(FeatureName.class))).thenReturn(true);
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId(generateUuid()).putSetupAbstractions("accountId", "accountId").build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(generateUuid())
                                  .setRuntimeId(generateUuid())
                                  .setInitiateMode(InitiateMode.CREATE_AND_START)
                                  .build();
    initiateNodeHandler.handleEventWithContext(event);
    verify(engine).initiateNode(eq(ambiance), eq(event.getNodeId()), eq(event.getRuntimeId()), eq(null), eq(null),
        eq(InitiateMode.CREATE_AND_START));
  }
}