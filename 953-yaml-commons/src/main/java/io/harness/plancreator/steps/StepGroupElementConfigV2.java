/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.template.yaml.TemplateLinkConfig;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.when.beans.StepWhenCondition;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@OwnedBy(PIPELINE)
public class StepGroupElementConfigV2 extends StepGroupElementConfig {
  StepGroupInfra stepGroupInfra;

  public StepGroupElementConfigV2(String uuid,
      @NotNull @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN) String identifier,
      @NotNull @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) String name,
      ParameterField<String> skipCondition, ParameterField<StepWhenCondition> when, TemplateLinkConfig template,
      ParameterField<List<FailureStrategyConfig>> failureStrategies, @Size(min = 1) List<ExecutionWrapperConfig> steps,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, ParameterField<StrategyConfig> strategy,
      StepGroupInfra stepGroupInfra) {
    super(uuid, identifier, name, skipCondition, when, template, failureStrategies, steps, delegateSelectors, strategy);
    this.stepGroupInfra = stepGroupInfra;
  }
}
