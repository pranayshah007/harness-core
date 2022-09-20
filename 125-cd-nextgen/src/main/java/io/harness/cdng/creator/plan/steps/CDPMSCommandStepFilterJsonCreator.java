/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cdng.ssh.CommandStepNode;
import io.harness.exception.InvalidYamlException;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;

import com.google.common.collect.Sets;
import java.util.Set;

public class CDPMSCommandStepFilterJsonCreator extends CDPMSStepFilterJsonCreatorV2 {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.COMMAND);
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    FilterCreationResponse response = super.handleNode(filterCreationContext, yamlField);
    validateStrategy(yamlField.getStrategy());
    validateCommandUnitsExist(yamlField);
    return response;
  }

  private void validateStrategy(StrategyConfig strategy) {
    if (strategy != null
        && (strategy.getMatrixConfig() != null
            || (strategy.getParallelism() != null
                && (strategy.getParallelism().getValue() != null
                    || strategy.getParallelism().getExpressionValue() != null)))) {
      throw new InvalidYamlException("Command step supports only repeat strategy.");
    }
  }

  private void validateCommandUnitsExist(AbstractStepNode stepNode) {
    if (CommandStepNode.class.isAssignableFrom(stepNode.getClass())
        && (((CommandStepNode) stepNode).getCommandStepInfo() == null
            || isEmpty(((CommandStepNode) stepNode).getCommandStepInfo().getCommandUnits()))) {
      throw new InvalidYamlException("Command step needs at least one command unit defined.");
    }
  }
}
