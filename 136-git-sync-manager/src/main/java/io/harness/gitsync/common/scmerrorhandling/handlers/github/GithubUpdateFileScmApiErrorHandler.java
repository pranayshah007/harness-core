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
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.util.ErrorMessageFormatter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class GithubUpdateFileScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String UPDATE_FILE_FAILED = "The requested file<FILEPATH> couldn't be updated. ";
  public static final String UPDATE_FILE_NOT_FOUND_ERROR_HINT = "Please check the following:\n"
      + "1. If requested Github repository<REPO> exists or not.\n"
      + "2. If requested branch<BRANCH> exists or not."
      + "3. If requested branch<BRANCH> has permissions to push.";
  public static final String UPDATE_FILE_NOT_FOUND_ERROR_EXPLANATION =
      "There was issue while updating file in git. Possible reasons can be:\n"
      + "1. The requested Github repository<REPO> doesn't exist\n"
      + "2. The requested branch<BRANCH> doesn't exist in given Github repository."
      + "3. The requested branch<BRANCH> does not have permissions to push.";
  public static final String UPDATE_FILE_CONFLICT_ERROR_HINT =
      "Please check the input blob id of the requested file. It should match with current blob id of the file<FILEPATH> at head of the branch<BRANCH> in Github repository<REPO>";
  public static final String UPDATE_FILE_CONFLICT_ERROR_EXPLANATION =
      "The input blob id of the requested file doesn't match with current blob id of the file<FILEPATH> at head of the branch<BRANCH> in Github repository<REPO>, which results in update operation failure.";
  public static final String UPDATE_FILE_UNPROCESSABLE_ENTITY_ERROR_HINT =
      "Please check if requested filepath<FILEPATH> is a valid one.";
  public static final String UPDATE_FILE_UNPROCESSABLE_ENTITY_ERROR_EXPLANATION =
      "Requested filepath<FILEPATH> doesn't match with expected valid format.";
  public static final String UPDATE_FAILURE_HINT =
      ScmErrorHints.INVALID_CREDENTIALS + "\n- " + ScmErrorHints.OAUTH_ACCESS_FAILURE;
  public static final String UPDATE_FAILURE_EXPLANATION = UPDATE_FILE_FAILED + "\n- "
      + ScmErrorExplanations.INVALID_CONNECTOR_CREDS + "\n- " + ScmErrorExplanations.OAUTH_ACCESS_DENIED;

  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 401:
      case 403:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(UPDATE_FAILURE_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(UPDATE_FAILURE_EXPLANATION, errorMetadata),
            new ScmUnauthorizedException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(UPDATE_FILE_NOT_FOUND_ERROR_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(UPDATE_FILE_NOT_FOUND_ERROR_EXPLANATION, errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 409:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(UPDATE_FILE_CONFLICT_ERROR_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(UPDATE_FILE_CONFLICT_ERROR_EXPLANATION, errorMetadata),
            new ScmConflictException(errorMessage));
      case 422:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(UPDATE_FILE_UNPROCESSABLE_ENTITY_ERROR_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(UPDATE_FILE_UNPROCESSABLE_ENTITY_ERROR_EXPLANATION, errorMetadata),
            new ScmBadRequestException(errorMessage));
      default:
        log.error(String.format("Error while updating github file: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
