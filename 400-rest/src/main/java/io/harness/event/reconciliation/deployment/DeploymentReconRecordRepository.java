/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.reconciliation.deployment;

import static software.wings.app.ManagerCacheRegistrar.DEPLOYMENT_RECONCILIATION_CACHE;

import io.harness.event.reconciliation.deployment.DeploymentReconRecord.DeploymentReconRecordKeys;
import io.harness.mongo.index.BasicDBUtils;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import javax.cache.Cache;
import javax.cache.CacheException;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DeploymentReconRecordRepository {
  @Inject HPersistence persistence;
  @Inject
  @Named(DEPLOYMENT_RECONCILIATION_CACHE)
  private Cache<String, DeploymentReconRecord> deploymentReconRecordCache;

  public DeploymentReconRecord getLatestDeploymentReconRecord(@NotNull String accountId, String entityClass) {
    DeploymentReconRecord deploymentReconRecord =
        deploymentReconRecordCache.get(String.join("$$", accountId, entityClass));
    if (deploymentReconRecord != null) {
      return deploymentReconRecord;
    }
    FindOptions findOptions = new FindOptions();
    findOptions.hint(BasicDBUtils.getIndexObject(
        DeploymentReconRecord.mongoIndexes(), "accountId_entityClass_durationEndTs_sorted"));
    try (HIterator<DeploymentReconRecord> iterator =
             new HIterator<>(persistence.createQuery(DeploymentReconRecord.class)
                                 .field(DeploymentReconRecordKeys.accountId)
                                 .equal(accountId)
                                 .field(DeploymentReconRecordKeys.entityClass)
                                 .equal(entityClass)
                                 .order(Sort.descending(DeploymentReconRecordKeys.durationEndTs))
                                 .fetch(findOptions))) {
      if (!iterator.hasNext()) {
        return null;
      }
      deploymentReconRecord = iterator.next();
      updateDeploymentReconCache(deploymentReconRecord);
      return deploymentReconRecord;
    }
  }

  public String saveDeploymentReconRecord(@NotNull DeploymentReconRecord deploymentReconRecord) {
    updateDeploymentReconCache(deploymentReconRecord);
    return persistence.save(deploymentReconRecord);
  }

  public void updateDeploymentReconRecord(
      @NotNull DeploymentReconRecord deploymentReconRecord, UpdateOperations updateOperations) {
    updateDeploymentReconCache(deploymentReconRecord);
    persistence.update(deploymentReconRecord, updateOperations);
  }

  private void updateDeploymentReconCache(DeploymentReconRecord deploymentReconRecord) {
    try {
      deploymentReconRecordCache.put(
          String.join("$$", deploymentReconRecord.getAccountId(), deploymentReconRecord.getEntityClass()),
          deploymentReconRecord);
    } catch (CacheException e) {
      log.error("Unable to set deployment recon record data into cache", e);
    }
  }
}
