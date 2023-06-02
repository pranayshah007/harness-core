/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.onlyRuntimeInputAllowed;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public abstract class DeploymentAbstractStageNode extends AbstractStageNode {
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.FAILURE_STRATEGY_CONFIG_LIST_CLASSPATH)
  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {onlyRuntimeInputAllowed})
  ParameterField<List<FailureStrategyConfig>> failureStrategies;

  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @YamlSchemaTypes({string})
  @JsonProperty("skipInstances")
  ParameterField<Boolean> skipInstances;
}
