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
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.SCMExceptionErrorMessages;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.exception.ScmUnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class BitbucketServerGetFileScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String GET_FILE_REQUEST_FAILURE = "The requested file could not be fetched from Bitbucket. ";

  @Override
  public void handleError(int statusCode, String errorMessage) throws WingsException {
    switch (statusCode) {
      case 401:
      case 403:
        throw NestedExceptionUtils.hintWithExplanationException(INVALID_CREDENTIALS,
            GET_FILE_REQUEST_FAILURE + ScmErrorExplanations.INVALID_CONNECTOR_CREDS,
            new ScmUnauthorizedException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(FILE_NOT_FOUND, ScmErrorExplanations.FILE_NOT_FOUND,
            new ScmBadRequestException(SCMExceptionErrorMessages.FILE_NOT_FOUND_ERROR));
      default:
        log.error(String.format("Error while getting bitbucket(server) file: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
