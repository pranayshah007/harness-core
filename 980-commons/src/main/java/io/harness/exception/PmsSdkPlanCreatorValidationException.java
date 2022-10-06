/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.PMS_INITIALIZE_SDK_EXCEPTION;

import io.harness.eraro.Level;

import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NonNull;

public class PmsSdkPlanCreatorValidationException extends WingsException {
  private static final String ERROR_MESSAGE = "Plan creators has unsupported filters or unsupported variables";

  @Getter private final Map<String, Set<String>> unsupportedFilters;
  @Getter private final Map<String, Set<String>> unsupportedVariables;

  public PmsSdkPlanCreatorValidationException(
      @NonNull Map<String, Set<String>> unsupportedFilters, @NonNull Map<String, Set<String>> unsupportedVariables) {
    super(ERROR_MESSAGE, null, PMS_INITIALIZE_SDK_EXCEPTION, Level.ERROR, null, null);
    this.unsupportedFilters = unsupportedFilters;
    this.unsupportedVariables = unsupportedVariables;
  }
}
