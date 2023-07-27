/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.scm.errorhandling;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmConflictException;
import io.harness.exception.ScmException;
import io.harness.exception.ScmInternalServerErrorException;
import io.harness.exception.ScmUnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.exceptions.GitErrorMetadataDTO;
import io.harness.gitsync.scm.beans.ScmErrorDetails;
import io.harness.gitsync.scm.beans.ScmGitMetaData;

import groovy.lang.Singleton;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Singleton
@OwnedBy(HarnessTeam.PL)
public class ScmErrorHandler {
  public final void processAndThrowException(
      int statusCode, ScmErrorDetails errorDetails, ScmGitMetaData errorMetadata) {
    handleError(statusCode, errorDetails, errorMetadata);
  }

  public final void processAndThrowException(int statusCode, ScmErrorDetails errorDetails) {
    handleError(statusCode, errorDetails);
  }

  void handleError(int statusCode, ScmErrorDetails errorDetails) {
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

  void handleError(int statusCode, ScmErrorDetails errorDetails, ScmGitMetaData errorMetadata) {
    switch (statusCode) {
      case 400:
      case 401:
        throw addMetadata(
            prepareException(new ScmBadRequestException(errorDetails.getErrorMessage()), errorDetails), errorMetadata);
      case 409:
        throw addMetadata(
            prepareException(new ScmConflictException(errorDetails.getErrorMessage()), errorDetails), errorMetadata);
      case 500:
        throw addMetadata(
            prepareException(new ScmInternalServerErrorException(errorDetails.getErrorMessage()), errorDetails),
            errorMetadata);
      default:
        throw addMetadata(
            prepareException(new ScmUnexpectedException(errorDetails.getErrorMessage()), errorDetails), errorMetadata);
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

  private WingsException addMetadata(WingsException wingsException, ScmGitMetaData errorMetadata) {
    wingsException.setMetadata(
        GitErrorMetadataDTO.builder().branch(errorMetadata.getBranchName()).repo(errorMetadata.getRepoName()).build());

    return wingsException;
  }
}
