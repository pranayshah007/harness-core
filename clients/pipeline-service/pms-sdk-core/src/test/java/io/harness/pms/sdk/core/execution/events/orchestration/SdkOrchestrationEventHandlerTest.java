/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.events.orchestration;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.core.registries.OrchestrationEventHandlerRegistry;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableSet;
import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class SdkOrchestrationEventHandlerTest extends PmsSdkCoreTestBase {
  @InjectMocks SdkOrchestrationEventHandler sdkOrchestrationEventHandler;
  @Mock OrchestrationEventHandlerRegistry registry;
  @Mock ExecutorService executorService;

  private OrchestrationEvent orchestrationEvent;
  private Ambiance ambiance;

  @Before
  public void setup() {
    ambiance = AmbianceTestUtils.buildAmbiance();
    orchestrationEvent = OrchestrationEvent.newBuilder()
                             .setAmbiance(ambiance)
                             .setEventType(OrchestrationEventType.ORCHESTRATION_START)
                             .build();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractMetricContext() {
    Map<String, String> metricsMap =
        sdkOrchestrationEventHandler.extractMetricContext(new HashMap<>(), orchestrationEvent);
    assertThat(metricsMap.isEmpty()).isFalse();
    assertThat(metricsMap.size()).isEqualTo(3);
    assertThat(metricsMap.get("accountId")).isEqualTo(AmbianceTestUtils.ACCOUNT_ID);
    assertThat(metricsMap.get("orgIdentifier")).isEqualTo(AmbianceTestUtils.ORG_ID);
    assertThat(metricsMap.get("projectIdentifier")).isEqualTo(AmbianceTestUtils.PROJECT_ID);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testMetricPrefix() {
    assertThat(sdkOrchestrationEventHandler.getMetricPrefix(orchestrationEvent)).isEqualTo("orchestration_event");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractLogProperties() {
    Map<String, String> metricsMap = sdkOrchestrationEventHandler.extraLogProperties(orchestrationEvent);
    assertThat(metricsMap.isEmpty()).isFalse();
    assertThat(metricsMap.size()).isEqualTo(1);
    assertThat(metricsMap.get("eventType")).isEqualTo(OrchestrationEventType.ORCHESTRATION_START.name());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractAmbiance() {
    assertThat(sdkOrchestrationEventHandler.extractAmbiance(orchestrationEvent)).isEqualTo(ambiance);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEventWithContext() {
    when(registry.obtain(OrchestrationEventType.ORCHESTRATION_START))
        .thenReturn(ImmutableSet.of(new NoopOrchestrationEventHandler()));
    sdkOrchestrationEventHandler.handleEventWithContext(orchestrationEvent);
    verify(executorService).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testBuildSdkOrchestrationEvent() {
    assertThat(sdkOrchestrationEventHandler.buildSdkOrchestrationEvent(orchestrationEvent))
        .isEqualTo(io.harness.pms.sdk.core.events.OrchestrationEvent.builder()
                       .eventType(orchestrationEvent.getEventType())
                       .endTs(orchestrationEvent.getEndTs())
                       .moduleInfo(RecastOrchestrationUtils.fromJson(
                           orchestrationEvent.getModuleInfo().toStringUtf8(), PipelineModuleInfo.class))
                       .ambiance(orchestrationEvent.getAmbiance())
                       .status(orchestrationEvent.getStatus())
                       .resolvedStepParameters(RecastOrchestrationUtils.fromJson(
                           orchestrationEvent.getStepParameters().toStringUtf8(), StepParameters.class))
                       .serviceName(orchestrationEvent.getServiceName())
                       .triggerPayload(orchestrationEvent.getTriggerPayload())
                       .tags(new ArrayList<>())
                       .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testBuildSdkOrchestrationEventWithTags() {
    orchestrationEvent = orchestrationEvent.toBuilder().addTags("a").build();
    assertThat(sdkOrchestrationEventHandler.buildSdkOrchestrationEvent(orchestrationEvent))
        .isEqualTo(io.harness.pms.sdk.core.events.OrchestrationEvent.builder()
                       .eventType(orchestrationEvent.getEventType())
                       .endTs(orchestrationEvent.getEndTs())
                       .moduleInfo(RecastOrchestrationUtils.fromJson(
                           orchestrationEvent.getModuleInfo().toStringUtf8(), PipelineModuleInfo.class))

                       .ambiance(orchestrationEvent.getAmbiance())
                       .status(orchestrationEvent.getStatus())
                       .resolvedStepParameters(RecastOrchestrationUtils.fromJson(
                           orchestrationEvent.getStepParameters().toStringUtf8(), StepParameters.class))
                       .serviceName(orchestrationEvent.getServiceName())
                       .triggerPayload(orchestrationEvent.getTriggerPayload())
                       .tags(Lists.newArrayList("a"))
                       .build());
  }

  private static class NoopOrchestrationEventHandler implements OrchestrationEventHandler {
    NoopOrchestrationEventHandler() {}
    @Override
    public void handleEvent(io.harness.pms.sdk.core.events.OrchestrationEvent event) {
      // doNothing
    }
  }
}
