/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.shellscript;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.filters.WithFileRefs;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SimpleVisitorHelper(helperClass = ShellScriptProvisionStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.SHELL_SCRIPT_PROVISION)
@TypeAlias("shellScriptProvisionStepInfo")
@RecasterAlias("io.harness.cdng.provision.shellscript.ShellScriptProvisionStepInfo")
public class ShellScriptProvisionStepInfo
    extends ShellScriptProvisionBaseStepInfo implements CDAbstractStepInfo, Visitable, WithFileRefs {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  List<NGVariable> environmentVariables;

  @Builder(builderMethodName = "infoBuilder")
  public ShellScriptProvisionStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ShellScriptSourceWrapper source, List<NGVariable> environmentVariables) {
    super(source, delegateSelectors);
    this.environmentVariables = environmentVariables;
  }

  @Override
  public StepType getStepType() {
    return ShellScriptProvisionStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return ShellScriptProvisionStepParameters.infoBuilder()
        .environmentVariables(NGVariablesUtils.getMapOfVariables(environmentVariables, 0L))
        .source(source)
        .delegateSelectors(delegateSelectors)
        .build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public Map<String, ParameterField<List<String>>> extractFileRefs() {
    Map<String, ParameterField<List<String>>> fileRefMap = new HashMap<>();
    if (source != null) {
      ParameterField<List<String>> fileRefList = ParameterField.createValueField(source.fetchFileRefs());
      fileRefMap.put("source.spec.file", fileRefList);
    }
    return fileRefMap;
  }
}
