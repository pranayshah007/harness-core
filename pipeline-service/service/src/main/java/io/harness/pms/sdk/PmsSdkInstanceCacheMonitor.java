/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

/**
 * A monitor to keep the pmsSdkInstance and instanceCache in sync
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSdkInstanceCacheMonitor {
  @Inject @Named("pmsSdkInstanceCache") Cache<String, PmsSdkInstance> instanceCache;
  @Inject PmsSdkInstanceService pmsSdkInstanceService;

  protected ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1,
      new ThreadFactoryBuilder()
          .setNameFormat("pipeline-sdk-instance-sync-Thread-%d")
          .setPriority(Thread.NORM_PRIORITY)
          .build());

  public void scheduleCacheSync() {
    long initialDelay = new SecureRandom().nextInt(1);
    try {
      executorService.scheduleAtFixedRate(this::syncCache, initialDelay, 1, TimeUnit.HOURS);
    } catch (Exception e) {
      log.error("Exception while creating a scheduled sdk instance cache sync", e);
    }
  }

  public void syncCache() {
    if (!pmsSdkInstanceService.shouldUseInstanceCache) {
      return;
    }
    log.info("Starting to monitor if sdkInstanceCache and sdkInstances in db are in sync");
    List<PmsSdkInstance> pmsSdkInstances = pmsSdkInstanceService.getActiveInstancesFromDB();
    for (PmsSdkInstance sdkInstance : pmsSdkInstances) {
      if (instanceCache.containsKey(sdkInstance.getName())) {
        PmsSdkInstance sdkInstanceCacheValue = instanceCache.get(sdkInstance.getName());
        if (!Objects.equals(sdkInstanceCacheValue, sdkInstance)) {
          log.warn("SdkInstance Redis Cache gone out of sync with the mongo collection, updating it");
          instanceCache.put(sdkInstance.getName(), sdkInstance);
        }
      } else {
        log.warn("SdkInstance Redis Cache gone out of sync with the mongo collection, updating it");
        instanceCache.put(sdkInstance.getName(), sdkInstance);
      }
    }
  }
}
