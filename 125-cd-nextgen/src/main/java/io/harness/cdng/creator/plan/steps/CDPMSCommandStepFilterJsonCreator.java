package io.harness.cdng.creator.plan.steps;

import io.harness.exception.InvalidYamlException;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;

import com.google.common.collect.Sets;
import java.util.Set;

public class CDPMSCommandStepFilterJsonCreator extends GenericStepPMSFilterJsonCreatorV2 {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.COMMAND);
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    super.handleNode(filterCreationContext, yamlField);
    validateStrategy(yamlField.getStrategy());
    return FilterCreationResponse.builder().build();
  }

  private void validateStrategy(StrategyConfig strategy) {
    if (strategy != null
        && (strategy.getMatrixConfig() != null
            || (strategy.getParallelism() != null
                && (strategy.getParallelism().getValue() != null
                    || strategy.getParallelism().getExpressionValue() != null)))) {
      throw new InvalidYamlException("Command step requires repeat strategy exclusively");
    }
  }
}
