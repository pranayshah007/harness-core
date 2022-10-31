/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler;

import static io.harness.exception.WingsException.ExecutionContext.DELEGATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData.ErrorNotifyResponseDataBuilder;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DX)
public class DelegateExceptionManager {
  @Inject ExceptionManager exceptionManager;

  public DelegateResponseData getResponseData(Throwable throwable,
      ErrorNotifyResponseDataBuilder errorNotifyResponseDataBuilder, boolean isErrorFrameworkSupportedByTask) {
    if (!(throwable instanceof Exception) || !isErrorFrameworkSupportedByTask) {
      // return default response
      return prepareErrorResponse(throwable, errorNotifyResponseDataBuilder).build();
    }

    Exception exception = (Exception) throwable;
    WingsException processedException = exceptionManager.processException(exception, DELEGATE, log);
    return prepareErrorResponse(processedException, errorNotifyResponseDataBuilder)
        .exception(processedException)
        .build();
  }

  // ---------- PRIVATE METHODS -------------

  private ErrorNotifyResponseDataBuilder prepareErrorResponse(
      Throwable throwable, ErrorNotifyResponseDataBuilder errorNotifyResponseDataBuilder) {
    if (errorNotifyResponseDataBuilder == null) {
      errorNotifyResponseDataBuilder = ErrorNotifyResponseData.builder();
    }

    return errorNotifyResponseDataBuilder.failureTypes(ExceptionUtils.getFailureTypes(throwable))
        .errorMessage(ExceptionUtils.getMessage(throwable));
  }
}
