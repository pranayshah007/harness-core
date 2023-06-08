/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.exception;

import io.harness.exception.GeneralException;
import io.harness.exception.HarnessServiceErrorMetadata;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.remote.client.HarnessServiceCallException;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

public class HarnessServiceExceptionHandler implements ExceptionHandler {
  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(HarnessServiceCallException.class).build();
  }
  @Override
  public WingsException handleException(Exception exception) {
    HarnessServiceCallException ex = (HarnessServiceCallException) exception;
    ErrorDTO errorDTO = ex.getErrorDTO();
    WingsException ex1 = new GeneralException("");
    ex1.setMetadata(HarnessServiceErrorMetadata.builder().responseMessages(errorDTO.getResponseMessages()).build());
    return ex1;
  }
}
