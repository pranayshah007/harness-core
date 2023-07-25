/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;
import static io.harness.pms.contracts.execution.events.OrchestrationEventType.NODE_EXECUTION_START;
import static io.harness.pms.contracts.execution.events.OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE;
import static io.harness.pms.contracts.execution.events.OrchestrationEventType.ORCHESTRATION_END;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.events.PipelineStageExecutionUpdateEventHandler;
import io.harness.engine.events.PipelineStepExecutionUpdateEventHandler;
import io.harness.engine.expressions.usages.ExpressionUsagesEventHandler;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;

import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationExecutionPmsEventHandlerRegistrar {
  public static Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> getEngineEventHandlers() {
    Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> handlerMap = new HashMap<>();
    handlerMap.put(ORCHESTRATION_END, Sets.newHashSet(ExpressionUsagesEventHandler.class));
    handlerMap.put(NODE_EXECUTION_STATUS_UPDATE,
        Sets.newHashSet(PipelineStageExecutionUpdateEventHandler.class, PipelineStepExecutionUpdateEventHandler.class));
    handlerMap.put(NODE_EXECUTION_START, Sets.newHashSet(PipelineStepExecutionUpdateEventHandler.class));

    return handlerMap;
  }
}
