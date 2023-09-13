/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Data
@Builder
@Schema(name = "Condition", description = "This contains details of the Condition entity in Harness")
public class ConditionDTO {
  @NotNull String key;
  @NotNull String value;
  @NotNull Operator operator;

  public static ConditionDTO fromCondition(Condition condition) {
    if (condition == null) {
      return null;
    }
    if (ParameterField.isNull(condition.getValue())) {
      throw new InvalidRequestException("Value can't be null");
    }
    String valueString = (String) condition.getValue().fetchFinalValue();
    if (isBlank(condition.getKey())) {
      throw new InvalidRequestException("Key can't be empty");
    }
    if (condition.getValue().isExpression()) {
      // unresolved expression mode set in Jira/SNow/Custom approvals
      throw new InvalidRequestException(
          String.format("Key [%s] has invalid value: [%s]", condition.getKey(), valueString));
    }
    return ConditionDTO.builder().key(condition.getKey()).value(valueString).operator(condition.getOperator()).build();
  }

  @Override
  public String toString() {
    return "ConditionDTO{"
        + "key='" + key + '\'' + ", value='" + value + '\'' + ", operator=" + operator + '}';
  }
}
