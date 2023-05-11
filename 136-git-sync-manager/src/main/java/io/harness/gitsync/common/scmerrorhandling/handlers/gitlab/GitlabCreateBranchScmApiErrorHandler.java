/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.gitlab;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.gitsync.common.scmerrorhandling.handlers.gitlab.ScmErrorExplanations.INVALID_CONNECTOR_CREDS;
import static io.harness.gitsync.common.scmerrorhandling.handlers.gitlab.ScmErrorHints.INVALID_CREDENTIALS;

import io.harness.annotations.dev.OwnedBy;
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
@OwnedBy(PIPELINE)
public class GitlabCreateBranchScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String CREATE_BRANCH_FAILED_MESSAGE =
      "The requested branch<NEW_BRANCH> could not be created on Gitlab. ";
  public static final String CREATE_BRANCH_CONFLICT_ERROR_HINT =
      "Please check if the requested Gitlab base branch<BRANCH> exists.";
  public static final String CREATE_BRANCH_CONFLICT_ERROR_EXPLANATION = "The given base branch<BRANCH> does not exist.";

  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 400:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(CREATE_BRANCH_CONFLICT_ERROR_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                CREATE_BRANCH_FAILED_MESSAGE + CREATE_BRANCH_CONFLICT_ERROR_EXPLANATION, errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 401:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(INVALID_CREDENTIALS, errorMetadata),
            ErrorMessageFormatter.formatMessage(CREATE_BRANCH_FAILED_MESSAGE + INVALID_CONNECTOR_CREDS, errorMetadata),
            new ScmUnauthorizedException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.REPO_NOT_FOUND, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                CREATE_BRANCH_FAILED_MESSAGE + ScmErrorExplanations.REPO_NOT_FOUND, errorMetadata),
            new ScmBadRequestException(errorMessage));
      default:
        log.error(String.format("Error while creating gitlab branch: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
