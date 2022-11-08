/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.reconciliation.deployment;

import io.harness.event.reconciliation.deployment.DeploymentReconRecord.DeploymentReconRecordKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Sort;

@Singleton
@Slf4j
public class DeploymentReconRecordRepository {
  @Inject HPersistence persistence;

  public DeploymentReconRecord getLatestDeploymentReconRecord(@NotNull String accountId, String entityClass) {
    try (HIterator<DeploymentReconRecord> iterator =
             new HIterator<>(persistence.createQuery(DeploymentReconRecord.class)
                                 .field(DeploymentReconRecordKeys.accountId)
                                 .equal(accountId)
                                 .field(DeploymentReconRecordKeys.entityClass)
                                 .equal(entityClass)
                                 .order(Sort.descending(DeploymentReconRecordKeys.durationEndTs))
                                 .fetch())) {
      if (!iterator.hasNext()) {
        return null;
      }
      return iterator.next();
    }
  }
}
