/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator.V1;

import static io.harness.ci.commonconstants.CIExecutionConstants.MAXIMUM_EXPANSION_LIMIT;
import static io.harness.ci.commonconstants.CIExecutionConstants.MAXIMUM_EXPANSION_LIMIT_FREE_ACCOUNT;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.steps.nodes.InitializeStepNode;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.integrationstage.BuildJobEnvInfoBuilder;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.license.CILicenseService;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.cimanager.stages.V1.IntegrationStageConfigImplV1;
import io.harness.cimanager.stages.V1.IntegrationStageNodeV1;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.matrix.ExpandedExecutionWrapperInfo;
import io.harness.steps.matrix.StrategyExpansionData;
import io.harness.steps.matrix.StrategyHelper;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@OwnedBy(HarnessTeam.CI)
public class InitializeStepPlanCreatorV1 extends CIPMSStepPlanCreatorV2<InitializeStepNode> {
  @Inject private StrategyHelper strategyHelper;
  @Inject private BuildJobEnvInfoBuilder buildJobEnvInfoBuilder;
  @Inject private CILicenseService ciLicenseService;

  public PlanCreationResponse createPlan(PlanCreationContext ctx, IntegrationStageNodeV1 integrationStageNodeV1,
      CodeBase codebase, ExecutionSource executionSource, List<ExecutionWrapperConfig> executionWrapperConfigs,
      String childID) {
    // create PluginStepNode
    InitializeStepNode initializeStepNode =
        getStepNode(ctx, codebase, integrationStageNodeV1, executionSource, executionWrapperConfigs);
    // create Plan node
    return createInternalStepPlan(ctx, initializeStepNode, childID);
  }

  private InitializeStepNode getStepNode(PlanCreationContext ctx, CodeBase codeBase,
      IntegrationStageNodeV1 integrationStageNodeV1, ExecutionSource executionSource,
      List<ExecutionWrapperConfig> executionWrapperConfigs) {
    String accountId = ctx.getAccountIdentifier();
    List<ExecutionWrapperConfig> expandedExecutionElement = new ArrayList<>();
    Map<String, StrategyExpansionData> strategyExpansionMap = new HashMap<>();

    LicensesWithSummaryDTO licensesWithSummaryDTO = ciLicenseService.getLicenseSummary(accountId);
    Optional<Integer> maxExpansionLimit = Optional.of(Integer.valueOf(MAXIMUM_EXPANSION_LIMIT));
    if (licensesWithSummaryDTO != null && licensesWithSummaryDTO.getEdition() == Edition.FREE) {
      maxExpansionLimit = Optional.of(Integer.valueOf(MAXIMUM_EXPANSION_LIMIT_FREE_ACCOUNT));
    }

    for (ExecutionWrapperConfig config : executionWrapperConfigs) {
      // Inject the envVariables before calling strategy expansion
      IntegrationStageUtils.injectLoopEnvVariables(config);
      ExpandedExecutionWrapperInfo expandedExecutionWrapperInfo =
          strategyHelper.expandExecutionWrapperConfig(config, maxExpansionLimit);
      expandedExecutionElement.addAll(expandedExecutionWrapperInfo.getExpandedExecutionConfigs());
      strategyExpansionMap.putAll(expandedExecutionWrapperInfo.getUuidToStrategyExpansionData());
    }
    IntegrationStageConfigImplV1 integrationStageConfigImplV1 = integrationStageNodeV1.getStageConfig();
    // TODO: use exact infra later
    Infrastructure infrastructure = IntegrationStageUtils.getInfrastructureV2();
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .identifier(InitializeStepInfo.STEP_TYPE.getType())
            .name(InitializeStepInfo.STEP_TYPE.getType())
            .infrastructure(infrastructure)
            .stageIdentifier(integrationStageNodeV1.getIdentifier())
            .variables(integrationStageNodeV1.getVariables())
            .stageElementConfig(IntegrationStageConfigImpl.builder()
                                    .uuid(integrationStageConfigImplV1.getUuid())
                                    .execution(ExecutionElementConfig.builder().steps(executionWrapperConfigs).build())
                                    .infrastructure(infrastructure)
                                    .cloneCodebase(ParameterField.createValueField(codeBase != null))
                                    .serviceDependencies(ParameterField.createValueField(Collections.emptyList()))
                                    .build())
            .executionSource(executionSource)
            .ciCodebase(codeBase)
            .skipGitClone(codeBase == null)
            .strategyExpansionMap(strategyExpansionMap)
            .executionElementConfig(ExecutionElementConfig.builder().steps(expandedExecutionElement).build())
            .timeout(buildJobEnvInfoBuilder.getTimeout(infrastructure))
            .build();

    return InitializeStepNode.builder()
        .identifier(InitializeStepInfo.STEP_TYPE.getType())
        .name(InitializeStepInfo.STEP_TYPE.getType())
        .uuid(generateUuid())
        .type(InitializeStepNode.StepType.liteEngineTask)
        .timeout(getTimeout(infrastructure))
        .initializeStepInfo(initializeStepInfo)
        .build();
  }

  private ParameterField<Timeout> getTimeout(Infrastructure infrastructure) {
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }
    // TODO: add infra check

    //    if (infrastructure.getType() == Infrastructure.Type.VM) {
    //      vmInitializeTaskParamsBuilder.validateInfrastructure(infrastructure);
    //      VmPoolYaml vmPoolYaml = (VmPoolYaml) ((VmInfraYaml) infrastructure).getSpec();
    //      return parseTimeout(vmPoolYaml.getSpec().getInitTimeout(), "15m");
    //    } else if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
    //      if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
    //        throw new CIStageExecutionException("Input infrastructure can not be empty");
    //      }
    //      ParameterField<String> timeout = ((K8sDirectInfraYaml) infrastructure).getSpec().getInitTimeout();
    //      return parseTimeout(timeout, "10m");
    //    } else {
    //      return ParameterField.createValueField(Timeout.fromString("10m"));
    //    }
    return ParameterField.createValueField(Timeout.fromString("10m"));
  }

  @Override
  public Set<String> getSupportedStepTypes() {
    return null;
  }

  @Override
  public Class<InitializeStepNode> getFieldClass() {
    return InitializeStepNode.class;
  }
}
