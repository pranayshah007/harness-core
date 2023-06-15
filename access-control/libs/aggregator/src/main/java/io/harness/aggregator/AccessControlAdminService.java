/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator;

import static java.lang.Boolean.TRUE;

import io.harness.aggregator.models.BlockedAccount;
import io.harness.aggregator.repositories.BlockedEntityRepository;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class AccessControlAdminService {
  private final BlockedEntityRepository blockedEntityRepository;
  private final GenerateACLsFromRoleAssignmentsJob generateACLsFromRoleAssignmentsJob;
  private final ExecutorService executorService;

  private final LoadingCache<String, Boolean> blockedAccountCache;

  @Inject
  public AccessControlAdminService(BlockedEntityRepository blockedEntityRepository,
      GenerateACLsFromRoleAssignmentsJob generateACLsFromRoleAssignmentsJob) {
    this.blockedEntityRepository = blockedEntityRepository;
    this.generateACLsFromRoleAssignmentsJob = generateACLsFromRoleAssignmentsJob;
    executorService =
        Executors.newFixedThreadPool(2, new ThreadFactoryBuilder().setNameFormat("blocked-event-processor-%d").build());
    blockedAccountCache = Caffeine.newBuilder()
                              .maximumSize(10000)
                              .expireAfterWrite(1, TimeUnit.MINUTES)
                              .build(accountId -> blockedEntityRepository.find(accountId).isPresent());
  }

  public List<BlockedAccount> getAllBlockedAccounts() {
    return blockedEntityRepository.findAll();
  }

  public BlockedAccount block(String accountIdentifier) {
    Optional<BlockedAccount> existingBlockedAccount = blockedEntityRepository.find(accountIdentifier);
    if (existingBlockedAccount.isEmpty()) {
      BlockedAccount blockedAccount =
          blockedEntityRepository.create(BlockedAccount.builder().accountIdentifier(accountIdentifier).build());

      blockedAccountCache.put(accountIdentifier, true);
      return blockedAccount;
    }
    blockedAccountCache.put(accountIdentifier, true);
    return existingBlockedAccount.get();
  }

  public void unblock(String accountIdentifier) {
    blockedEntityRepository.delete(accountIdentifier);

    blockedAccountCache.invalidate(accountIdentifier);
    executorService.execute(() -> generateACLsFromRoleAssignmentsJob.migrate(accountIdentifier));
  }

  public boolean isBlocked(String accountIdentifier) {
    if (accountIdentifier == null) {
      return false;
    }
    return TRUE.equals(blockedAccountCache.get(accountIdentifier));
  }
}
