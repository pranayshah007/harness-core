/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExplanationException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.SCMExceptionExplanations;
import io.harness.exception.SCMExceptionHints;
import io.harness.exception.ScmException;
import io.harness.exception.UnexpectedException;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(DX)
public class ScmResponseStatusUtils {
  // If different provider have different status code take provider type as input too.
  public void checkScmResponseStatusAndThrowException(int statusCode, String errorMsg) {
    if (statusCode >= 300) {
      ErrorCode errorCode = convertScmStatusCodeToErrorCode(statusCode);
      if (errorCode == ErrorCode.UNEXPECTED) {
        log.error("Encountered new status code: [{}] with message: [{}] from scm", statusCode, errorMsg);
        throw new UnexpectedException("Unexpected error occurred while doing scm operation");
      } else if (errorCode == ErrorCode.SCM_NOT_FOUND_ERROR) {
        throw NestedExceptionUtils.hintWithExplanationException(SCMExceptionHints.INVALID_CREDENTIALS,
            SCMExceptionExplanations.UNABLE_TO_PUSH_TO_REPO_WITH_USER_CREDENTIALS, new ScmException(errorCode));
      }
      if (isNotEmpty(errorMsg)) {
        throw new ExplanationException(errorMsg, new ScmException(errorCode));
      } else {
        throw new ScmException(errorCode);
      }
    }
  }

  public void checkScmResponseStatusAndThrowExceptionForUpsertWebhook(int statusCode, String errorMsg) {
    if (statusCode >= 300) {
      ErrorCode errorCode = convertScmStatusCodeToErrorCodeForUpsertWebhook(statusCode);
      if (errorCode == ErrorCode.UNEXPECTED) {
        log.error("Encountered new status code: [{}] with message: [{}] from scm", statusCode, errorMsg);
        throw new UnexpectedException("Unexpected error occurred while doing scm operation");
      } else if (errorCode == ErrorCode.SCM_NOT_FOUND_ERROR) {
        throw NestedExceptionUtils.hintWithExplanationException(SCMExceptionHints.INVALID_CREDENTIALS,
            SCMExceptionExplanations.UNABLE_TO_PUSH_TO_REPO_WITH_USER_CREDENTIALS, new ScmException(errorCode));
      }
      if (isNotEmpty(errorMsg)) {
        throw new ExplanationException(errorMsg, new ScmException(errorCode));
      } else {
        throw new ScmException(errorCode);
      }
    }
  }

  public ErrorCode convertScmStatusCodeToErrorCode(int statusCode) {
    switch (statusCode) {
      case 304:
        return ErrorCode.SCM_NOT_MODIFIED;
      case 404:
        return ErrorCode.SCM_NOT_FOUND_ERROR;
      case 409:
        return ErrorCode.SCM_CONFLICT_ERROR;
      case 422:
        return ErrorCode.SCM_UNPROCESSABLE_ENTITY;
      case 401:
      case 403:
        return ErrorCode.SCM_UNAUTHORIZED;
      case 500:
        return ErrorCode.SCM_INTERNAL_SERVER_ERROR;
      default:
        return ErrorCode.UNEXPECTED;
    }
  }

  public ErrorCode convertScmStatusCodeToErrorCodeForUpsertWebhook(int statusCode) {
    switch (statusCode) {
      case 304:
        return ErrorCode.SCM_NOT_MODIFIED;
      case 404:
        return ErrorCode.SCM_NOT_FOUND_ERROR;
      case 409:
        return ErrorCode.SCM_CONFLICT_ERROR;
      case 422:
        return ErrorCode.SCM_UNPROCESSABLE_ENTITY;
      case 401:
        return ErrorCode.SCM_UNAUTHORIZED;
      case 403:
        return ErrorCode.SCM_FORBIDDEN;
      case 400:
        return ErrorCode.SCM_BAD_REQUEST;
      case 500:
        return ErrorCode.SCM_INTERNAL_SERVER_ERROR;
      default:
        return ErrorCode.UNEXPECTED;
    }
  }
  public String formatErrorMessage(int statusCode) {
    switch (statusCode) {
      case 401:
        return "Bad credentials";
      case 403:
        return "Request Forbidden";
      case 500:
        return "Internal server error, Try again later";
      default:
        return "Couldn't connect to SCM provider, Try again later";
    }
  }

  public boolean isSuccessResponse(int statusCode) {
    return statusCode == 200;
  }
}
