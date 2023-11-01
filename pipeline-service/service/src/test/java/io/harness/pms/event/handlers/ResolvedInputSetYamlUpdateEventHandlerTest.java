/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.handlers;

import static io.harness.rule.OwnerRule.VINICIUS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.OrchestrationStepTypes;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.helpers.YamlExpressionResolveHelper;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class ResolvedInputSetYamlUpdateEventHandlerTest extends CategoryTest {
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @Mock private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock private YamlExpressionResolveHelper yamlExpressionResolveHelper;

  @InjectMocks ResolvedInputSetYamlUpdateEventHandler resolvedInputSetYamlUpdateEventHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldTestHandleEvent() {
    String planExecutionId = UUIDGenerator.generateUuid();
    String inputSetYaml = "input-set-yaml";
    String resolvedInputSetYaml = "resolved-input-set-yaml";
    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(
                Ambiance.newBuilder()
                    .setPlanExecutionId(planExecutionId)
                    .addLevels(Level.newBuilder()
                                   .setStepType(StepType.newBuilder().setType(OrchestrationStepTypes.PIPELINE_SECTION))
                                   .build())
                    .build())
            .eventType(OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE)
            .build();
    when(planExecutionMetadataService.getWithFieldsIncludedFromSecondary(
             planExecutionId, Sets.newHashSet(PlanExecutionMetadataKeys.inputSetYaml)))
        .thenReturn(PlanExecutionMetadata.builder().inputSetYaml(inputSetYaml).build());
    when(yamlExpressionResolveHelper.resolveExpressionsInYaml(anyString(), any(ResolveInputYamlType.class), any()))
        .thenReturn(resolvedInputSetYaml);
    resolvedInputSetYamlUpdateEventHandler.handleEvent(event);
    verify(pmsExecutionSummaryService, times(1)).updateResolvedUserInputSetYaml(planExecutionId, resolvedInputSetYaml);
  }
}