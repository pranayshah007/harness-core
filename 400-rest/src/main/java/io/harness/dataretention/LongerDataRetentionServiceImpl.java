/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention;

import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import software.wings.beans.datatretention.LongerDataRetentionState;
import software.wings.beans.datatretention.LongerDataRetentionState.LongerDataRetentionStateKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LongerDataRetentionServiceImpl implements LongerDataRetentionService {
  @Inject WingsPersistence wingsPersistence;
  @Inject PersistentLocker persistentLocker;

  @Override
  public boolean updateLongerDataRetentionState(String key, boolean status, String accountId) {
    Query<LongerDataRetentionState> query = wingsPersistence.createQuery(LongerDataRetentionState.class)
                                                .field(LongerDataRetentionStateKeys.accountId)
                                                .equal(accountId);
    try (AcquiredLock lock =
             persistentLocker.tryToAcquireLock("LongerDataRetention-" + accountId, Duration.ofMinutes(1))) {
      LongerDataRetentionState longerDataRetentionState = query.get();
      if (longerDataRetentionState == null) {
        Map<String, Boolean> keyStatusMap = Collections.singletonMap(key, status);
        wingsPersistence.insert(
            LongerDataRetentionState.builder().accountId(accountId).keyRetentionCompletedMap(keyStatusMap).build());
        return true;
      }
      longerDataRetentionState.getKeyRetentionCompletedMap().put(key, status);

      UpdateOperations<LongerDataRetentionState> updateOperations =
          wingsPersistence.createUpdateOperations(LongerDataRetentionState.class);
      setUnset(updateOperations, LongerDataRetentionStateKeys.keyRetentionCompletedMap,
          longerDataRetentionState.getKeyRetentionCompletedMap());

      wingsPersistence.update(query, updateOperations);
      return true;
    }
  }

  @Override
  public boolean isLongerDataRetentionCompleted(String key, String accountId) {
    LongerDataRetentionState longerDataRetentionState = wingsPersistence.createQuery(LongerDataRetentionState.class)
                                                            .filter(LongerDataRetentionStateKeys.accountId, accountId)
                                                            .get();
    if (longerDataRetentionState == null || longerDataRetentionState.getKeyRetentionCompletedMap() == null) {
      return false;
    }
    return longerDataRetentionState.getKeyRetentionCompletedMap().getOrDefault(key, false);
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(wingsPersistence.createQuery(LongerDataRetentionState.class)
                                .filter(LongerDataRetentionStateKeys.accountId, accountId));
  }
}
