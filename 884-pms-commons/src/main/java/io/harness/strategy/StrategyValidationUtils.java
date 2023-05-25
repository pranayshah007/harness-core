/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.strategy;

import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.strategy.AxisConfig;
import io.harness.plancreator.strategy.ExcludeConfig;
import io.harness.plancreator.strategy.ExpressionAxisConfig;
import io.harness.plancreator.strategy.MatrixConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.yaml.ParameterField;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class StrategyValidationUtils {
  public final String STRATEGY_IDENTIFIER_POSTFIX = "<+strategy.identifierPostFix>";
  public final String STRATEGY_IDENTIFIER_POSTFIX_ESCAPED = "<\\+strategy.identifierPostFix>";

  public void validateStrategyNode(StrategyConfig config) {
    if (ParameterField.isNotNull(config.getMatrixConfig()) && config.getMatrixConfig().getValue() != null) {
      validateAxes(config);
      Set<String> validKeys = new HashSet<>();
      Map<String, AxisConfig> axisConfig = ((MatrixConfig) config.getMatrixConfig().getValue()).getAxes();
      Map<String, ExpressionAxisConfig> expressionAxisConfig =
          ((MatrixConfig) config.getMatrixConfig().getValue()).getExpressionAxes();
      validKeys.addAll(axisConfig.keySet());
      validKeys.addAll(expressionAxisConfig.keySet());

      if (!ParameterField.isBlank(((MatrixConfig) config.getMatrixConfig().getValue()).getExclude())
          && ((MatrixConfig) config.getMatrixConfig().getValue()).getExclude().getValue() != null) {
        List<ExcludeConfig> excludeConfigs =
            ((MatrixConfig) config.getMatrixConfig().getValue()).getExclude().getValue();
        for (ExcludeConfig excludeConfig : excludeConfigs) {
          if (!validKeys.containsAll(excludeConfig.getExclude().keySet())) {
            throw new InvalidYamlException(
                "Values defined in the exclude are not correct. Please make sure exclude contains all the valid keys defined as axes.");
          }
        }
      }
    } else if (config.getRepeat() != null) {
      if (!ParameterField.isBlank(config.getRepeat().getTimes()) && config.getRepeat().getTimes().getValue() != null
          && config.getRepeat().getTimes().getValue() == 0) {
        throw new InvalidYamlException(
            "Iteration can not be [zero]. Please provide some positive Integer for Iteration count");
      }
    } else if (!ParameterField.isBlank(config.getParallelism()) && config.getParallelism().getValue() != null
        && config.getParallelism().getValue() == 0) {
      throw new InvalidYamlException(
          "Parallelism can not be [zero]. Please provide some positive Integer for Parallelism");
    }
  }

  private void validateAxes(StrategyConfig config) {
    Map<String, AxisConfig> axisConfig = ((MatrixConfig) config.getMatrixConfig().getValue()).getAxes();
    Map<String, ExpressionAxisConfig> expressionAxisConfig =
        ((MatrixConfig) config.getMatrixConfig().getValue()).getExpressionAxes();
    int sizeOfAxis =
        (axisConfig != null ? axisConfig.size() : 0) + (expressionAxisConfig != null ? expressionAxisConfig.size() : 0);
    if (sizeOfAxis == 0) {
      throw new InvalidYamlException("No Axes defined in matrix. Please define at least one axis");
    }
    for (Map.Entry<String, AxisConfig> entry : axisConfig.entrySet()) {
      if (!entry.getValue().getAxisValue().isExpression() && entry.getValue().getAxisValue().getValue().isEmpty()) {
        throw new InvalidYamlException(String.format(
            "Axis is empty for key [%s]. Please provide at least one value in the axis.", entry.getKey()));
      }
    }
    for (Map.Entry<String, ExpressionAxisConfig> entry : expressionAxisConfig.entrySet()) {
      if (StringUtils.isEmpty(entry.getValue().getExpression().getExpressionValue())) {
        throw new InvalidYamlException(String.format(
            "Axis is empty for key [%s]. Please provide at least one value in the axis.", entry.getKey()));
      }
    }
  }
}
