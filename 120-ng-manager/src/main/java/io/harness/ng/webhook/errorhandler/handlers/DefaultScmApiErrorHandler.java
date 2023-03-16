/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.webhook.errorhandler.handlers;

import static io.harness.annotations.dev.HarnessTeam.SPG;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExplanationException;
import io.harness.exception.ScmException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.ng.webhook.errorhandler.dtos.ErrorMetadata;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(SPG)
public class DefaultScmApiErrorHandler implements ScmApiErrorHandler {
  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    if (statusCode >= 300) {
      ErrorCode errorCode = convertScmStatusCodeToErrorCode(statusCode);
      if (errorCode == ErrorCode.UNEXPECTED) {
        log.error("Encountered new status code: [{}] with message: [{}] from scm", statusCode, errorMessage);
        throw new UnexpectedException("Unexpected error occurred while doing scm operation");
      }
      if (isNotEmpty(errorMessage)) {
        throw new ExplanationException(errorMessage, new ScmException(errorCode));
      } else {
        throw new ExplanationException("Error while performing scm operation", new ScmException(errorCode));
      }
    }
  }

  private ErrorCode convertScmStatusCodeToErrorCode(int statusCode) {
    switch (statusCode) {
      case 304:
        return ErrorCode.SCM_NOT_MODIFIED;
      case 400:
      case 404:
      case 422:
        return ErrorCode.SCM_BAD_REQUEST;
      case 401:
      case 403:
        return ErrorCode.SCM_UNAUTHORIZED_ERROR_V2;
      case 409:
        return ErrorCode.SCM_CONFLICT_ERROR_V2;
      case 500:
        return ErrorCode.SCM_INTERNAL_SERVER_ERROR_V2;
      default:
        return ErrorCode.UNEXPECTED;
    }
  }
}
