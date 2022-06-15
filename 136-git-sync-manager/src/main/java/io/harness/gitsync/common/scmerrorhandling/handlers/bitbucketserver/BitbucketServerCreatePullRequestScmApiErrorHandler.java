/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver;

import static io.harness.annotations.dev.HarnessTeam.PL;

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
@OwnedBy(PL)
public class BitbucketServerCreatePullRequestScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String CREATE_PULL_REQUEST_FAILURE = "The pull request could not be created in Bitbucket. ";

  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 400:
        // bitbucket already throws well formatted error messages, so no need of any hints/explanations here
        throw new ScmBadRequestException(errorMessage);
      case 401:
      case 403:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.INVALID_CREDENTIALS, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                CREATE_PULL_REQUEST_FAILURE + ScmErrorExplanations.INVALID_CONNECTOR_CREDS, errorMetadata),
            new ScmUnauthorizedException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.REPO_OR_BRANCH_NOT_FOUND, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                CREATE_PULL_REQUEST_FAILURE + ScmErrorExplanations.REPO_OR_BRANCH_NOT_FOUND, errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 409:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.PR_ALREADY_EXISTS, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                CREATE_PULL_REQUEST_FAILURE + ScmErrorExplanations.PR_ALREADY_EXISTS, errorMetadata),
            new ScmBadRequestException(errorMessage));
      default:
        log.error(
            String.format("Error while creating bitbucket(server) pull request: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
