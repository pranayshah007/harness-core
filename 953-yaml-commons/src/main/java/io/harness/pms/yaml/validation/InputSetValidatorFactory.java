/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml.validation;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.expression.EngineExpressionResolver;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class InputSetValidatorFactory {
  @Inject private Injector injector;

  public RuntimeValidator obtainValidator(InputSetValidator inputSetValidator,
      EngineExpressionResolver engineExpressionResolver, ExpressionMode expressionMode) {
    RuntimeValidator runtimeValidator;
    switch (inputSetValidator.getValidatorType()) {
      case ALLOWED_VALUES:
        runtimeValidator = new AllowedValuesValidator(engineExpressionResolver, expressionMode);
        break;
      case REGEX:
        runtimeValidator = new RegexValidator(engineExpressionResolver, expressionMode);
        break;
      default:
        throw new InvalidRequestException(
            "No Invoker present for execution mode :" + inputSetValidator.getValidatorType());
    }
    injector.injectMembers(runtimeValidator);
    return runtimeValidator;
  }
}
