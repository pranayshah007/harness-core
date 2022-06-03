/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.github;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmConflictException;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.exception.ScmUnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class GithubUpdateFileScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String UPDATE_FILE_FAILED = "The requested file couldn't be updated. ";
  public static final String UPDATE_FILE_NOT_FOUND_ERROR_HINT = "Please check the following:\n"
      + "1. If requested Github repository exists or not.\n"
      + "2. If requested branch exists or not.";
  public static final String UPDATE_FILE_NOT_FOUND_ERROR_EXPLANATION =
      "There was issue while updating file in git. Possible reasons can be:\n"
      + "1. The requested Github repository doesn't exist\n"
      + "2. The requested branch doesn't exist in given Github repository.";
  public static final String UPDATE_FILE_CONFLICT_ERROR_HINT =
      "Please check the input blob id of the requested file. It should match with current blob id of the file at head of the branch in Github repository";
  public static final String UPDATE_FILE_CONFLICT_ERROR_EXPLANATION =
      "The input blob id of the requested file doesn't match with current blob id of the file at head of the branch in Github repository, which results in update operation failure.";
  public static final String UPDATE_FILE_UNPROCESSABLE_ENTITY_ERROR_HINT =
      "Please check if requested filepath is a valid one.";
  public static final String UPDATE_FILE_UNPROCESSABLE_ENTITY_ERROR_EXPLANATION =
      "Requested filepath doesn't match with expected valid format.";

  @Override
  public void handleError(int statusCode, String errorMessage) throws WingsException {
    switch (statusCode) {
      case 401:
      case 403:
        throw NestedExceptionUtils.hintWithExplanationException(ScmErrorHints.INVALID_CREDENTIALS,
            UPDATE_FILE_FAILED + ScmErrorExplanations.INVALID_CONNECTOR_CREDS,
            new ScmUnauthorizedException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(UPDATE_FILE_NOT_FOUND_ERROR_HINT,
            UPDATE_FILE_NOT_FOUND_ERROR_EXPLANATION, new ScmBadRequestException(errorMessage));
      case 409:
        throw NestedExceptionUtils.hintWithExplanationException(UPDATE_FILE_CONFLICT_ERROR_HINT,
            UPDATE_FILE_CONFLICT_ERROR_EXPLANATION, new ScmConflictException(errorMessage));
      case 422:
        throw NestedExceptionUtils.hintWithExplanationException(UPDATE_FILE_UNPROCESSABLE_ENTITY_ERROR_HINT,
            UPDATE_FILE_UNPROCESSABLE_ENTITY_ERROR_EXPLANATION, new ScmBadRequestException(errorMessage));
      default:
        log.error(String.format("Error while updating github file: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
