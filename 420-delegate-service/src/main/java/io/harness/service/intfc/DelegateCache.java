/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.intfc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateTaskRank;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.DEL)
public interface DelegateCache {
  Delegate get(String accountId, String delegateId, boolean forceRefresh);

  DelegateGroup getDelegateGroup(String accountId, String delegateGroupId);

  DelegateProfile getDelegateProfile(String accountId, String delegateProfileId);

  void invalidateDelegateProfileCache(String accountId, String delegateProfileId);

  List<Delegate> getDelegatesForGroup(String accountId, String delegateGroupId);

  Set<String> getDelegateSupportedTaskTypes(@NotNull String accountId);

  long getTasksCount(@NotNull String accountId, @NotNull DelegateTaskRank rank);

  Map<String, Long> getTasksCountPerAccount(@NotNull DelegateTaskRank rank);

  Set<String> getAbortedTaskList(@NotNull String accountId);

  void addToAbortedTaskList(String accountId, Set<String> abortedTaskList);

  void removeFromAbortedTaskList(String accountId, String delegateTaskId);
}
