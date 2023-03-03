/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.schema.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
@OwnedBy(DX)
public enum SupportedPossibleFieldTypes {
  string,
  number,
  integer,
  bool,
  list,
  map,
  expression,
  runtimeButNotExecutionTime, // to support execution input field type, like <+input>.executionInput(). It also includes
                              // runtime inputs.
  numberString,
  numberStringWithEmptyValue,
  runtime, // to support runtime field type, like <+input>. It also includes executionInput field, like
           // <+input>.executionInput()
  /*
   Supports runtimeInputs and executionTimeInputs along with empty strings
   To avoid schema issues when runtime object inputs are provided as empty string("")
   As which is behaviour of UI when user switch from visual to yaml without giving inputs
   */
  runtimeEmptyStringAllowed,
  // allow only <+input>
  onlyRuntimeInputAllowed,

  /**
   * Only used for setting default.
   */
  none
}
