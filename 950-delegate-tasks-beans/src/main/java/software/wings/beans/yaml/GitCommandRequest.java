/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.yaml;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import java.util.concurrent.TimeUnit;

/**
 * Created by anubhaw on 10/27/17.
 */
@OwnedBy(HarnessTeam.DX)
@TargetModule(HarnessModule._970_API_SERVICES_BEANS)
public class GitCommandRequest implements GitCommand {
  protected GitCommandType gitCommandType;
  public static final long gitRequestTimeout = TimeUnit.MINUTES.toMillis(20);
  private boolean useGitFetchCommandTimeout;

  public GitCommandRequest(GitCommandType gitCommandType) {
    this.gitCommandType = gitCommandType;
  }

  @Override
  public GitCommandType getGitCommandType() {
    return gitCommandType;
  }
}
