/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.updategitopsapp;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@OwnedBy(GITOPS)
@Data
@NoArgsConstructor
@JsonTypeName(StepSpecTypeConstants.UPDATE_GITOPS_APP)
@TypeAlias("UpdateGitOpsAppStepInfo")
@RecasterAlias("io.harness.cdng.gitops.updategitopsapp.UpdateGitOpsAppStepInfo")
public class UpdateGitOpsAppStepInfo extends UpdateGitOpsAppBaseStepInfo implements CDAbstractStepInfo, Visitable {
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder(builderMethodName = "infoBuilder")
  public UpdateGitOpsAppStepInfo(ParameterField<String> applicationName, ParameterField<String> agentId,
      ParameterField<String> targetRevision, ParameterField<HelmValues> helmValues,
      ParameterField<KustomizeValues> kustomizeValues) {
    super(applicationName, agentId, targetRevision, helmValues, kustomizeValues);
  }

  @Override
  public StepType getStepType() {
    return UpdateGitOpsAppStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return UpdateGitOpsAppStepParameters.infoBuilder()
        .applicationName(applicationName)
        .agentId(agentId)
        .targetRevision(targetRevision)
        .helm(helm)
        .kustomize(kustomize)
        .build();
  }

  // this step does not execute on delegate
  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return null;
  }

  @Override
  public void setDelegateSelectors(ParameterField<List<TaskSelectorYaml>> delegateSelectors) {}
}
