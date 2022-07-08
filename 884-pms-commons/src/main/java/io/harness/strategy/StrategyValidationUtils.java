package io.harness.strategy;

import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.strategy.AxisConfig;
import io.harness.plancreator.strategy.ExcludeConfig;
import io.harness.plancreator.strategy.MatrixConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StrategyValidationUtils {
  public void validateStrategyNode(StrategyConfig config) {
    if (config.getMatrixConfig() != null) {
      Map<String, AxisConfig> axisConfig = ((MatrixConfig) config.getMatrixConfig()).getAxes();
      if (axisConfig == null || axisConfig.size() == 0) {
        throw new InvalidYamlException("No Axes defined in matrix. Please define at least one axis");
      }
      for (Map.Entry<String, AxisConfig> entry : axisConfig.entrySet()) {
        if (!entry.getValue().getAxisValue().isExpression() && entry.getValue().getAxisValue().getValue().isEmpty()) {
          throw new InvalidYamlException(String.format(
              "Axis is empty for key [%s]. Please provide at least one value in the axis.", entry.getKey()));
        }
      }
      if (!ParameterField.isBlank(((MatrixConfig) config.getMatrixConfig()).getExclude())
          && ((MatrixConfig) config.getMatrixConfig()).getExclude().getValue() != null) {
        List<ExcludeConfig> excludeConfigs = ((MatrixConfig) config.getMatrixConfig()).getExclude().getValue();
        for (ExcludeConfig excludeConfig : excludeConfigs) {
          if (!excludeConfig.getExclude().keySet().equals(axisConfig.keySet())) {
            throw new InvalidYamlException(
                "Values defined in the exclude are not correct. Please make sure exclude contains all the axis values and no extra value.");
          }
        }
      }
    } else if (config.getForConfig() != null) {
      if (!ParameterField.isBlank(config.getForConfig().getIteration())
          && config.getForConfig().getIteration().getValue() != null
          && config.getForConfig().getIteration().getValue() == 0) {
        throw new InvalidYamlException(
            "Iteration can not be [zero]. Please provide some positive Integer for Iteration count");
      }
    } else if (!ParameterField.isBlank(config.getParallelism()) && config.getParallelism().getValue() != null
        && config.getParallelism().getValue() == 0) {
      throw new InvalidYamlException(
          "Parallelism can not be [zero]. Please provide some positive Integer for Parallelism");
    }
  }
}
