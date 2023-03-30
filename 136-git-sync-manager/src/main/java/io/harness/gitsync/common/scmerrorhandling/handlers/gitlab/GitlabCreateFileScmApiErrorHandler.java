/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.gitlab;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
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
public class GitlabCreateFileScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String CREATE_FILE_WITH_INVALID_CREDS =
      "The requested file<FILEPATH> couldn't be created in Gitlab. " + ScmErrorExplanations.INVALID_CONNECTOR_CREDS;
  public static final String CREATE_FILE_NOT_FOUND_ERROR_HINT = "Please check the following:\n"
      + "1. If requested Gitlab repository<REPO> exists or not.\n"
      + "2. If requested branch<BRANCH> exists or not.\n"
      + "3. If requested branch<BRANCH> has permissions to push.";
  public static final String CREATE_FILE_NOT_FOUND_ERROR_EXPLANATION =
      "There was issue while creating file<FILEPATH> in Gitlab. Possible reasons can be:\n"
      + "1. The requested Gitlab repository<REPO> doesn't exist\n"
      + "2. The requested branch<BRANCH> doesn't exist in given Gitlab repository<REPO>.\n"
      + "3. The requested branch<BRANCH> does not have permissions to push.";
  public static final String CREATE_FILE_CONFLICT_ERROR_HINT =
      "Please check if there's already a file<FILEPATH> in Gitlab repository<REPO> for the given filepath and branch<BRANCH>.";
  public static final String CREATE_FILE_CONFLICT_ERROR_EXPLANATION =
      "File with given filepath<FILEPATH> already exists in Gitlab, thus couldn't create a new file";

  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 400:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(CREATE_FILE_CONFLICT_ERROR_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(CREATE_FILE_CONFLICT_ERROR_EXPLANATION, errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 401:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(INVALID_CREDENTIALS, errorMetadata),
            ErrorMessageFormatter.formatMessage(CREATE_FILE_WITH_INVALID_CREDS, errorMetadata),
            new ScmUnauthorizedException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(CREATE_FILE_NOT_FOUND_ERROR_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(CREATE_FILE_NOT_FOUND_ERROR_EXPLANATION, errorMetadata),
            new ScmBadRequestException(errorMessage));
      default:
        log.error(String.format("Error while creating gitlab file: <%s: %s>", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
