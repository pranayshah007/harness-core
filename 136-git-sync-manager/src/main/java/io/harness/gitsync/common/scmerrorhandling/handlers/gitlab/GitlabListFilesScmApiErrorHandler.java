/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.gitlab;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

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
public class GitlabListFilesScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String LIST_FILES_NOT_FOUND_EXPLANATION =
      "There was an issue while fetching head commit details for ref<REF>. Possible reasons can be:\n"
      + "1. The requested repository<REPO> doesn't exist in Gitlab.\n"
      + "2. The requested ref<REF> doesn't exist in given Gitlab repository<REPO>.\n"
      + "3. The request file directory<FILEPATH> doesn't exist at ref<REF>.";
  public static final String LIST_FILES_NOT_FOUND_HINT = "Please check the following:\n"
      + "1. If the requested repo<REPO> exists on Gitlab or not.\n"
      + "2. If the requested ref<REF> exists in repo<REPO> on Gitlab or not.\n"
      + "3. If the requested file directory<FILEPATH> exists or not at ref<REF>.";
  public static final String LIST_FILES_FAILED_MESSAGE = "Listing files from Gitlab failed. ";

  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 401:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.INVALID_CREDENTIALS, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                LIST_FILES_FAILED_MESSAGE + ScmErrorExplanations.INVALID_CONNECTOR_CREDS, errorMetadata),
            new ScmUnauthorizedException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(LIST_FILES_NOT_FOUND_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(LIST_FILES_NOT_FOUND_EXPLANATION, errorMetadata),
            new ScmBadRequestException(errorMessage));
      default:
        log.error(String.format("Error while list files operation: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}