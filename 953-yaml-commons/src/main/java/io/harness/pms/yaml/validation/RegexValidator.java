/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml.validation;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.expression.EngineExpressionResolver;

import java.util.regex.Pattern;

/**
 * This validator handles currentValue to match the given regex.
 * Examples -
 * ${input}.regex(^prod*) #render and use matcher
 * ${input}.regex(^${env.name}_[a-z]+) #render and use matcher
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class RegexValidator implements RuntimeValidator {
  private final EngineExpressionResolver engineExpressionResolver;
  private final ExpressionMode expressionMode;

  public RegexValidator(EngineExpressionResolver engineExpressionResolver, ExpressionMode expressionMode) {
    this.engineExpressionResolver = engineExpressionResolver;
    this.expressionMode = expressionMode;
  }

  @Override
  public RuntimeValidatorResponse isValidValue(Object currentValue, String parameters) {
    if (currentValue == null) {
      return RuntimeValidatorResponse.builder().errorMessage("Current value is null").build();
    }

    String regex = engineExpressionResolver.renderExpression(parameters, expressionMode);
    if (currentValue instanceof String) {
      if (!ExpressionUtils.matchesPattern(Pattern.compile(regex), (String) currentValue)) {
        return RuntimeValidatorResponse.builder()
            .errorMessage("Current value " + currentValue + " does not match with given regex")
            .build();
      }
      return RuntimeValidatorResponse.builder().isValid(true).build();
    } else {
      return RuntimeValidatorResponse.builder()
          .errorMessage("Regex do not handle value of type: " + currentValue.getClass())
          .build();
    }
  }
}
