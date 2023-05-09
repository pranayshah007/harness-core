/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.UpdateReleaseRepoStepVisitorHelper;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.GITOPS)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.GITOPS_UPDATE_RELEASE_REPO)
@SimpleVisitorHelper(helperClass = UpdateReleaseRepoStepVisitorHelper.class)
@TypeAlias("UpdateReleaseRepoStepInfo")
@RecasterAlias("io.harness.cdng.gitops.UpdateReleaseRepoStepInfo")
public class UpdateReleaseRepoStepInfo extends UpdateReleaseRepoBaseStepInfo implements CDAbstractStepInfo, Visitable {
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;
  @VariableExpression(skipVariableExpression = true) List<NGVariable> variables;

  @Builder(builderMethodName = "infoBuilder")
  public UpdateReleaseRepoStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<Map<String, String>> stringMap, List<NGVariable> variables, ParameterField<String> prTitle) {
    super(stringMap, delegateSelectors, prTitle);
    this.variables = variables;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public StepType getStepType() {
    return UpdateReleaseRepoStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return UpdateReleaseRepoStepParams.infoBuilder()
        .stringMap(getStringMap())
        .delegateSelectors(getDelegateSelectors())
        .prTitle(prTitle)
        .variables(NGVariablesUtils.getMapOfVariablesWithoutSecretExpression(variables))
        .build();
  }

  @Override
  public ExpressionMode getExpressionMode() {
    return ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED;
  }
}
