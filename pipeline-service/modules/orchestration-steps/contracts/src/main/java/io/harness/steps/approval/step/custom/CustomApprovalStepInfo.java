/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.custom;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapper;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonTypeName(StepSpecTypeConstants.CUSTOM_APPROVAL)
@TypeAlias("customApprovalStepInfo")
@RecasterAlias("io.harness.steps.approval.step.custom.CustomApprovalStepInfo")
public class CustomApprovalStepInfo implements PMSStepInfo, WithDelegateSelector {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull ShellType shell;

  @NotNull ShellScriptSourceWrapper source;

  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Pattern(regexp = NGRegexValidatorConstants.TIMEOUT_PATTERN)
  @VariableExpression(skipInnerObjectTraversal = true)
  ParameterField<Timeout> scriptTimeout;

  @VariableExpression(skipVariableExpression = true) List<NGVariable> outputVariables;

  List<NGVariable> environmentVariables;

  @YamlSchemaTypes(value = {expression})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Pattern(regexp = NGRegexValidatorConstants.TIMEOUT_PATTERN)
  @VariableExpression(skipInnerObjectTraversal = true)
  ParameterField<Timeout> retryInterval;

  @NotNull @VariableExpression(skipVariableExpression = true) CriteriaSpecWrapper approvalCriteria;
  @VariableExpression(skipVariableExpression = true) CriteriaSpecWrapper rejectionCriteria;

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.CUSTOM_APPROVAL_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return StepSpecTypeConstants.APPROVAL_FACILITATOR;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return io.harness.steps.approval.step.custom.CustomApprovalSpecParameters.builder()
        .retryInterval(getRetryInterval())
        .outputVariables(NGVariablesUtils.getMapOfVariablesWithoutSecretExpression(outputVariables))
        .environmentVariables(NGVariablesUtils.getMapOfVariables(environmentVariables, 0L))
        .secretOutputVariables(NGVariablesUtils.getSetOfSecretVars(outputVariables))
        .shellType(getShell())
        .source(getSource())
        .delegateSelectors(getDelegateSelectors())
        .approvalCriteria(getApprovalCriteria())
        .rejectionCriteria(getRejectionCriteria())
        .scriptTimeout(getScriptTimeout())
        .build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public ExpressionMode getExpressionMode() {
    return ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED;
  }
}
