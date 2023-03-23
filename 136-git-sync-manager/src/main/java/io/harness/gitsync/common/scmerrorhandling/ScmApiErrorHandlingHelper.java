/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.beans.ScmApis;
import io.harness.gitsync.common.dtos.GitErrorMetadata;
import io.harness.gitsync.common.dtos.RepoProviders;
import io.harness.gitsync.common.helper.RepoProviderHelper;
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.gitsync.common.scmerrorhandling.handlers.DefaultScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(PL)
public class ScmApiErrorHandlingHelper {
  public final String DEFAULT_ERROR_MESSAGE = "Failed to perform GIT operation.";
  private static final Map<ConnectorType, Set<Integer>> failureErrorCodeExceptions =
      ImmutableMap.<ConnectorType, Set<Integer>>builder()
          .put(ConnectorType.AZURE_REPO, new HashSet<>(Collections.singletonList(203)))
          .build();

  public void processAndThrowError(ScmApis scmAPI, ConnectorType connectorType, String repoUrl, int statusCode,
      String errorMessage, ErrorMetadata errorMetadata) {
    if (errorMetadata == null) {
      errorMetadata = ErrorMetadata.builder().build();
    }
    errorMessage = formatErrorMessage(errorMessage);

    ScmApiErrorHandler scmAPIErrorHandler = getScmAPIErrorHandler(scmAPI, connectorType, repoUrl);
    try {
      scmAPIErrorHandler.handleError(statusCode, errorMessage, errorMetadata);
    } catch (WingsException exception) {
      exception.setMetadata(GitErrorMetadata.builder()
                                .branch(errorMetadata.getBranchName())
                                .filepath(errorMetadata.getFilepath())
                                .build());
      throw exception;
    }
  }

  public boolean isFailureResponse(int statusCode, ConnectorType connectorType) {
    return statusCode >= 300
        || failureErrorCodeExceptions.getOrDefault(connectorType, new HashSet<>()).contains(statusCode);
  }

  @VisibleForTesting
  protected ScmApiErrorHandler getScmAPIErrorHandler(ScmApis scmApi, ConnectorType connectorType, String repoUrl) {
    RepoProviders repoProvider = RepoProviderHelper.getRepoProviderType(connectorType, repoUrl);
    ScmApiErrorHandler scmApiErrorHandler = ScmApiErrorHandlerFactory.getHandler(scmApi, repoProvider);
    if (scmApiErrorHandler == null) {
      log.error(String.format(
          "No scm API handler registered for API: %s, providerType: %s", scmApi.toString(), repoProvider));
      return new DefaultScmApiErrorHandler();
    }
    return scmApiErrorHandler;
  }

  private String formatErrorMessage(String errorMessage) {
    return EmptyPredicate.isEmpty(errorMessage) ? DEFAULT_ERROR_MESSAGE : errorMessage;
  }
}
