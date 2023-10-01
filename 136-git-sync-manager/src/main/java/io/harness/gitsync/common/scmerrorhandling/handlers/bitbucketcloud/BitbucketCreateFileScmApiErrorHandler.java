/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.ScmErrorExplanations.INVALID_CONNECTOR_CREDS;
import static io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.ScmErrorExplanations.OAUTH_ACCESS_DENIED;
import static io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.ScmErrorHints.INVALID_CREDENTIALS;
import static io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.ScmErrorHints.REPO_NOT_FOUND;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
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
public class BitbucketCreateFileScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String CREATE_FILE_REQUEST_FAILURE =
      "The requested file<FILEPATH> couldn't be created in Bitbucket. ";
  public static final String CREATE_FILE_BAD_REQUEST_EXPLANATION =
      "The requested branch<BRANCH> does not have push permission.";
  public static final String CREATE_FILE_BAD_REQUEST_HINT =
      "Please use a pull request to create file<FILEPATH> in this branch<BRANCH>.";

  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 401:
      case 403:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(INVALID_CREDENTIALS, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                CREATE_FILE_REQUEST_FAILURE + INVALID_CONNECTOR_CREDS + OAUTH_ACCESS_DENIED, errorMetadata),
            new ScmUnauthorizedException(errorMessage));
      case 400:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(CREATE_FILE_BAD_REQUEST_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                CREATE_FILE_REQUEST_FAILURE + CREATE_FILE_BAD_REQUEST_EXPLANATION, errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(REPO_NOT_FOUND, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                CREATE_FILE_REQUEST_FAILURE + ScmErrorExplanations.REPO_NOT_FOUND, errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 429:
        throw NestedExceptionUtils.hintWithExplanationException(ScmErrorHints.RATE_LIMIT,
            ScmErrorExplanations.RATE_LIMIT,
            new ScmBadRequestException(
                EmptyPredicate.isEmpty(errorMessage) ? ScmErrorDefaultMessage.RATE_LIMIT : errorMessage));
      default:
        log.error(String.format("Error while creating bitbucket file: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
