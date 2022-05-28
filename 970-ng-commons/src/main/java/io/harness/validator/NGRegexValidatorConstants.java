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
  String IDENTIFIER_PATTERN = "^[a-zA-Z_][0-9a-zA-Z_$]{0,63}$";
  String NAME_PATTERN = "^[a-zA-Z_][-0-9a-zA-Z_\\s]{0,63}$";
  String TIMEOUT_PATTERN =
      "^(([1-9])+\\d+[s])|(((([1-9])+\\d*[mhwd])+([\\s]?\\d+[smhwd])*)|(<\\+input>.*)|(.*<\\+.*>.*))$";
  String VERSION_LABEL_PATTERN = "^[0-9a-zA-Z][^\\s]{0,63}$";
  String RUNTIME_OR_FIXED_IDENTIFIER_PATTERN = "\\<\\+input\\>|^[a-zA-Z_][0-9a-zA-Z_$]{0,63}$";
}
