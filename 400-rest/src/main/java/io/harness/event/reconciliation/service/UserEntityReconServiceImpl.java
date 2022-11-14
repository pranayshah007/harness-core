/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import static io.harness.event.reconciliation.service.LookerEntityReconServiceHelper.performReconciliationHelper;

import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.looker.LookerEntityReconRecordRepository;
import io.harness.lock.PersistentLocker;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.search.framework.TimeScaleEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.MorphiaKeyIterator;

@Singleton
@Slf4j
public class UserEntityReconServiceImpl implements LookerEntityReconService {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject LookerEntityReconRecordRepository lookerEntityReconRecordRepository;
  @Inject PersistentLocker persistentLocker;
  @Inject HPersistence persistence;

  private String CREATED_AT = "createdAt";

  @Override
  public ReconciliationStatus performReconciliation(
      String accountId, long durationStartTs, long durationEndTs, TimeScaleEntity timeScaleEntity) {
    return performReconciliationHelper(accountId, durationStartTs, durationEndTs, timeScaleEntity, timeScaleDBService,
        lookerEntityReconRecordRepository, persistentLocker, persistence);
  }

  public Set<String> getEntityIdsFromMongoDB(String accountId, long durationStartTs, long durationEndTs) {
    Set<String> userIds = new HashSet<>();
    MorphiaKeyIterator<User> users = persistence.createQuery(User.class)
                                         .field(UserKeys.accounts)
                                         .contains(accountId)
                                         .field(CREATED_AT)
                                         .exists()
                                         .field(CREATED_AT)
                                         .greaterThanOrEq(durationStartTs)
                                         .field(CREATED_AT)
                                         .lessThanOrEq(durationEndTs)
                                         .fetchKeys();
    users.forEachRemaining(userKey -> userIds.add((String) userKey.getId()));

    return userIds;
  }
}
