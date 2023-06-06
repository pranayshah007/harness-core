/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.exceptionmanager.exceptionhandler;

import io.harness.exception.EngineExpressionEvaluationException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.MissingRequiredFieldException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.JexlRuntimeException;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MissingRequiredFieldExceptionHandler implements ExceptionHandler {
  static String explanationMessage = "Following mandatory fields are missing %s.";

  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(MissingRequiredFieldException.class).build();
  }

  @Override
  public WingsException handleException(Exception exception) {
    if (exception instanceof MissingRequiredFieldException) {
      MissingRequiredFieldException missingRequiredFieldException = (MissingRequiredFieldException) exception;
      List<String> missingFields = missingRequiredFieldException.getMissingFields();
      return NestedExceptionUtils.hintWithExplanationException(
          getHintMessage(missingFields), getExplanationMessage(missingFields), new GeneralException("Missing fields"));
    }
    return new InvalidRequestException(ExceptionUtils.getMessage(exception));
  }

  public static String getExplanationMessage(List<String> missingFields) {
    return String.format(explanationMessage, String.join(",", missingFields));
  }

  public static String getHintMessage(List<String> missingFields) {
    return String.format("Please add the missing fields: [%s]", String.join(",", missingFields));
  }
}
