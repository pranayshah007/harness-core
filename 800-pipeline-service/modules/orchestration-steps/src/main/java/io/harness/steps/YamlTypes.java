/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PIPELINE)
public interface YamlTypes {
  String SOURCE = "source";
  String EXECUTION_TARGET = "executionTarget";
  String OUTPUT_VARIABLES = "outputVariables";
  String ENVIRONMENT_VARIABLES = "environmentVariables";
  String SPEC = "spec";
  String APPROVAL_INPUTS = "approverInputs";
  String APPROVERS = "approvers";
}
