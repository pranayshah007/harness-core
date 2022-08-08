/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils.artifacts;

import static java.util.Collections.emptyList;

import software.wings.beans.command.Command;

import java.util.List;

public class OtherArtifactCommands implements ArtifactCommands {
  @Override
  public boolean isInternal() {
    return false;
  }

  @Override
  public List<Command> getDefaultCommands() {
    return emptyList();
  }
}
