/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.reconciliation.looker;

import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecord;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
public class LookerEntityReconRecordRepository {
  @Inject HPersistence persistence;

  public LookerEntityReconRecord getLatestLookerEntityReconRecord(String accountId, String entityClass) {
    try (HIterator<LookerEntityReconRecord> iterator = new HIterator<>(
             persistence.createQuery(LookerEntityReconRecord.class)
                 .field(LookerEntityReconRecord.LookerEntityReconRecordKeys.accountId)
                 .equal(accountId)
                 .field(LookerEntityReconRecord.LookerEntityReconRecordKeys.entityClass)
                 .equal(entityClass)
                 .order(Sort.descending(LookerEntityReconRecord.LookerEntityReconRecordKeys.durationEndTs))
                 .fetch())) {
      if (!iterator.hasNext()) {
        return null;
      }
      return iterator.next();
    }
  }

  public LookerEntityReconRecord updateReconStatus(
      LookerEntityReconRecord reconRecord, ReconciliationStatus reconciliationStatus) {
    UpdateOperations updateOperations = persistence.createUpdateOperations(LookerEntityReconRecord.class);
    updateOperations.set(
        LookerEntityReconRecord.LookerEntityReconRecordKeys.reconciliationStatus, reconciliationStatus);
    updateOperations.set(LookerEntityReconRecord.LookerEntityReconRecordKeys.reconEndTs, System.currentTimeMillis());
    persistence.update(reconRecord, updateOperations);
    return reconRecord;
  }
}
