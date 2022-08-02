/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("JexlCriteriaSpec")
@Schema(name = "JexlCriteriaSpec", description = "This contains details of the Jexl Criteria")
public class JexlCriteriaSpecDTO implements CriteriaSpecDTO {
  @NotEmpty String expression;

  @Override
  public boolean isEmpty() {
    return expression == null || expression.trim().equals("");
  }

  public static JexlCriteriaSpecDTO fromJexlCriteriaSpec(JexlCriteriaSpec jexlCriteriaSpec, boolean skipEmpty) {
    if (ParameterField.isNull(jexlCriteriaSpec.getExpression())) {
      if (skipEmpty) {
        return JexlCriteriaSpecDTO.builder().expression("").build();
      }
      throw new InvalidRequestException("Expression can't be null");
    }
    String expressionString = (String) jexlCriteriaSpec.getExpression().fetchFinalValue();
    return JexlCriteriaSpecDTO.builder().expression(expressionString).build();
  }
}
