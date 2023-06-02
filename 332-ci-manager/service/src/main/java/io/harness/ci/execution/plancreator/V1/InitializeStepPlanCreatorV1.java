/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator.V1;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.InitializeStepNode;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.ci.integrationstage.BuildJobEnvInfoBuilder;
import io.harness.ci.integrationstage.VmInitializeTaskParamsBuilder;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@OwnedBy(HarnessTeam.CI)
public class InitializeStepPlanCreatorV1 extends CIPMSStepPlanCreatorV2<InitializeStepNode> {
  private final String InitializeDisplayName = "Initialize";
  @Inject private BuildJobEnvInfoBuilder buildJobEnvInfoBuilder;

  public PlanCreationResponse createPlan(PlanCreationContext ctx, AbstractStageNode abstractStageNode,
      CodeBase codebase, Infrastructure infrastructure, List<ExecutionWrapperConfig> executionWrapperConfigs,
      String childID) {
    // create PluginStepNode
    InitializeStepNode initializeStepNode =
        getStepNode(ctx, codebase, infrastructure, abstractStageNode, executionWrapperConfigs);
    // create Plan node
    return createInternalStepPlan(ctx, initializeStepNode, childID);
  }

  private InitializeStepNode getStepNode(PlanCreationContext ctx, CodeBase codeBase, Infrastructure infrastructure,
      AbstractStageNode abstractStageNode, List<ExecutionWrapperConfig> executionWrapperConfigs) {
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .identifier(InitializeStepInfo.STEP_TYPE.getType())
            .name(InitializeStepInfo.STEP_TYPE.getType())
            .infrastructure(infrastructure)
            .stageIdentifier(abstractStageNode.getIdentifier())
            .variables(abstractStageNode.getVariables())
            .stageElementConfig(IntegrationStageConfigImpl.builder()
                                    .uuid(IdentifierGeneratorUtils.getId(abstractStageNode.getName()))
                                    .execution(ExecutionElementConfig.builder().steps(executionWrapperConfigs).build())
                                    .infrastructure(infrastructure)
                                    .cloneCodebase(ParameterField.createValueField(codeBase != null))
                                    .serviceDependencies(ParameterField.createValueField(Collections.emptyList()))
                                    .build())
            .ciCodebase(codeBase)
            .skipGitClone(codeBase == null)
            .executionElementConfig(ExecutionElementConfig.builder().steps(executionWrapperConfigs).build())
            .timeout(buildJobEnvInfoBuilder.getTimeout(infrastructure, ctx.getAccountIdentifier()))
            .build();

    return InitializeStepNode.builder()
        .identifier(InitializeStepInfo.STEP_TYPE.getType())
        .name(InitializeDisplayName)
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

    if (infrastructure.getType() == Infrastructure.Type.VM) {
      VmInitializeTaskParamsBuilder.validateInfrastructure(infrastructure);
      VmPoolYaml vmPoolYaml = (VmPoolYaml) ((VmInfraYaml) infrastructure).getSpec();
      return parseTimeout(vmPoolYaml.getSpec().getInitTimeout(), "15m");
    } else if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
      if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
        throw new CIStageExecutionException("Input infrastructure can not be empty");
      }
      ParameterField<String> timeout = ((K8sDirectInfraYaml) infrastructure).getSpec().getInitTimeout();
      return parseTimeout(timeout, "10m");
    }
    return ParameterField.createValueField(Timeout.fromString("10m"));
  }

  private ParameterField<Timeout> parseTimeout(ParameterField<String> timeout, String defaultTimeout) {
    if (timeout != null && timeout.fetchFinalValue() != null && isNotEmpty((String) timeout.fetchFinalValue())) {
      return ParameterField.createValueField(Timeout.fromString((String) timeout.fetchFinalValue()));
    } else {
      return ParameterField.createValueField(Timeout.fromString(defaultTimeout));
    }
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
