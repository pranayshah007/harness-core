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
  public static final String CREATE_FILE_BAD_REQUEST_ERROR_EXPLANATION =
      "There was issue while updating file<FILEPATH> in Azure. Possible reasons can be:\n"
      + "1. The requested branch<BRANCH> doesn't exist in given Azure repository<REPO>."
      + "2. The current version of file is outdated with respect to git branch head";
  public static final String CREATE_FILE_BAD_REQUEST_ERROR_HINT = "Please check the following:\n"
      + "1. If the requested branch<BRANCH> exists or not in given Azure repository<REPO>."
      + "2. Try and make changes on updated version of the file.";

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
            ErrorMessageFormatter.formatMessage(CREATE_FILE_BAD_REQUEST_ERROR_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(CREATE_FILE_BAD_REQUEST_ERROR_EXPLANATION, errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(REPO_NOT_FOUND, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                UPDATE_FILE_REQUEST_FAILURE + ScmErrorExplanations.REPO_NOT_FOUND, errorMetadata),
            new ScmBadRequestException(errorMessage));
      default:
        log.error(String.format("Error while updating Azure file: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
