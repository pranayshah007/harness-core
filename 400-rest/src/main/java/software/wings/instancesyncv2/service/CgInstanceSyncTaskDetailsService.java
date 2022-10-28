/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.dl.WingsMongoPersistence;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails.InstanceSyncTaskDetailsKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@OwnedBy(HarnessTeam.CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class CgInstanceSyncTaskDetailsService {
  private final WingsMongoPersistence mongoPersistence;

  public void save(InstanceSyncTaskDetails taskDetails) {
    if (Objects.isNull(taskDetails)) {
      log.error("Cannot save null task details. Doing nothing");
      return;
    }

    mongoPersistence.save(taskDetails);
  }

  public InstanceSyncTaskDetails getForId(String taskDetailsId) {
    return mongoPersistence.get(InstanceSyncTaskDetails.class, taskDetailsId);
  }

  public InstanceSyncTaskDetails getForInfraMapping(String accountId, String infraMappingId) {
    Query<InstanceSyncTaskDetails> query = mongoPersistence.createQuery(InstanceSyncTaskDetails.class)
                                               .filter(InstanceSyncTaskDetailsKeys.accountId, accountId)
                                               .filter(InstanceSyncTaskDetailsKeys.infraMappingId, infraMappingId);
    return query.get();
  }

  public InstanceSyncTaskDetails fetchForCloudProvider(String accountId, String cloudProviderId) {
    Query<InstanceSyncTaskDetails> query = mongoPersistence.createQuery(InstanceSyncTaskDetails.class)
                                               .filter(InstanceSyncTaskDetailsKeys.accountId, accountId)
                                               .filter(InstanceSyncTaskDetailsKeys.cloudProviderId, cloudProviderId);

    return query.get();
  }

  public List<InstanceSyncTaskDetails> fetchAllForPerpetualTask(String accountId, String perpetualTaskId) {
    Query<InstanceSyncTaskDetails> query = mongoPersistence.createQuery(InstanceSyncTaskDetails.class)
                                               .filter(InstanceSyncTaskDetailsKeys.accountId, accountId)
                                               .filter(InstanceSyncTaskDetailsKeys.perpetualTaskId, perpetualTaskId);

    return query.asList();
  }

  public void updateLastRun(String taskDetailsId) {
    InstanceSyncTaskDetails taskDetails = getForId(taskDetailsId);
    taskDetails.setLastSuccessfulRun(System.currentTimeMillis());
    save(taskDetails);
  }
}
