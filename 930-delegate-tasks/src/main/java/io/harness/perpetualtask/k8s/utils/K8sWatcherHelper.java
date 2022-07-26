/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.utils;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class K8sWatcherHelper {
  public static final String POD_WATCHER_PREFIX = "PodWatcher-%s";
  public static final String NODE_WATCHER_PREFIX = "NodeWatcher-%s";
  public static final String PV_WATCHER_PREFIX = "PVWatcher-%s";
  private static final long WAIT_TIME = Duration.ofMinutes(20).getSeconds();
  private static final int WATCHER_CREATION_MAX_COUNT = 4;
  private static final Map<String, Instant> WATCHER_ID_LAST_SEEN = new ConcurrentHashMap<>();
  private static final Map<String, Integer> WATCHER_ID_COUNT = new ConcurrentHashMap<>();

  public static void updateLastSeen(@NonNull final String watcherId, @NonNull final Instant lastSeen) {
    WATCHER_ID_LAST_SEEN.put(watcherId, lastSeen);
  }

  private static Instant getLastSeen(@NonNull final String watcherId) {
    return WATCHER_ID_LAST_SEEN.get(watcherId);
  }

  public static boolean shouldCreateWatcher(@NonNull final String watchId) {
    final boolean shouldCreateWatcher = (shouldCreatePodWatcher(watchId) || shouldCreateNodeWatcher(watchId))
        && WATCHER_ID_COUNT.getOrDefault(watchId, 0) < WATCHER_CREATION_MAX_COUNT;
    if (shouldCreateWatcher) {
      WATCHER_ID_COUNT.put(watchId, WATCHER_ID_COUNT.getOrDefault(watchId, 0) + 1);
      log.info("Count of watcher id {}: {}", watchId, WATCHER_ID_COUNT.get(watchId));
    }
    return shouldCreateWatcher;
  }

  public static boolean shouldCreatePodWatcher(@NonNull final String watchId) {
    return shouldCreateWatcher(watchId, POD_WATCHER_PREFIX);
  }

  public static boolean shouldCreateNodeWatcher(@NonNull final String watchId) {
    return shouldCreateWatcher(watchId, NODE_WATCHER_PREFIX);
  }

  public static boolean shouldCreatePVWatcher(@NonNull final String watchId) {
    return shouldCreateWatcher(watchId, PV_WATCHER_PREFIX);
  }

  private static boolean shouldCreateWatcher(@NonNull final String watchId, @NonNull final String prefix) {
    final String watcherId = String.format(prefix, watchId);
    final Instant lastSeen = getLastSeen(watcherId);
    log.info("Watcher id {} last seen: {}", watcherId, lastSeen);
    return Objects.isNull(lastSeen) || Duration.between(lastSeen, Instant.now()).abs().getSeconds() > WAIT_TIME;
  }

  public static void deleteWatcher(@NonNull final String watchId) {
    deleteWatcher(watchId, POD_WATCHER_PREFIX);
    deleteWatcher(watchId, NODE_WATCHER_PREFIX);
    deleteWatcher(watchId, PV_WATCHER_PREFIX);
    WATCHER_ID_COUNT.remove(watchId);
    log.info("Removed/Deleted watcher id: {}", watchId);
  }

  private static void deleteWatcher(@NonNull final String watchId, @NonNull final String prefix) {
    final String watcherId = String.format(prefix, watchId);
    WATCHER_ID_LAST_SEEN.remove(watcherId);
  }
}
