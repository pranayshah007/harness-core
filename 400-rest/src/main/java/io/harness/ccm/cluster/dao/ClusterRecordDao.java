/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.cluster.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HPersistence.returnNewOptions;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.cluster.entities.Cluster;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.cluster.entities.ClusterRecord.ClusterRecordKeys;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@Singleton
@OwnedBy(CE)
@TargetModule(HarnessModule._490_CE_COMMONS)
public class ClusterRecordDao {
  public static final String cloudProviderField = ClusterRecordKeys.cluster + "."
      + "cloudProviderId";

  @Inject private HPersistence persistence;

  private Query<ClusterRecord> getQuery(ClusterRecord clusterRecord) {
    Cluster cluster = clusterRecord.getCluster();
    Query<ClusterRecord> query = persistence.createQuery(ClusterRecord.class, excludeValidate)
                                     .filter(ClusterRecordKeys.accountId, clusterRecord.getAccountId());
    cluster.addRequiredQueryFilters(query); // the filter differs depending on the Cluster type
    return query;
  }

  public ClusterRecord get(ClusterRecord clusterRecord) {
    Preconditions.checkNotNull(clusterRecord);
    Query<ClusterRecord> query = getQuery(clusterRecord);
    return query.get();
  }

  public ClusterRecord get(String clusterId) {
    Query<ClusterRecord> query = persistence.createQuery(ClusterRecord.class, excludeValidate)
                                     .filter(ClusterRecordKeys.uuid, new ObjectId(clusterId));
    return query.get();
  }

  public List<ClusterRecord> list(String accountId, String cloudProviderId, Integer count, Integer startIndex) {
    Query<ClusterRecord> query = persistence.createQuery(ClusterRecord.class, excludeValidate)
                                     .field(ClusterRecordKeys.accountId)
                                     .equal(accountId);
    if (!isEmpty(cloudProviderId)) {
      query.field(cloudProviderField).equal(cloudProviderId);
    }
    return query.asList(new FindOptions().skip(startIndex).limit(count));
  }

  public List<ClusterRecord> listCeEnabledClusters(String accountId) {
    Query<ClusterRecord> query = persistence.createQuery(ClusterRecord.class, excludeValidate)
                                     .field(ClusterRecordKeys.accountId)
                                     .equal(accountId)
                                     .field(ClusterRecordKeys.perpetualTaskIds)
                                     .exists();
    return query.asList(new FindOptions());
  }

  public ClusterRecord upsertCluster(ClusterRecord clusterRecord) {
    Query<ClusterRecord> query = getQuery(clusterRecord);
    UpdateOperations<ClusterRecord> updateOperations =
        persistence.createUpdateOperations(ClusterRecord.class)
            .set(ClusterRecordKeys.accountId, clusterRecord.getAccountId())
            .set(ClusterRecordKeys.cluster, clusterRecord.getCluster());
    return persistence.upsert(query, updateOperations, upsertReturnNewOptions);
  }

  public boolean delete(ClusterRecord clusterRecord) {
    Query<ClusterRecord> query = getQuery(clusterRecord);
    return persistence.delete(query);
  }

  public boolean delete(String accountId, String cloudProviderId) {
    Query<ClusterRecord> query = persistence.createQuery(ClusterRecord.class, excludeValidate)
                                     .field(ClusterRecordKeys.accountId)
                                     .equal(accountId)
                                     .field(cloudProviderField)
                                     .equal(cloudProviderId);
    return persistence.delete(query);
  }

  public ClusterRecord setStatus(String accountId, String cloudProviderId, boolean isDeactivated) {
    Query<ClusterRecord> query = persistence.createQuery(ClusterRecord.class, excludeValidate)
                                     .field(ClusterRecordKeys.accountId)
                                     .equal(accountId)
                                     .field(cloudProviderField)
                                     .equal(cloudProviderId);
    UpdateOperations<ClusterRecord> updateOperations =
        persistence.createUpdateOperations(ClusterRecord.class).set(ClusterRecordKeys.isDeactivated, isDeactivated);
    return persistence.findAndModify(query, updateOperations, returnNewOptions);
  }

  public ClusterRecord insertTask(ClusterRecord clusterRecord, String taskId) {
    Query<ClusterRecord> query = getQuery(clusterRecord);
    UpdateOperations<ClusterRecord> updateOperations =
        persistence.createUpdateOperations(ClusterRecord.class).addToSet(ClusterRecordKeys.perpetualTaskIds, taskId);
    return persistence.findAndModify(query, updateOperations, returnNewOptions);
  }

  public ClusterRecord removeTask(ClusterRecord clusterRecord, String taskId) {
    Query<ClusterRecord> query = getQuery(clusterRecord);
    UpdateOperations<ClusterRecord> updateOperations =
        persistence.createUpdateOperations(ClusterRecord.class).removeAll(ClusterRecordKeys.perpetualTaskIds, taskId);
    return persistence.findAndModify(query, updateOperations, returnNewOptions);
  }

  public List<ClusterRecord> getByAccountId(String accountId) {
    Query<ClusterRecord> query =
        persistence.createQuery(ClusterRecord.class).field(ClusterRecordKeys.accountId).equal(accountId);
    return query.asList();
  }
}
