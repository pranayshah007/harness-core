/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.ScmErrorExplanations.INVALID_CONNECTOR_CREDS;
import static io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.ScmErrorHints.INVALID_CREDENTIALS;
import static io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.ScmErrorHints.REPO_NOT_FOUND;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmConflictException;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.exception.ScmUnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class BitbucketServerUpdateFileScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String UPDATE_FILE_REQUEST_FAILURE = "The requested file couldn't be updated in Bitbucket. ";
  public static final String UPDATE_FILE_CONFLICT_ERROR_HINT =
      "Please check the input commit id of the requested file. It should match with current commit id of the file at head of the branch in the given Bitbucket repository";
  public static final String UPDATE_FILE_CONFLICT_ERROR_EXPLANATION =
      "The input commit id of the requested file doesn't match with current commit id of the file at head of the branch in Bitbucket repository, which results in update operation failure.";

  @Override
  public void handleError(int statusCode, String errorMessage) throws WingsException {
    switch (statusCode) {
      case 401:
      case 403:
        throw NestedExceptionUtils.hintWithExplanationException(INVALID_CREDENTIALS,
            UPDATE_FILE_REQUEST_FAILURE + INVALID_CONNECTOR_CREDS, new ScmUnauthorizedException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(REPO_NOT_FOUND,
            UPDATE_FILE_REQUEST_FAILURE + ScmErrorExplanations.REPO_NOT_FOUND,
            new ScmBadRequestException(errorMessage));
      case 409:
        throw NestedExceptionUtils.hintWithExplanationException(UPDATE_FILE_CONFLICT_ERROR_HINT,
            UPDATE_FILE_CONFLICT_ERROR_EXPLANATION, new ScmConflictException(errorMessage));
      default:
        log.error(String.format("Error while updating bitbucket(server) file: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
