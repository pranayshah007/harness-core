/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.exceptionmanager.exceptionhandler;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.EngineExpressionEvaluationException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.JexlRuntimeException;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class JexlRuntimeExceptionHandler implements ExceptionHandler {
  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(JexlRuntimeException.class).build();
  }

  @Override
  public WingsException handleException(Exception exception) {
    log.error("Logging JexlRuntime exception details", exception);
    if (exception instanceof JexlRuntimeException) {
      JexlRuntimeException jexlRuntimeException = (JexlRuntimeException) exception;
      String expression = jexlRuntimeException.getExpression();
      String message;
      if (jexlRuntimeException.getRootCause() != null) {
        message = ExceptionUtils.getMessage(jexlRuntimeException.getRootCause());
      } else {
        message = getExplanationMessage(exception);
      }
      return new EngineExpressionEvaluationException(message, expression);
    }
    return new InvalidRequestException(ExceptionUtils.getMessage(exception));
  }

  public static String getExplanationMessage(Exception ex) {
    String message = ExceptionUtils.getMessage(ex);

    if (message.contains("parsing")) {
      return "Expression could not be resolved due to incorrect expression format.";
    } else {
      if (message.contains("InvalidRequestException")) {
        return "Failed to evaluate the expression due to " + ex.getCause().getMessage();
      }

      return "Failed to evaluate the expression.";
    }
  }

  public static String getHintMessage(Exception ex, String expression) {
    String message = ExceptionUtils.getMessage(ex);

    if (message.contains("parsing")) {
      return String.format(
          "Please re-check the expression %s are written in correct format of <+...> as well as for embedded expressions.",
          expression);
    } else {
      return String.format(
          "Expression %s might contain some unresolved expressions which could not be evaluated.", expression);
    }
  }
}
