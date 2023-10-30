/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.service;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static dev.morphia.mapping.Mapper.ID_KEY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import software.wings.dl.WingsMongoPersistence;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails.InstanceSyncTaskDetailsKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import dev.morphia.query.CountOptions;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class CgInstanceSyncTaskDetailsService {
  private final WingsMongoPersistence mongoPersistence;

  public void save(InstanceSyncTaskDetails taskDetails) {
    if (Objects.isNull(taskDetails)) {
      log.warn("Cannot save null task details. Doing nothing");
      return;
    }

    mongoPersistence.save(taskDetails);
  }

  public boolean delete(String taskDetailsId) {
    return mongoPersistence.delete(InstanceSyncTaskDetails.class, taskDetailsId);
  }
  public boolean deleteByInfraMappingId(String accountId, String infraMappingId) {
    Query<InstanceSyncTaskDetails> query = mongoPersistence.createQuery(InstanceSyncTaskDetails.class)
                                               .filter(InstanceSyncTaskDetailsKeys.accountId, accountId)
                                               .filter(InstanceSyncTaskDetailsKeys.infraMappingId, infraMappingId);
    return mongoPersistence.delete(query);
  }

  public InstanceSyncTaskDetails getForId(String taskDetailsId) {
    return mongoPersistence.createQuery(InstanceSyncTaskDetails.class)
        .filter(ID_KEY, taskDetailsId)
        .first(createFindOptionsToHitSecondaryNode());
  }

  public InstanceSyncTaskDetails getForInfraMapping(String accountId, String infraMappingId) {
    Query<InstanceSyncTaskDetails> query = mongoPersistence.createQuery(InstanceSyncTaskDetails.class)
                                               .filter(InstanceSyncTaskDetailsKeys.accountId, accountId)
                                               .filter(InstanceSyncTaskDetailsKeys.infraMappingId, infraMappingId);
    return query.get(createFindOptionsToHitSecondaryNode());
  }

  public InstanceSyncTaskDetails fetchForCloudProvider(String accountId, String cloudProviderId) {
    Query<InstanceSyncTaskDetails> query = mongoPersistence.createQuery(InstanceSyncTaskDetails.class)
                                               .filter(InstanceSyncTaskDetailsKeys.accountId, accountId)
                                               .filter(InstanceSyncTaskDetailsKeys.cloudProviderId, cloudProviderId);

    return query.get(createFindOptionsToHitSecondaryNode());
  }

  public List<InstanceSyncTaskDetails> fetchAllForPerpetualTask(String accountId, String perpetualTaskId) {
    Query<InstanceSyncTaskDetails> query = mongoPersistence.createQuery(InstanceSyncTaskDetails.class)
                                               .filter(InstanceSyncTaskDetailsKeys.accountId, accountId)
                                               .filter(InstanceSyncTaskDetailsKeys.perpetualTaskId, perpetualTaskId);

    return query.asList(createFindOptionsToHitSecondaryNode());
  }

  public boolean isInstanceSyncTaskDetailsExist(String accountId, String perpetualTaskId) {
    Query<InstanceSyncTaskDetails> query = mongoPersistence.createQuery(InstanceSyncTaskDetails.class)
                                               .filter(InstanceSyncTaskDetailsKeys.accountId, accountId)
                                               .filter(InstanceSyncTaskDetailsKeys.perpetualTaskId, perpetualTaskId);

    return query.count(createCountOptionsToHitSecondaryNode()) > 0;
  }

  public InstanceSyncTaskDetails getInstanceSyncTaskDetails(String accountId, String instanceSyncTaskDetailId) {
    Query<InstanceSyncTaskDetails> query = mongoPersistence.createQuery(InstanceSyncTaskDetails.class)
                                               .filter(InstanceSyncTaskDetailsKeys.accountId, accountId)
                                               .filter(InstanceSyncTaskDetailsKeys.uuid, instanceSyncTaskDetailId);

    return query.get(createFindOptionsToHitSecondaryNode());
  }

  public void updateLastRun(
      String taskDetailsId, Set<CgReleaseIdentifiers> releasesToUpdate, Set<CgReleaseIdentifiers> releasesToDelete) {
    InstanceSyncTaskDetails taskDetails = getForId(taskDetailsId);
    taskDetails.setLastSuccessfulRun(System.currentTimeMillis());
    Set<CgReleaseIdentifiers> allReleases = new HashSet<>(releasesToUpdate);
    if (!releasesToUpdate.isEmpty() || !releasesToDelete.isEmpty()) {
      if (isNotEmpty(taskDetails.getReleaseIdentifiers())) {
        allReleases.addAll(taskDetails.getReleaseIdentifiers());
      }
      allReleases.removeAll(releasesToDelete);
      taskDetails.setReleaseIdentifiers(allReleases);
    }
    if (allReleases.isEmpty()) {
      delete(taskDetailsId);
    } else {
      save(taskDetails);
    }
  }

  private FindOptions createFindOptionsToHitSecondaryNode() {
    return new FindOptions().readPreference(ReadPreference.secondaryPreferred());
  }

  private CountOptions createCountOptionsToHitSecondaryNode() {
    return new CountOptions().readPreference(ReadPreference.secondaryPreferred());
  }
}
