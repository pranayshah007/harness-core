/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.filters.WithConnectorRef;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapper;
import io.harness.steps.approval.step.beans.ServiceNowChangeWindowSpec;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@JsonTypeName(StepSpecTypeConstants.SERVICENOW_APPROVAL)
@TypeAlias("serviceNowApprovalStepInfo")
@RecasterAlias("io.harness.steps.approval.step.servicenow.ServiceNowApprovalStepInfo")
public class ServiceNowApprovalStepInfo implements PMSStepInfo, WithConnectorRef, WithDelegateSelector {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> connectorRef;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> ticketNumber;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> ticketType;
  @NotNull @VariableExpression(skipVariableExpression = true) CriteriaSpecWrapper approvalCriteria;
  @VariableExpression(skipVariableExpression = true) CriteriaSpecWrapper rejectionCriteria;
  ServiceNowChangeWindowSpec changeWindow;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {expression})
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Pattern(regexp = NGRegexValidatorConstants.TIMEOUT_PATTERN)
  @VariableExpression(skipInnerObjectTraversal = true)
  ParameterField<Timeout> retryInterval;

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.SERVICE_NOW_APPROVAL_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return StepSpecTypeConstants.APPROVAL_FACILITATOR;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return ServiceNowApprovalSpecParameters.builder()
        .connectorRef(connectorRef)
        .ticketNumber(ticketNumber)
        .ticketType(ticketType)
        .approvalCriteria(approvalCriteria)
        .rejectionCriteria(rejectionCriteria)
        .delegateSelectors(delegateSelectors)
        .changeWindowSpec(changeWindow)
        .retryInterval(retryInterval)
        .build();
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
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
