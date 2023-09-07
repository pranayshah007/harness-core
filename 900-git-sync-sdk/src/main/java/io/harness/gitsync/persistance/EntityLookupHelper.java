/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.persistance;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub;
import io.harness.gitsync.IsOldGitSyncEnabledForModule;
import io.harness.gitsync.common.helper.GitSyncGrpcClientUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.cache.Cache;
import javax.cache.CacheException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;

@ParametersAreNonnullByDefault
@Singleton
@Slf4j
@OwnedBy(DX)
public class EntityLookupHelper implements EntityKeySource {
  private final @NonNull Cache<String, Boolean> gitEnabledCache;
  HarnessToGitPushInfoServiceBlockingStub harnessToGitPushInfoServiceBlockingStub;

  @Inject
  public EntityLookupHelper(HarnessToGitPushInfoServiceBlockingStub harnessToGitPushInfoServiceBlockingStub,
      @Named("gitSyncEnabledCache") Cache<String, Boolean> gitEnabledCache) {
    this.gitEnabledCache = gitEnabledCache;
    this.harnessToGitPushInfoServiceBlockingStub = harnessToGitPushInfoServiceBlockingStub;
  }

  private String getKey(EntityScopeInfo entityScopeInfo) {
    String scope = "";
    if (!StringUtils.isEmpty(entityScopeInfo.getAccountId())) {
      scope += "/" + entityScopeInfo.getAccountId();
    }
    if (!StringUtils.isEmpty(entityScopeInfo.getOrgId().getValue())) {
      scope += "/" + entityScopeInfo.getOrgId().getValue();
    }

    if (!StringUtils.isEmpty(entityScopeInfo.getProjectId().getValue())) {
      scope += "/" + entityScopeInfo.getProjectId().getValue();
    }
    return scope;
  }
  @Override
  public boolean fetchKey(EntityScopeInfo entityScopeInfo) {
    final String scope = getKey(entityScopeInfo);
    Boolean isGitEnabled = gitEnabledCache.get(scope);
    if (isGitEnabled != null) {
      return isGitEnabled;
    } else {
      IsOldGitSyncEnabledForModule isOldGitSyncEnabledForModule =
          IsOldGitSyncEnabledForModule.newBuilder().setEntityScopeInfo(entityScopeInfo).setIsNotFFModule(true).build();
      final Boolean gitSyncEnabled =
          GitSyncGrpcClientUtils
              .retryAndProcessException(
                  harnessToGitPushInfoServiceBlockingStub::isOldGitSyncEnabledForModule, isOldGitSyncEnabledForModule)
              .getIsEnabled();
      try {
        gitEnabledCache.put(scope, gitSyncEnabled);
      } catch (CacheException e) {
        log.error("Unable to set git enabled data into cache", e);
      }
      return gitSyncEnabled;
    }
  }

  @Override
  public void updateKey(EntityScopeInfo entityScopeInfo) {
    log.info("Invalidating cache {}", entityScopeInfo);
    gitEnabledCache.remove(getKey(entityScopeInfo));
  }
}
