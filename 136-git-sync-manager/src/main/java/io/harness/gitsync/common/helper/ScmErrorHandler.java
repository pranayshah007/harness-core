/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmConflictException;
import io.harness.exception.ScmException;
import io.harness.exception.ScmInternalServerErrorException;
import io.harness.exception.ScmUnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.scm.beans.ScmErrorDetails;
import io.harness.gitsync.scm.beans.ScmGitMetaData;

import groovy.lang.Singleton;
import lombok.experimental.UtilityClass;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class ScmErrorHandler {
  public void processAndThrowException(int statusCode, ScmErrorDetails errorDetails, ScmGitMetaData errorMetadata) {
    handleError(statusCode, errorDetails, errorMetadata);
  }

  void handleError(int statusCode, ScmErrorDetails errorDetails, ScmGitMetaData errorMetadata) {
    switch (statusCode) {
      case 400:
      case 401:
        throw prepareException(new ScmBadRequestException(errorDetails.getErrorMessage()), errorDetails);
      case 409:
        throw prepareException(new ScmConflictException(errorDetails.getErrorMessage()), errorDetails);
      case 500:
        throw prepareException(new ScmInternalServerErrorException(errorDetails.getErrorMessage()), errorDetails);
      default:
        throw prepareException(new ScmUnexpectedException(errorDetails.getErrorMessage()), errorDetails);
    }
  }

  private WingsException prepareException(ScmException scmException, ScmErrorDetails scmErrorDetails) {
    WingsException finalException = scmException;
    if (isNotEmpty(scmErrorDetails.getExplanationMessage())) {
      finalException = new ExplanationException(scmErrorDetails.getExplanationMessage(), finalException);
    }
    if (isNotEmpty(scmErrorDetails.getHintMessage())) {
      finalException = new HintException(scmErrorDetails.getHintMessage(), finalException);
    }
    return finalException;
  }
}
