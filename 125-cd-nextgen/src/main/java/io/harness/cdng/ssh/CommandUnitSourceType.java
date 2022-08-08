/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ssh.FileSourceType;

@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.ssh.CommandUnitSourceType")
public enum CommandUnitSourceType {
  Artifact(FileSourceType.ARTIFACT),
  Config(FileSourceType.CONFIG);

  private final FileSourceType sourceType;
  CommandUnitSourceType(FileSourceType sourceType) {
    this.sourceType = sourceType;
  }

  public FileSourceType getFileSourceType() {
    return sourceType;
  }
}
