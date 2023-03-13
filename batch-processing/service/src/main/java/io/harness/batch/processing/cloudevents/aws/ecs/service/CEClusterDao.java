/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.ccm.commons.entities.billing.CECluster;
import io.harness.ccm.commons.entities.billing.CECluster.CEClusterKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CEClusterDao {
  private final HPersistence hPersistence;

  @Inject
  public CEClusterDao(HPersistence hPersistence) {
    this.hPersistence = hPersistence;
  }

  public boolean create(CECluster ceCluster) {
    return hPersistence.save(ceCluster) != null;
  }

  public List<CECluster> getByInfraAccountId(String accountId, String infraAccountId) {
    return hPersistence.createQuery(CECluster.class)
        .field(CEClusterKeys.accountId)
        .equal(accountId)
        .field(CEClusterKeys.infraAccountId)
        .equal(infraAccountId)
        .asList();
  }

  public List<CECluster> getCECluster(String accountId) {
    return hPersistence.createQuery(CECluster.class).field(CEClusterKeys.accountId).equal(accountId).asList();
  }

  public Map<String, CECluster> getClusterIdMapping(String accountId) {
    return hPersistence.createQuery(CECluster.class)
        .field(CEClusterKeys.accountId)
        .equal(accountId)
        .project(CEClusterKeys.uuid, true)
        .project(CEClusterKeys.clusterName, true)
        .asList()
        .stream()
        .collect(Collectors.toMap(CECluster::getUuid, Function.identity()));
  }

  public boolean deleteCluster(String uuid) {
    Query<CECluster> query =
        hPersistence.createQuery(CECluster.class, excludeAuthority).field(CEClusterKeys.uuid).equal(uuid);
    return hPersistence.delete(query);
  }

  public boolean deactivateCluster(CECluster ceCluster) {
    UpdateOperations<CECluster> updateOperations = hPersistence.createUpdateOperations(CECluster.class);

    updateOperations.set(CEClusterKeys.isDeactivated, true);
    UpdateResults updateResults = hPersistence.update(ceCluster, updateOperations);
    return updateResults.getUpdatedCount() > 0;
  }

  public boolean upsert(CECluster ceCluster) {
    return (hPersistence.upsert(hPersistence.createQuery(CECluster.class)
                                    .field(CEClusterKeys.accountId)
                                    .equal(ceCluster.getAccountId())
                                    .field(CEClusterKeys.infraAccountId)
                                    .equal(ceCluster.getInfraAccountId())
                                    .field(CEClusterKeys.region)
                                    .equal(ceCluster.getRegion())
                                    .field(CEClusterKeys.clusterName)
                                    .equal(ceCluster.getClusterName()),
               hPersistence.createUpdateOperations(CECluster.class)
                   .set(CEClusterKeys.accountId, ceCluster.getAccountId())
                   .set(CEClusterKeys.clusterName, ceCluster.getClusterName())
                   .set(CEClusterKeys.clusterArn, ceCluster.getClusterArn())
                   .set(CEClusterKeys.region, ceCluster.getRegion())
                   .set(CEClusterKeys.infraAccountId, ceCluster.getInfraAccountId())
                   .set(CEClusterKeys.infraMasterAccountId, ceCluster.getInfraMasterAccountId())
                   .set(CEClusterKeys.parentAccountSettingId, ceCluster.getParentAccountSettingId())
                   .set(CEClusterKeys.isDeactivated, false)
                   .set(CEClusterKeys.labels, ceCluster.getLabels())
                   .set(CEClusterKeys.hash, ceCluster.getHash()),
               HPersistence.upsertReturnNewOptions))
        != null;
  }
}
