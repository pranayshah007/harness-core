/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.gitsync;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.manage.GlobalContextManager;
import io.harness.manage.GlobalContextManager.GlobalContextGuard;

@OwnedBy(PIPELINE)
public class PmsGitSyncBranchContextGuard implements AutoCloseable {
  private final GlobalContextGuard guard;

  public PmsGitSyncBranchContextGuard(GitSyncBranchContext gitSyncBranchContext, boolean findDefaultFromOtherBranches) {
    this.guard = GlobalContextManager.initGlobalContextGuard(GlobalContextManager.obtainGlobalContextCopy());
    if (gitSyncBranchContext != null && gitSyncBranchContext.getGitBranchInfo() != null) {
      // Set findDefaultFromOtherBranches if it's not already true. This is done so that we can fetch entities used by
      // steps (like connectors) from default branch of other repos also.
      if (findDefaultFromOtherBranches && !gitSyncBranchContext.getGitBranchInfo().isFindDefaultFromOtherRepos()) {
        gitSyncBranchContext = gitSyncBranchContext.withGitBranchInfo(
            gitSyncBranchContext.getGitBranchInfo().withFindDefaultFromOtherRepos(true));
      }
      GlobalContextManager.upsertGlobalContextRecord(gitSyncBranchContext);
    } else {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().build());
    }
  }

  @Override
  public void close() {
    if (guard != null) {
      guard.close();
    }
  }
}
