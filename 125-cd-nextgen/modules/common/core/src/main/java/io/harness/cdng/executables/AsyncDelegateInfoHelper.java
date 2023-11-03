/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.executables;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.common.beans.StepDelegateInfo;
import io.harness.client.DelegateSelectionLogHttpClient;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.network.SafeHttpCall;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AsyncDelegateInfoHelper {
  @Inject private DelegateSelectionLogHttpClient delegateSelectionLogHttpClient;

  private LoadingCache<DelegateSelectionLogParamsKey, DelegateSelectionLogParams> taskIdToDelegateSelectionLogParams =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .build(new CacheLoader<DelegateSelectionLogParamsKey, DelegateSelectionLogParams>() {
            @Override
            public DelegateSelectionLogParams load(@NotNull DelegateSelectionLogParamsKey key) throws IOException {
              return getDelegateSelectionLogParams(key.getAccountId(), key.getTaskId());
            }
          });

  private DelegateSelectionLogParams getDelegateSelectionLogParams(String accountId, String taskId) {
    try {
      DelegateSelectionLogParams delegateSelectionLogParams =
          SafeHttpCall.execute(delegateSelectionLogHttpClient.getDelegateInfo(accountId, taskId)).getResource();
      if (delegateSelectionLogParams == null) {
        log.warn("DelegateSelectionLogParams is null for account {} and task {}", accountId, taskId);
      }
      return delegateSelectionLogParams;
    } catch (Exception exception) {
      log.warn("Not able to talk to delegate service. Ignoring delegate Information", exception);
      return DelegateSelectionLogParams.builder().build();
    }
  }

  public Optional<StepDelegateInfo> getDelegateInformationForGivenTask(
      String taskName, String taskId, String accountId) {
    try {
      if (taskId != null) {
        DelegateSelectionLogParams resource = taskIdToDelegateSelectionLogParams.get(
            DelegateSelectionLogParamsKey.builder().accountId(accountId).taskId(taskId).build());
        return Optional.of(StepDelegateInfo.builder()
                               .delegateId(resource.getDelegateId())
                               .delegateName(resource.getDelegateName())
                               .taskId(taskId)
                               .taskName(taskName)
                               .build());
      }
      return Optional.empty();
    } catch (Exception exception) {
      log.warn("Not able to talk to delegate service. Ignoring delegate Information", exception);
      return Optional.empty();
    }
  }

  @Data
  @Builder
  private static class DelegateSelectionLogParamsKey {
    String accountId;
    String taskId;
  }
}
