/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.revertpr;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
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

@OwnedBy(GITOPS)
@Data
@NoArgsConstructor
@JsonTypeName(StepSpecTypeConstants.GITOPS_REVERT_PR)
@TypeAlias("RevertPRStepInfo")
@RecasterAlias("io.harness.cdng.gitops.revertpr.RevertPRStepInfo")
public class RevertPRStepInfo extends RevertPRBaseStepInfo implements CDAbstractStepInfo, Visitable {
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder(builderMethodName = "infoBuilder")
  public RevertPRStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors, ParameterField<String> commitId,
      ParameterField<String> prTitle) {
    super(delegateSelectors, commitId, prTitle);
  }

  @Override
  public StepType getStepType() {
    return RevertPRStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public SpecParameters getSpecParameters() {
    return RevertPRStepParameters.infoBuilder()
        .delegateSelectors(getDelegateSelectors())
        .commitId(commitId)
        .prTitle(prTitle)
        .build();
  }
}
