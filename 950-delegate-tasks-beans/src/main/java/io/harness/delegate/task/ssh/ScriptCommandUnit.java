/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.shell.TailFilePatternDto;
import io.harness.expression.Expression;
import io.harness.shell.ScriptType;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class ScriptCommandUnit implements NgCommandUnit {
  String name;
  ScriptType scriptType;
  @Expression(ALLOW_SECRETS) String script;
  @Expression(ALLOW_SECRETS) String workingDirectory;
  List<TailFilePatternDto> tailFilePatterns;
  @Expression(ALLOW_SECRETS) String command;

  @Override
  public String getCommandUnitType() {
    return NGCommandUnitType.SCRIPT;
  }
}
