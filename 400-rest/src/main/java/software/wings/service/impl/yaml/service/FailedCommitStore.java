/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Value;

@OwnedBy(CDC)
@Singleton
public class FailedCommitStore {
  private final Cache<Commit, Boolean> commitsWhichExceedLimit =
      CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build();

  public boolean didExceedLimit(Commit commit) {
    return commitsWhichExceedLimit.getIfPresent(commit) != null;
  }

  /**
   * This method should be called with a Commit Id  which lead to {@link
   * io.harness.eraro.ErrorCode#USAGE_LIMITS_EXCEEDED} error.
   */
  public void exceededLimit(Commit commit) {
    commitsWhichExceedLimit.put(commit, true);
  }

  @Value
  @AllArgsConstructor
  public static class Commit {
    String commitId;
    String accountId;
  }
}
