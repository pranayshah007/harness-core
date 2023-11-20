/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.ScmErrorHints.FILE_NOT_FOUND;
import static io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.ScmErrorHints.INVALID_CREDENTIALS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.SCMExceptionErrorMessages;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmRequestTimeoutException;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.exception.ScmUnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.util.ErrorMessageFormatter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class BitbucketServerGetFileScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String GET_FILE_REQUEST_FAILURE =
      "The requested file<FILEPATH> could not be fetched from Bitbucket. ";
  private static final String BITBUCKET_REQUEST_TIMEOUT_ERROR_MESSAGE = "Failed to fetch file: ";

  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 401:
      case 403:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(INVALID_CREDENTIALS, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                GET_FILE_REQUEST_FAILURE + ScmErrorExplanations.INVALID_CONNECTOR_CREDS, errorMetadata),
            new ScmUnauthorizedException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(FILE_NOT_FOUND, errorMetadata),
            ErrorMessageFormatter.formatMessage(ScmErrorExplanations.FILE_NOT_FOUND, errorMetadata),
            new ScmBadRequestException(SCMExceptionErrorMessages.FILE_NOT_FOUND_ERROR));
      case 429:
        throw NestedExceptionUtils.hintWithExplanationException(ScmErrorHints.RATE_LIMIT,
            ScmErrorExplanations.RATE_LIMIT,
            new ScmBadRequestException(
                EmptyPredicate.isEmpty(errorMessage) ? ScmErrorDefaultMessage.RATE_LIMIT : errorMessage));
      case 504:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.HINT_REQUEST_TIMED_OUT, errorMetadata),
            ErrorMessageFormatter.formatMessage(ScmErrorExplanations.EXPLANATION_REQUEST_TIMED_OUT, errorMetadata),
            new ScmRequestTimeoutException(ErrorMessageFormatter.formatMessage(
                BITBUCKET_REQUEST_TIMEOUT_ERROR_MESSAGE + errorMessage, errorMetadata)));
      default:
        log.error(String.format("Error while getting bitbucket(server) file: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
