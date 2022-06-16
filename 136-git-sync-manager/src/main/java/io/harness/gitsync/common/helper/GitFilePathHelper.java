/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.gitsync.beans.GitRepositoryDTO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class GitFilePathHelper {
  GitSyncConnectorHelper gitSyncConnectorHelper;
  public static final String FILE_PATH_SEPARATOR = "/";
  public static final String HARNESS_DIRECTORY_PATH = ".harness/";
  public static final String FILE_PATH_INVALID_HINT = "Please check if the requested filepath is valid.";
  public static final String FILE_PATH_INVALID_DIRECTORY_EXPLANATION =
      "Harness File should be present under [.harness] directory.";
  public static final String FILE_PATH_INVALID_DIRECTORY_ERROR_FORMAT =
      "FilePath [%s] doesn't contain harness directory.";
  public static final String FILE_PATH_INVALID_EXTENSION_EXPLANATION =
      "Harness File should have [.yaml] or [.yml] extension.";
  public static final String FILE_PATH_INVALID_EXTENSION_ERROR_FORMAT = "FilePath [%s] doesn't have right extension.";
  public static final String NULL_FILE_PATH_ERROR_MESSAGE = "FilePath cannot be null or empty.";
  public static final String INVALID_FILE_PATH_FORMAT_ERROR_MESSAGE = "FilePath [%s] should not start or end with [/].";

  public void validateFilePath(String filePath) {
    validateFilePathFormat(filePath);
    validateFilePathIsInHarnessDirectory(filePath);
    validateFilePathHasCorrectExtension(filePath);
  }

  public String getFileUrl(
      Scope scope, String connectorRef, String branchName, String filePath, GitRepositoryDTO gitRepositoryDTO) {
    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), connectorRef, gitRepositoryDTO.getName());
    return scmConnector.getFileUrl(branchName, filePath, gitRepositoryDTO);
  }

  private static void validateFilePathFormat(String filePath) {
    if (isEmpty(filePath)) {
      throw new InvalidRequestException(NULL_FILE_PATH_ERROR_MESSAGE);
    }
    if (filePath.startsWith(FILE_PATH_SEPARATOR) || filePath.endsWith(FILE_PATH_SEPARATOR)) {
      throw new InvalidRequestException(String.format(INVALID_FILE_PATH_FORMAT_ERROR_MESSAGE, filePath));
    }
  }

  private static void validateFilePathIsInHarnessDirectory(String filePath) {
    if (!filePath.startsWith(HARNESS_DIRECTORY_PATH)) {
      throw NestedExceptionUtils.hintWithExplanationException(FILE_PATH_INVALID_HINT,
          FILE_PATH_INVALID_DIRECTORY_EXPLANATION,
          new InvalidRequestException(String.format(FILE_PATH_INVALID_DIRECTORY_ERROR_FORMAT, filePath)));
    }
  }

  private static void validateFilePathHasCorrectExtension(String filePath) {
    if (!filePath.endsWith(".yaml") && !filePath.endsWith(".yml")) {
      throw NestedExceptionUtils.hintWithExplanationException(FILE_PATH_INVALID_HINT,
          FILE_PATH_INVALID_EXTENSION_EXPLANATION,
          new InvalidRequestException(String.format(FILE_PATH_INVALID_EXTENSION_ERROR_FORMAT, filePath)));
    }
  }
}
