/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filters;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.yaml.ParameterField.isNotNull;
import static io.harness.pms.yaml.YAMLFieldNameConstants.COMMAND;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.sdk.core.pipeline.filters.ChildrenFilterJsonCreator;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class StepGroupPmsFilterJsonCreator extends ChildrenFilterJsonCreator<StepGroupElementConfig> {
  @Override
  public Map<String, YamlField> getDependencies(FilterCreationContext ctx) {
    List<YamlNode> yamlNodes =
        Optional.of(Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField("steps")).getNode().asArray())
            .orElse(Collections.emptyList());
    List<YamlField> stepYamlFields = PlanCreatorUtils.getStepYamlFields(yamlNodes);
    YamlField variablesField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.VARIABLES);
    if (variablesField != null) {
      FilterCreatorHelper.checkIfVariableNamesAreValid(variablesField);
    }
    return stepYamlFields.stream().collect(
        Collectors.toMap(stepYamlField -> stepYamlField.getNode().getUuid(), stepYamlField -> stepYamlField));
  }

  @Override
  public PipelineFilter getFilterForGivenField(FilterCreationContext filterCreationContext) {
    return null;
  }

  @Override
  public int getStageCount(FilterCreationContext filterCreationContext, Collection<YamlField> children) {
    return 0;
  }

  @Override
  public Class<StepGroupElementConfig> getFieldClass() {
    return StepGroupElementConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(STEP_GROUP, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, StepGroupElementConfig field) {
    FilterCreationResponse response = super.handleNode(filterCreationContext, field);
    validateStrategy(field);
    return response;
  }

  private void validateStrategy(StepGroupElementConfig field) {
    ParameterField<StrategyConfig> strategy = field.getStrategy();
    if (isNotNull(strategy) && strategy.getValue() != null && containsCommandStep(field)
        && ((isNotNull(strategy.getValue().getMatrixConfig())
                && strategy.getValue().getMatrixConfig().getValue() != null)
            || (strategy.getValue().getParallelism() != null
                && (strategy.getValue().getParallelism().getValue() != null
                    || strategy.getValue().getParallelism().getExpressionValue() != null)))) {
      throw new InvalidYamlException("Only repeat strategy is supported if step group contains command step.");
    }
  }

  private boolean containsCommandStep(StepGroupElementConfig field) {
    if (isEmpty(field.getSteps())) {
      return false;
    }
    return field.getSteps()
        .stream()
        .map(ExecutionWrapperConfig::getStep)
        .filter(Objects::nonNull)
        .map(i -> i.get("type"))
        .filter(Objects::nonNull)
        .anyMatch(i -> COMMAND.equalsIgnoreCase(i.asText()));
  }
}
