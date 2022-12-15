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

import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.search.framework.TimeScaleEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.MorphiaKeyIterator;

@Singleton
@Slf4j
public class ServiceEntityReconServiceImpl implements LookerEntityReconService {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject LookerEntityReconRecordRepository lookerEntityReconRecordRepository;
  @Inject PersistentLocker persistentLocker;
  @Inject HPersistence persistence;

  @Override
  public ReconciliationStatus performReconciliation(
      String accountId, long durationStartTs, long durationEndTs, TimeScaleEntity timeScaleEntity) {
    return performReconciliationHelper(accountId, durationStartTs, durationEndTs, timeScaleEntity, timeScaleDBService,
        lookerEntityReconRecordRepository, persistentLocker, persistence);
  }

  public Set<String> getEntityIdsFromMongoDB(String accountId, long durationStartTs, long durationEndTs) {
    Set<String> serviceIds = new HashSet<>();
    MorphiaKeyIterator<Service> services = persistence.createQuery(Service.class)
                                               .field(ServiceKeys.accountId)
                                               .equal(accountId)
                                               .field(ServiceKeys.createdAt)
                                               .exists()
                                               .field(ServiceKeys.createdAt)
                                               .greaterThanOrEq(durationStartTs)
                                               .field(ServiceKeys.createdAt)
                                               .lessThanOrEq(durationEndTs)
                                               .fetchKeys();
    services.forEachRemaining(serviceKey -> serviceIds.add((String) serviceKey.getId()));

    return serviceIds;
  }
}
