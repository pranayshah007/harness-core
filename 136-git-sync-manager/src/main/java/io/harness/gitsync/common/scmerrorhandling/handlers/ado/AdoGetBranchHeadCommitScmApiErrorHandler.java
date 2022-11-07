/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.ado;

import static io.harness.gitsync.common.scmerrorhandling.handlers.ado.ScmErrorHints.WRONG_REPO_OR_BRANCH;

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
public class AdoGetBranchHeadCommitScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String GET_BRANCH_HEAD_COMMIT_FAILED_MESSAGE =
      "Failed to fetch branch head commit details from Azure.";

  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 203:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.INVALID_CREDENTIALS, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                GET_BRANCH_HEAD_COMMIT_FAILED_MESSAGE + ScmErrorExplanations.INVALID_CONNECTOR_CREDS, errorMetadata),
            new ScmUnauthorizedException(errorMessage));
      case 400:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(WRONG_REPO_OR_BRANCH, errorMetadata),
            ErrorMessageFormatter.formatMessage(ScmErrorExplanations.WRONG_REPO_OR_BRANCH, errorMetadata),
            new ScmBadRequestException(SCMExceptionErrorMessages.AZURE_REPOSITORY_OR_BRANCH_NOT_FOUND_ERROR));
      case 401:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.MISSING_PERMISSION_CREDS_HINTS, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                ScmErrorExplanations.MISSING_PERMISSION_CREDS_EXPLANATION, errorMetadata),
            new ScmBadRequestException(errorMessage));
      default:
        log.error(String.format(
            "Error while fetching the branch head commit from Azure: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
