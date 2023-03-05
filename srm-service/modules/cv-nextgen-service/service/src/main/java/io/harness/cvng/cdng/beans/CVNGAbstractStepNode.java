/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.onlyRuntimeInputAllowed;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.timeout.Timeout;

import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.Pattern;
import lombok.Data;

@OwnedBy(HarnessTeam.CV)
@Data
public abstract class CVNGAbstractStepNode extends AbstractStepNode {
  // check if this field is needed or not
  @ApiModelProperty(dataType = SwaggerConstants.FAILURE_STRATEGY_CONFIG_LIST_CLASSPATH)
  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {onlyRuntimeInputAllowed})
  ParameterField<List<FailureStrategyConfig>> failureStrategies;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Pattern(regexp = NGRegexValidatorConstants.TIMEOUT_PATTERN_WITHOUT_EXECUTION_INPUT)
  @VariableExpression(skipInnerObjectTraversal = true)
  ParameterField<Timeout> timeout;
}
