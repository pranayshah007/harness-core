/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.google.inject.Inject;
import java.util.Arrays;

@OwnedBy(HarnessTeam.CDC)
public class ServiceVariableOverridesFunctor implements SdkFunctor {
  private static final int NUMBER_OF_EXPECTED_ARGS = 1;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    if (args.length != NUMBER_OF_EXPECTED_ARGS) {
      throw new InvalidArgumentsException(
          format("Invalid service variable overrides functor arguments: %s", Arrays.asList(args)));
    }

    final OptionalSweepingOutput optionalSweepingOutput = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.SERVICE_VARIABLES));

    if (optionalSweepingOutput.isFound()) {
      VariablesSweepingOutput variablesSweepingOutput = (VariablesSweepingOutput) optionalSweepingOutput.getOutput();

      Object value = variablesSweepingOutput.get(args[0]);
      if (value instanceof ParameterField) {
        ParameterField<?> parameterFieldValue = (ParameterField<?>) value;
        if (parameterFieldValue.fetchFinalValue() == null) {
          throw new InvalidRequestException(String.format("Service variable [%s] value found to be null", args[0]));
        }

        if (EngineExpressionEvaluator.hasExpressions(parameterFieldValue.fetchFinalValue().toString())) {
          return cdExpressionResolver.renderExpression(ambiance, parameterFieldValue.fetchFinalValue().toString());
        }

        return parameterFieldValue.getValue();
      }
    }

    return null;
  }
}
