/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions;

import io.harness.ModuleType;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.expressions.AmbianceExpressionEvaluator;
import io.harness.engine.expressions.functors.NodeExecutionEntityType;
import io.harness.engine.expressions.functors.StrategyFunctor;
import io.harness.expression.VariableResolverTracker;
import io.harness.ngtriggers.expressions.functors.EventPayloadFunctor;
import io.harness.ngtriggers.expressions.functors.TriggerFunctor;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.expression.RemoteFunctorServiceGrpc.RemoteFunctorServiceBlockingStub;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.expressions.functors.AccountFunctor;
import io.harness.pms.expressions.functors.ExecutionInputExpressionFunctor;
import io.harness.pms.expressions.functors.InputSetFunctor;
import io.harness.pms.expressions.functors.OrgFunctor;
import io.harness.pms.expressions.functors.PipelineExecutionFunctor;
import io.harness.pms.expressions.functors.ProjectFunctor;
import io.harness.pms.expressions.functors.RemoteExpressionFunctor;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.project.remote.ProjectClient;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSExpressionEvaluator extends AmbianceExpressionEvaluator {
  @Inject Map<ModuleType, RemoteFunctorServiceBlockingStub> remoteFunctorServiceBlockingStubMap;
  @Inject @Named("PRIVILEGED") private AccountClient accountClient;
  @Inject @Named("PRIVILEGED") private OrganizationClient organizationClient;
  @Inject @Named("PRIVILEGED") private ProjectClient projectClient;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private PMSExecutionService pmsExecutionService;
  @Inject PmsSdkInstanceService pmsSdkInstanceService;
  @Inject PipelineExpressionHelper pipelineExpressionHelper;
  @Inject ExecutionInputService executionInputService;

  @Inject PmsExecutionSummaryService pmsExecutionSummaryService;

  public PMSExpressionEvaluator(VariableResolverTracker variableResolverTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific, Map<String, String> contextMap) {
    super(variableResolverTracker, ambiance, entityTypes, refObjectSpecific, contextMap);
  }

  @Override
  protected void initialize() {
    super.initialize();
    // NG access functors
    addToContext("account", new AccountFunctor(accountClient, ambiance));
    addToContext("org", new OrgFunctor(organizationClient, ambiance));
    addToContext("project", new ProjectFunctor(projectClient, ambiance));

    addToContext("pipeline",
        new PipelineExecutionFunctor(
            pmsExecutionService, pipelineExpressionHelper, planExecutionMetadataService, ambiance));
    addToContext("executionInput", new ExecutionInputExpressionFunctor(executionInputService, ambiance));

    addToContext("strategy", new StrategyFunctor(ambiance, nodeExecutionsCache));
    addToContext("inputSet", new InputSetFunctor(pmsExecutionSummaryService, ambiance));

    // Trigger functors
    addToContext(SetupAbstractionKeys.eventPayload, new EventPayloadFunctor(ambiance, planExecutionMetadataService));
    addToContext(SetupAbstractionKeys.trigger, new TriggerFunctor(ambiance, planExecutionMetadataService));
    Map<String, PmsSdkInstance> cacheValueMap = pmsSdkInstanceService.getSdkInstanceCacheValue();
    cacheValueMap.values().forEach(e -> {
      for (Map.Entry<String, String> entry : CollectionUtils.emptyIfNull(e.getStaticAliases()).entrySet()) {
        addStaticAlias(entry.getKey(), entry.getValue());
      }
    });

    cacheValueMap.forEach((key, value) -> {
      for (String functorKey : CollectionUtils.emptyIfNull(value.getSdkFunctors())) {
        addToContext(functorKey,
            RemoteExpressionFunctor.builder()
                .ambiance(ambiance)
                .remoteFunctorServiceBlockingStub(remoteFunctorServiceBlockingStubMap.get(ModuleType.fromString(key)))
                .functorKey(functorKey)
                .build());
      }
    });

    // Group aliases
    // TODO: Replace with step category
    addGroupAlias(YAMLFieldNameConstants.STAGE, StepOutcomeGroup.STAGE.name());
    addGroupAlias(YAMLFieldNameConstants.STEP, StepOutcomeGroup.STEP.name());
    addGroupAlias(YAMLFieldNameConstants.STEP_GROUP, StepCategory.STEP_GROUP.name());
  }
}
