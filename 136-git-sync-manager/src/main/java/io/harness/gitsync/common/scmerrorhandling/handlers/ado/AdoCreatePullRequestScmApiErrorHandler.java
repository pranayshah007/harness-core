/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.ado;

import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.SCMExceptionErrorMessages;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.exception.ScmUnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.util.ErrorMessageFormatter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AdoCreatePullRequestScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String CREATE_PULL_REQUEST_FAILURE = "The pull request could not be created in Azure. ";
  public static final String CREATE_PULL_REQUEST_CONFLICT_ERROR_EXPLANATION =
      "The pull request could not be created in Azure repo<REPO> as there is already an open pull request from source branch<BRANCH> to target branch<TARGET_BRANCH>.";
  public static final String CREATE_PULL_REQUEST_CONFLICT_ERROR_HINT =
      "Please check if there is already an open pull request from source branch<BRANCH> to target branch<TARGET_BRANCH> for given Azure repo<REPO>.";
  public static final String CREATE_PULL_REQUEST_BAD_REQUEST_ERROR_EXPLANATION =
      "The pull request could not be created in Azure repo<REPO> as the source branch<BRANCH> or target branch<TARGET_BRANCH> doesn't exist for given Azure repository.";
  public static final String CREATE_PULL_REQUEST_BAD_REQUEST_ERROR_HINT =
      "Please check If source branch<BRANCH> and target branch<TARGET_BRANCH> both exists in the given Azure repository";

  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 203:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.INVALID_CREDENTIALS, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                CREATE_PULL_REQUEST_FAILURE + ScmErrorExplanations.INVALID_CONNECTOR_CREDS, errorMetadata),
            new ScmUnauthorizedException(errorMessage));
      case 400:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(CREATE_PULL_REQUEST_BAD_REQUEST_ERROR_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(CREATE_PULL_REQUEST_BAD_REQUEST_ERROR_EXPLANATION, errorMetadata),
            new ScmBadRequestException(SCMExceptionErrorMessages.CREATE_PULL_REQUEST_FAILURE));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.REPO_NOT_FOUND, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                CREATE_PULL_REQUEST_FAILURE + ScmErrorExplanations.REPO_NOT_FOUND, errorMetadata),
            new ScmBadRequestException(SCMExceptionErrorMessages.REPOSITORY_NOT_FOUND_ERROR));
      case 409:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(CREATE_PULL_REQUEST_CONFLICT_ERROR_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(CREATE_PULL_REQUEST_CONFLICT_ERROR_EXPLANATION, errorMetadata),
            new ScmBadRequestException(SCMExceptionErrorMessages.CREATE_PULL_REQUEST_FAILURE));
      default:
        log.error(String.format("Error while creating ADO pull request: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
