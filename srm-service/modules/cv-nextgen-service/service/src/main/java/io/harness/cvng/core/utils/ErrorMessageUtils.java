/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils;

import io.harness.cvng.core.beans.params.ProjectParams;

import java.util.Objects;

public class ErrorMessageUtils {
  private ErrorMessageUtils() {}

  public static String generateErrorMessageFromParam(String paramName) {
    return paramName + " should not be null";
  }

  public static String generateErrorMessageFromProjectParam(ProjectParams projectParams) {
    String errorText = "";
    if (Objects.nonNull(projectParams.getAccountIdentifier())) {
      errorText += String.format("accountId %s", projectParams.getAccountIdentifier());
    }
    if (Objects.nonNull(projectParams.getOrgIdentifier())) {
      errorText += String.format(", orgIdentifier %s", projectParams.getOrgIdentifier());
    }
    if (Objects.nonNull(projectParams.getProjectIdentifier())) {
      errorText += String.format(" and projectIdentifier %s", projectParams.getProjectIdentifier());
    }
    return errorText;
  }
}
