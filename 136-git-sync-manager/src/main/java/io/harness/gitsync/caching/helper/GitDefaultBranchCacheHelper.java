/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.caching.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.GitServiceConfiguration;
import io.harness.gitsync.caching.utils.GitCacheUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Date;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitDefaultBranchCacheHelper {
  private GitServiceConfiguration gitServiceConfiguration;

  @Inject
  public GitDefaultBranchCacheHelper(
      @Named("gitServiceConfiguration") GitServiceConfiguration gitServiceConfiguration) {
    this.gitServiceConfiguration = gitServiceConfiguration;
  }

  public Date getValidUntilTime(long currentTime) {
    return GitCacheUtils.getValidUntilTime(currentTime, getMaxCacheDuration());
  }

  private long getMaxCacheDuration() {
    return gitServiceConfiguration.getGitServiceCacheConfiguration().getDefaultBranchCacheDurationTimeInMinutes();
  }
}
