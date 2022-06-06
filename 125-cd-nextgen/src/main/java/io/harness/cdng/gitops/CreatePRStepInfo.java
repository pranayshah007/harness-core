/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.CreatePRStepVisitorHelper;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.shellscript.ShellType;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.GITOPS_CREATE_PR)
@SimpleVisitorHelper(helperClass = CreatePRStepVisitorHelper.class)
@TypeAlias("CreatePRStepInfo")
@RecasterAlias("io.harness.cdng.gitops.CreatePRStepInfo")
public class CreatePRStepInfo extends CreatePRBaseStepInfo implements CDStepInfo, Visitable {
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder(builderMethodName = "infoBuilder")
  public CreatePRStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<Map<String, String>> stringMap, CreatePRStepUpdateConfigScriptWrapper updateConfigScriptWrapper,
      ParameterField<StoreConfigWrapper> store, ParameterField<String> commitMessage,
      ParameterField<String> targetBranch, ParameterField<Boolean> isNewBranch, ParameterField<String> prTitle,
      ShellType shellType, ParameterField<Boolean> overrideConfig) {
    super(shellType, overrideConfig, stringMap, updateConfigScriptWrapper, delegateSelectors, store, commitMessage,
        targetBranch, isNewBranch, prTitle);
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public StepType getStepType() {
    return CreatePRStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return CreatePRStepParams.infoBuilder()
        .shellType(getShell())
        .overrideConfig(getOverrideConfig())
        .updateConfigScriptWrapper(this.getSource())
        .stringMap(getStringMap())
        .store(getStore())
        .commitMessage(getCommitMessage())
        .isNewBranch(getIsNewBranch())
        .prTitle(getPrTitle())
        .targetBranch(getTargetBranch())
        .build();
  }
}
