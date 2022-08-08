/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.util;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorMessageFormatter {
  public final String BRANCH_NAME_FORMAT_KEY = "<BRANCH>";
  public final String REPO_NAME_FORMAT_KEY = "<REPO>";
  public final String FILE_PATH_FORMAT_KEY = "<FILEPATH>";
  public final String TARGET_BRANCH_NAME_FORMAT_KEY = "<TARGET_BRANCH>";
  public final String NEW_BRANCH_NAME_FORMAT_KEY = "<NEW_BRANCH>";
  public final String CONNECTOR_REF_FORMAT_KEY = "<CONNECTOR>";

  public String formatMessage(String message, ErrorMetadata errorMetadata) {
    if (isEmpty(message)) {
      return message;
    }

    message = message.replaceAll(BRANCH_NAME_FORMAT_KEY, getFormattedKeyValue(errorMetadata.getBranchName()));
    message = message.replaceAll(REPO_NAME_FORMAT_KEY, getFormattedKeyValue(errorMetadata.getRepoName()));
    message = message.replaceAll(FILE_PATH_FORMAT_KEY, getFormattedKeyValue(errorMetadata.getFilepath()));
    message =
        message.replaceAll(TARGET_BRANCH_NAME_FORMAT_KEY, getFormattedKeyValue(errorMetadata.getTargetBranchName()));
    message = message.replaceAll(NEW_BRANCH_NAME_FORMAT_KEY, getFormattedKeyValue(errorMetadata.getNewBranchName()));
    message = message.replaceAll(CONNECTOR_REF_FORMAT_KEY, getFormattedKeyValue(errorMetadata.getConnectorRef()));
    return message;
  }

  private String getFormattedKeyValue(String value) {
    if (isEmpty(value)) {
      return "";
    }
    return " [" + value + "]";
  }
}
