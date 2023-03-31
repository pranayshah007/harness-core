/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.user.jobs;

import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.user.beans.entity.UserEventEntity;
import io.harness.idp.user.repositories.UserEventRepository;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.IDP)
public class UserSyncJob implements Managed {
  private static final long DELAY_IN_MINUTES = TimeUnit.MINUTES.toMinutes(10);
  private ScheduledExecutorService executorService;
  private final UserEventRepository userEventRepository;
  private final BackstageResourceClient backstageResourceClient;

  @Inject
  public UserSyncJob(@Named("userSyncer") ScheduledExecutorService executorService,
      UserEventRepository userEventRepository, BackstageResourceClient backstageResourceClient) {
    this.executorService = executorService;
    this.userEventRepository = userEventRepository;
    this.backstageResourceClient = backstageResourceClient;
  }

  @Override
  public void start() throws Exception {
    executorService =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("user-sync-job").build());
    executorService.scheduleWithFixedDelay(this::run, 0, DELAY_IN_MINUTES, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    executorService.shutdownNow();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
  }

  public void run() {
    log.info("User sync job started");
    List<UserEventEntity> userEventEntities = userEventRepository.findAllByHasEvent(true);
    userEventEntities.forEach(userEventEntity -> {
      String accountIdentifier = userEventEntity.getAccountIdentifier();
      try {
        userEventEntity.setHasEvent(false);
        userEventRepository.saveOrUpdate(userEventEntity);
        log.info("Processing event for account {}", accountIdentifier);
        getGeneralResponse(backstageResourceClient.providerRefresh(accountIdentifier));
      } catch (Exception e) {
        log.error("Could not sync users  for account {}", accountIdentifier, e);
      }
    });

    log.info("User sync job completed");
  }
}
