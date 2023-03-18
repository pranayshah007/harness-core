/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.bamboo;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.core.VariableExpression.IteratePolicy.REGULAR_WITH_CUSTOM_FIELD;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.VariableExpression;

import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BambooParameterField implements Visitable {
  @NotEmpty @VariableExpression(policy = REGULAR_WITH_CUSTOM_FIELD) String name;

  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @VariableExpression(skipVariableExpression = true)
  ParameterField<String> value;

  @ApiModelProperty(dataType = SwaggerConstants.BAMBOO_PARAMETER_FIELD_TYPE_ENUM_CLASSPATH)
  @VariableExpression(skipVariableExpression = true)
  ParameterField<BambooParameterFieldType> type;
}
