/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.gitlab;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmConflictException;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.exception.ScmUnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.util.ErrorMessageFormatter;

import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Slf4j
@OwnedBy(PIPELINE)

public class GitlabUpdateFileScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String UPDATE_FILE_FAILED = "The requested file<FILEPATH> couldn't be updated. ";
  public static final String UPDATE_FILE_NOT_FOUND_ERROR_HINT = "Please check the following:\n"
      + "1. If requested Gitlab repository<REPO> exists or not.\n"
      + "2. If requested branch<BRANCH> exists or not."
      + "3. If requested branch<BRANCH> has permissions to push.";
  public static final String UPDATE_FILE_NOT_FOUND_ERROR_EXPLANATION =
      "There was issue while updating file in git. Possible reasons can be:\n"
      + "1. The requested Gitlab repository<REPO> doesn't exist\n"
      + "2. The requested branch<BRANCH> doesn't exist in given Gitlab repository."
      + "3. The requested branch<BRANCH> does not have permissions to push.";
  public static final String UPDATE_FILE_CONFLICT_ERROR_HINT =
      "Please check the input commit id of the requested file. It should match with current commit id of the file<FILEPATH> at head of the branch<BRANCH> in Gitlab repository<REPO>";
  public static final String UPDATE_FILE_CONFLICT_ERROR_EXPLANATION =
      "The input commit id of the requested file doesn't match with current commit id of the file<FILEPATH> at head of the branch<BRANCH> in Gitlab repository<REPO>, which results in update operation failure.";

  public static final String UPDATE_CONFLICT_ERROR_MESSAGE =
      "You are attempting to update a file that has changed since you started editing it.";
  public static final String UPDATE_FAILURE_HINT =
      ScmErrorHints.INVALID_CREDENTIALS + "\n- " + ScmErrorHints.OAUTH_ACCESS_FAILURE;
  public static final String UPDATE_FAILURE_EXPLANATION = UPDATE_FILE_FAILED + "\n- "
      + ScmErrorExplanations.INVALID_CONNECTOR_CREDS + "\n- " + ScmErrorExplanations.OAUTH_ACCESS_DENIED;

  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 400:
        //        NOTE: the extra handling is done harness side as gitlab throws the same error code in case of conflict
        //        and even when invalid branch is passed
        if (UPDATE_CONFLICT_ERROR_MESSAGE.equals(errorMessage)) {
          throw NestedExceptionUtils.hintWithExplanationException(
              ErrorMessageFormatter.formatMessage(UPDATE_FILE_CONFLICT_ERROR_HINT, errorMetadata),
              ErrorMessageFormatter.formatMessage(UPDATE_FILE_CONFLICT_ERROR_EXPLANATION, errorMetadata),
              new ScmConflictException(errorMessage));
        } else {
          throw NestedExceptionUtils.hintWithExplanationException(
              ErrorMessageFormatter.formatMessage(UPDATE_FILE_NOT_FOUND_ERROR_HINT, errorMetadata),
              ErrorMessageFormatter.formatMessage(UPDATE_FILE_NOT_FOUND_ERROR_EXPLANATION, errorMetadata),
              new ScmBadRequestException(errorMessage));
        }
      case 401:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(UPDATE_FAILURE_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(UPDATE_FAILURE_EXPLANATION, errorMetadata),
            new ScmUnauthorizedException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(UPDATE_FILE_NOT_FOUND_ERROR_HINT, errorMetadata),
            ErrorMessageFormatter.formatMessage(UPDATE_FILE_NOT_FOUND_ERROR_EXPLANATION, errorMetadata),
            new ScmBadRequestException(errorMessage));
      default:
        log.error(String.format("Error while updating Gitlab file: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
