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

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Value;

@Value
@JsonTypeName(ShellScriptBaseSourceV1.INLINE)
@OwnedBy(CDC)
@RecasterAlias("io.harness.steps.shellscript.v1.ShellScriptInlineSourceV1")
public class ShellScriptInlineSourceV1 implements ShellScriptBaseSourceV1 {
  ParameterField<String> script;

  @Override
  public String getType() {
    return ShellScriptBaseSourceV1.INLINE;
  }
}
