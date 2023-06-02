/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.validator;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PIPELINE)
public interface NGRegexValidatorConstants {
  String IDENTIFIER_PATTERN = "^[a-zA-Z_][0-9a-zA-Z_]{0,127}$";
  String PIPELINE_IDENTIFIER_PATTERN = "^[a-zA-Z_][0-9a-zA-Z_]{0,127}$";
  String NON_EMPTY_STRING_PATTERN = "^(?=\\s*\\S).*$";
  String VARIABLE_NAME_PATTERN = "^[a-zA-Z_][0-9a-zA-Z_\\.$]{0,127}$";
  String NAME_PATTERN = "^[a-zA-Z_][-0-9a-zA-Z_\\s]{0,127}$";
  String TIMEOUT_PATTERN =
      "^(([1-9])+\\d+[s])|(((([1-9])+\\d*[mhwd])+([\\s]?\\d+[smhwd])*)|(<\\+input>.*)|(.*<\\+.*>.*)|(^$))$";
  String EXPRESSION_PATTERN_WITHOUT_EXECUTION_INPUT = "(.*<\\+.*>(?!.*\\.executionInput\\(\\)).*)";
  String TIMEOUT_PATTERN_WITHOUT_EXECUTION_INPUT = "^(([1-9])+\\d+[s])|(((([1-9])+\\d*[mhwd])+([\\s]?\\d+[smhwd])*)|"
      + EXPRESSION_PATTERN_WITHOUT_EXECUTION_INPUT + "|(^$))$";
  String VERSION_LABEL_PATTERN = "^[0-9a-zA-Z][^\\s]{0,63}$";
  String RUNTIME_OR_FIXED_IDENTIFIER_PATTERN = "\\<\\+input\\>|^[a-zA-Z_][0-9a-zA-Z_$]{0,63}$";
  // Use this pattern when want a string to have any value but not execution input.
  String STRING_BUT_NOT_EXECUTION_INPUT_PATTERN = "^(?!<\\+input>.*\\.executionInput\\(\\).*)(.*)";
}
