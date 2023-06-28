/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DelegateRequestResources {
  protected static String EXECUTION_INFRASTRUCTURE = "/executionInfrastructure/%s";
  protected static String EXECUTION = "/execution/%s/%s";

  public static String getInitiateExecutionInfrastructureUri(String runnerType) {
    return String.format(EXECUTION_INFRASTRUCTURE, runnerType);
  }

  public static String getExecutionUri(String runnerType, String infraId) {
    return String.format(EXECUTION, runnerType, infraId);
  }
}
