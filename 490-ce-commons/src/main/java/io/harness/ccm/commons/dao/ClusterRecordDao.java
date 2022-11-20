/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.persistence.HPersistence.returnNewOptions;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.ClusterRecord;
import io.harness.ccm.commons.entities.ClusterRecord.ClusterRecordKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;
import org.bson.types.ObjectId;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@OwnedBy(CE)
public class ClusterRecordDao {
  private final HPersistence persistence;
  @Inject
  public ClusterRecordDao(HPersistence persistence) {
    this.persistence = persistence;
  }

  public ClusterRecord get(String clusterId) {
    Query<ClusterRecord> query = persistence.createQuery(ClusterRecord.class, excludeValidate)
                                     .filter(ClusterRecordKeys.uuid, new ObjectId(clusterId));
    return query.get();
  }

  public ClusterRecord get(String accountId, String k8sBaseConnectorRefIdentifier) {
    Query<ClusterRecord> query =
        persistence.createQuery(ClusterRecord.class)
            .filter(ClusterRecordKeys.accountId, accountId)
            .filter(ClusterRecordKeys.k8sBaseConnectorRefIdentifier, k8sBaseConnectorRefIdentifier);
    return query.get();
  }

  public boolean delete(String accountId, String ceK8sConnectorIdentifier) {
    Query<ClusterRecord> query = persistence.createQuery(ClusterRecord.class, excludeValidate)
                                     .field(ClusterRecordKeys.accountId)
                                     .equal(accountId)
                                     .field(ClusterRecordKeys.ceK8sConnectorIdentifier)
                                     .equal(ceK8sConnectorIdentifier);
    return persistence.delete(query);
  }

  public ClusterRecord getByCEK8sIdentifier(String accountId, String ceK8sConnectorRefIdentifier) {
    Query<ClusterRecord> query = persistence.createQuery(ClusterRecord.class)
                                     .filter(ClusterRecordKeys.accountId, accountId)
                                     .filter(ClusterRecordKeys.ceK8sConnectorIdentifier, ceK8sConnectorRefIdentifier);
    return query.get();
  }

  public List<ClusterRecord> getByCEK8sIdentifierList(String accountId, List<String> ceK8sConnectorRefIdentifierList) {
    return persistence.createQuery(ClusterRecord.class)
        .field(ClusterRecordKeys.accountId)
        .equal(accountId)
        .field(ClusterRecordKeys.ceK8sConnectorIdentifier)
        .in(ceK8sConnectorRefIdentifierList)
        .asList();
  }

  public ClusterRecord upsert(ClusterRecord clusterRecord) {
    Query<ClusterRecord> query =
        persistence.createQuery(ClusterRecord.class)
            .filter(ClusterRecordKeys.accountId, clusterRecord.getAccountId())
            .filter(ClusterRecordKeys.k8sBaseConnectorRefIdentifier, clusterRecord.getK8sBaseConnectorRefIdentifier())
            .filter(ClusterRecordKeys.ceK8sConnectorIdentifier, clusterRecord.getCeK8sConnectorIdentifier());

    UpdateOperations<ClusterRecord> updateOperations =
        persistence.createUpdateOperations(ClusterRecord.class)
            .set(ClusterRecordKeys.accountId, clusterRecord.getAccountId())
            .set(ClusterRecordKeys.ceK8sConnectorIdentifier, clusterRecord.getCeK8sConnectorIdentifier())
            .set(ClusterRecordKeys.k8sBaseConnectorRefIdentifier, clusterRecord.getK8sBaseConnectorRefIdentifier());
    if (clusterRecord.getOrgIdentifier() != null) {
      updateOperations.set(ClusterRecordKeys.orgIdentifier, clusterRecord.getOrgIdentifier());
    }
    if (clusterRecord.getProjectIdentifier() != null) {
      updateOperations.set(ClusterRecordKeys.projectIdentifier, clusterRecord.getProjectIdentifier());
    }
    if (clusterRecord.getPerpetualTaskId() != null) {
      updateOperations.set(ClusterRecordKeys.perpetualTaskId, clusterRecord.getPerpetualTaskId());
    }
    if (clusterRecord.getClusterName() != null) {
      updateOperations.set(ClusterRecordKeys.clusterName, clusterRecord.getClusterName());
    }
    return persistence.upsert(query, updateOperations, upsertReturnNewOptions);
  }

  public ClusterRecord insertTask(ClusterRecord clusterRecord, String taskId) {
    Query<ClusterRecord> query =
        persistence.createQuery(ClusterRecord.class)
            .filter(ClusterRecordKeys.accountId, clusterRecord.getAccountId())
            .filter(ClusterRecordKeys.k8sBaseConnectorRefIdentifier, clusterRecord.getK8sBaseConnectorRefIdentifier())
            .filter(ClusterRecordKeys.ceK8sConnectorIdentifier, clusterRecord.getCeK8sConnectorIdentifier());

    UpdateOperations<ClusterRecord> updateOperations =
        persistence.createUpdateOperations(ClusterRecord.class).set(ClusterRecordKeys.perpetualTaskId, taskId);
    return persistence.findAndModify(query, updateOperations, returnNewOptions);
  }
}
