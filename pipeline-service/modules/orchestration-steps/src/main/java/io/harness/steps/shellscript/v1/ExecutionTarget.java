/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript.v1;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import lombok.Value;

@Value
@OwnedBy(CDC)
@RecasterAlias("io.harness.steps.shellscript.v1.ExecutionTarget")
public class ExecutionTarget {
  ParameterField<String> host;
  ParameterField<String> connector;
  ParameterField<String> dir;
}
