/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.ado;

import static io.harness.gitsync.common.scmerrorhandling.handlers.ado.ScmErrorHints.REPO_NOT_FOUND;

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
public class AdoUpdateFileScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String UPDATE_FILE_REQUEST_FAILURE =
      "The requested file<FILEPATH> couldn't be updated in Azure. ";
  public static final String UPDATE_FILE_FORBIDDEN_REQUEST_EXPLANATION =
      "The requested branch<BRANCH> does not have push permission.";
  public static final String UPDATE_FILE_FORBIDDEN_REQUEST_HINT =
      "Please use a pull request to update file<FILEPATH> in this branch<BRANCH>.";
  public static final String UPDATE_FILE_BAD_REQUEST_ERROR_EXPLANATION =
      "There was issue while updating file in Azure. The requested branch<BRANCH> doesn't exist in given Azure repository.";
  public static final String UPDATE_FILE_BAD_REQUEST_ERROR_HINT =
      "Please check if requested branch<BRANCH> exists or not.";
  public static final String UPDATE_FILE_CONFLICT_ERROR_HINT =
      "Please resolve the conflicts with latest remote file<FILEPATH>";
  public static final String UPDATE_FILE_CONFLICT_ERROR_EXPLANATION =
      "The requested file<FILEPATH> has conflicts with remote file.";

  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 203:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.INVALID_CREDENTIALS, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                UPDATE_FILE_REQUEST_FAILURE + ScmErrorExplanations.INVALID_CONNECTOR_CREDS, errorMetadata),
            new ScmUnauthorizedException(errorMessage));
      case 400:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(UPDATE_FILE_BAD_REQUEST_ERROR_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(UPDATE_FILE_BAD_REQUEST_ERROR_EXPLANATION, errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 401:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.MISSING_PERMISSION_CREDS_HINTS, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                ScmErrorExplanations.MISSING_PERMISSION_CREDS_EXPLANATION, errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 403:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(UPDATE_FILE_FORBIDDEN_REQUEST_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                UPDATE_FILE_REQUEST_FAILURE + UPDATE_FILE_FORBIDDEN_REQUEST_EXPLANATION, errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(REPO_NOT_FOUND, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                UPDATE_FILE_REQUEST_FAILURE + ScmErrorExplanations.REPO_NOT_FOUND, errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 409:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(UPDATE_FILE_CONFLICT_ERROR_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(UPDATE_FILE_CONFLICT_ERROR_EXPLANATION, errorMetadata),
            new ScmConflictException(errorMessage));
      default:
        log.error(String.format("Error while updating Azure file: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
