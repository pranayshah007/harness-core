/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.exceptionmanager.exceptionhandler;

import static io.harness.exception.WingsException.USER;

import io.harness.exception.DataProcessingException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.CCMConnectorRuntimeException;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CCMConnectorExceptionHandler implements ExceptionHandler {
  // Create list of exceptions that will be handled by this exception handler
  // and use it while registering to map binder
  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(CCMConnectorRuntimeException.class).build();
  }

  @Override
  public WingsException handleException(Exception exception) {
    CCMConnectorRuntimeException ex = (CCMConnectorRuntimeException) exception;
    log.info("Exception message: {}, code: {}", ex.getMessage(), ex.getCode());
    if (ex.getCode() == 500) {
      log.info("Returning data processing exception");
      return NestedExceptionUtils.hintWithExplanationException(
          ex.getHint(), ex.getExplanation(), new DataProcessingException(ex.getMessage(), USER));
    }
    return NestedExceptionUtils.hintWithExplanationException(
        ex.getHint(), ex.getExplanation(), new InvalidRequestException(ex.getMessage(), USER));
  }
}
