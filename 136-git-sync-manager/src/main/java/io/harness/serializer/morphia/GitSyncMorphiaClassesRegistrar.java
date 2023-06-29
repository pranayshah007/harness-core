/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.caching.entity.GitDefaultBranchCache;
import io.harness.gitsync.caching.entity.GitFileCache;
import io.harness.gitsync.common.beans.AzureRepoSCM;
import io.harness.gitsync.common.beans.BitbucketSCM;
import io.harness.gitsync.common.beans.GitBranch;
import io.harness.gitsync.common.beans.GitFileLocation;
import io.harness.gitsync.common.beans.GitSyncSettings;
import io.harness.gitsync.common.beans.GitToHarnessProgress;
import io.harness.gitsync.common.beans.GithubSCM;
import io.harness.gitsync.common.beans.GitlabSCM;
import io.harness.gitsync.common.beans.UserSourceCodeManager;
import io.harness.gitsync.common.beans.YamlChangeSet;
import io.harness.gitsync.common.beans.YamlGitConfig;
import io.harness.gitsync.core.beans.GitCommit;
import io.harness.gitsync.core.beans.GitFullSyncConfig;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo;
import io.harness.gitsync.core.fullsync.entity.GitFullSyncJob;
import io.harness.gitsync.gitfileactivity.beans.GitFileActivity;
import io.harness.gitsync.gitfileactivity.beans.GitFileActivitySummary;
import io.harness.gitsync.gitsyncerror.beans.GitSyncError;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

@OwnedBy(DX)
public class GitSyncMorphiaClassesRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(YamlGitConfig.class);
    set.add(GitCommit.class);
    set.add(YamlChangeSet.class);
    set.add(GitFileActivitySummary.class);
    set.add(GitSyncError.class);
    set.add(GitFileActivity.class);
    set.add(GitFileLocation.class);
    set.add(GitBranch.class);
    set.add(GitSyncSettings.class);
    set.add(GitToHarnessProgress.class);
    set.add(GitFullSyncEntityInfo.class);
    set.add(GitFullSyncConfig.class);
    set.add(GitFullSyncJob.class);
    set.add(GitFileCache.class);
    set.add(GitDefaultBranchCache.class);
    set.add(UserSourceCodeManager.class);
    set.add(AzureRepoSCM.class);
    set.add(GithubSCM.class);
    set.add(GitlabSCM.class);
    set.add(BitbucketSCM.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // Nothing to register
  }
}
