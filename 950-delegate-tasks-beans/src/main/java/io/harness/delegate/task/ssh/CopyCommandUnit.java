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
import io.harness.expression.Expression;
import io.harness.ssh.FileSourceType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDP)
public class CopyCommandUnit implements NgCommandUnit {
  String name;
  @Expression(ALLOW_SECRETS) String destinationPath;
  FileSourceType sourceType;

  @Override
  public String getCommandUnitType() {
    return NGCommandUnitType.COPY;
  }

  @Override
  public String getWorkingDirectory() {
    return destinationPath;
  }
}
