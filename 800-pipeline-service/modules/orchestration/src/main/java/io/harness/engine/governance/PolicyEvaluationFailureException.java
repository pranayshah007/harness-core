/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.governance;

import static io.harness.eraro.ErrorCode.POLICY_EVALUATION_FAILURE;

import io.harness.eraro.Level;
import io.harness.exception.WingsException;
import io.harness.governance.GovernanceMetadata;

public class PolicyEvaluationFailureException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  GovernanceMetadata governanceMetadata;
  String yaml;

  public PolicyEvaluationFailureException(String message, GovernanceMetadata governanceMetadata, String yaml) {
    super(message, null, POLICY_EVALUATION_FAILURE, Level.ERROR, null, null);
    param(MESSAGE_KEY, message);
    this.governanceMetadata = governanceMetadata;
    this.yaml = yaml;
  }

  public GovernanceMetadata getGovernanceMetadata() {
    return governanceMetadata;
  }

  public String getYaml() {
    return yaml;
  }
}
