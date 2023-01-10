/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.persistence.HQuery.excludeAuthority;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

import io.harness.data.structure.EmptyPredicate;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;

import software.wings.api.DeploymentSummary;
import software.wings.beans.InfrastructureMapping;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.instance.InstanceSyncPerpetualTaskInfo.InstanceSyncPerpetualTaskInfoKeys;
import software.wings.service.intfc.instance.InstanceService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class InstanceSyncPerpetualTaskServiceImpl implements InstanceSyncPerpetualTaskService {
  @Inject private InstanceService instanceService;
  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private InstanceHandlerFactoryService instanceHandlerFactory;

  @Override
  public void createPerpetualTasks(InfrastructureMapping infrastructureMapping) {
    InstanceSyncByPerpetualTaskHandler handler = getInstanceHandler(infrastructureMapping);

    if (!shouldCreatePerpetualTasks(infrastructureMapping)) {
      return;
    }

    List<String> perpetualTaskIds =
        handler.getInstanceSyncPerpetualTaskCreator().createPerpetualTasks(infrastructureMapping);
    if (!perpetualTaskIds.isEmpty()) {
      save(infrastructureMapping.getAccountId(), infrastructureMapping.getUuid(), perpetualTaskIds);
    }
  }

  private InstanceSyncByPerpetualTaskHandler getInstanceHandler(InfrastructureMapping infrastructureMapping) {
    return (InstanceSyncByPerpetualTaskHandler) instanceHandlerFactory.getInstanceHandler(infrastructureMapping);
  }

  private boolean shouldCreatePerpetualTasks(InfrastructureMapping infrastructureMapping) {
    long instanceCount =
        instanceService.getInstanceCount(infrastructureMapping.getAppId(), infrastructureMapping.getUuid());
    return instanceCount > 0 && !perpetualTasksAlreadyExists(infrastructureMapping);
  }

  private boolean perpetualTasksAlreadyExists(InfrastructureMapping infrastructureMapping) {
    Optional<InstanceSyncPerpetualTaskInfo> info =
        getByAccountIdAndInfrastructureMappingId(infrastructureMapping.getAccountId(), infrastructureMapping.getUuid());
    return info.isPresent() && !info.get().getPerpetualTaskIds().isEmpty();
  }

  @Override
  public void createPerpetualTasksForNewDeployment(
      InfrastructureMapping infrastructureMapping, List<DeploymentSummary> deploymentSummaries) {
    InstanceSyncByPerpetualTaskHandler handler = getInstanceHandler(infrastructureMapping);

    List<PerpetualTaskRecord> existingTasks = getExistingPerpetualTasks(infrastructureMapping);

    List<String> newPerpetualTaskIds =
        handler.getInstanceSyncPerpetualTaskCreator().createPerpetualTasksForNewDeployment(
            deploymentSummaries, existingTasks, infrastructureMapping);

    if (EmptyPredicate.isNotEmpty(newPerpetualTaskIds)) {
      save(infrastructureMapping.getAccountId(), infrastructureMapping.getUuid(), newPerpetualTaskIds);
    }
  }

  private List<PerpetualTaskRecord> getExistingPerpetualTasks(InfrastructureMapping infrastructureMapping) {
    Optional<InstanceSyncPerpetualTaskInfo> info =
        getByAccountIdAndInfrastructureMappingId(infrastructureMapping.getAccountId(), infrastructureMapping.getUuid());
    return info
        .map(instanceSyncPerpetualTaskInfo
            -> instanceSyncPerpetualTaskInfo.getPerpetualTaskIds()
                   .stream()
                   .map(id -> perpetualTaskService.getTaskRecord(id))
                   .filter(Objects::nonNull)
                   .collect(Collectors.toList()))
        .orElse(emptyList());
  }

  @Override
  public void deletePerpetualTasks(InfrastructureMapping infrastructureMapping) {
    deletePerpetualTasks(infrastructureMapping.getAccountId(), infrastructureMapping.getUuid());
  }

  @Override
  public void deletePerpetualTasks(String accountId, String infrastructureMappingId) {
    Optional<InstanceSyncPerpetualTaskInfo> info =
        getByAccountIdAndInfrastructureMappingId(accountId, infrastructureMappingId);
    if (!info.isPresent()) {
      return;
    }

    for (String taskId : info.get().getPerpetualTaskIds()) {
      deletePerpetualTask(accountId, infrastructureMappingId, taskId);
    }
  }

  @Override
  public void resetPerpetualTask(String accountId, String perpetualTaskId) {
    perpetualTaskService.resetTask(accountId, perpetualTaskId, null);
  }

  private Optional<InstanceSyncPerpetualTaskInfo> getByAccountIdAndInfrastructureMappingId(
      String accountId, String infrastructureMappingId) {
    Query<InstanceSyncPerpetualTaskInfo> query = getInfoQuery(accountId, infrastructureMappingId);
    return ofNullable(query.get());
  }

  private Query<InstanceSyncPerpetualTaskInfo> getInfoQuery(String accountId, String infrastructureMappingId) {
    return wingsPersistence.createQuery(InstanceSyncPerpetualTaskInfo.class)
        .filter(InstanceSyncPerpetualTaskInfoKeys.accountId, accountId)
        .filter(InstanceSyncPerpetualTaskInfoKeys.infrastructureMappingId, infrastructureMappingId);
  }

  private void save(InstanceSyncPerpetualTaskInfo info) {
    wingsPersistence.save(info);
  }

  private void delete(String infrastructureMappingId) {
    Query<InstanceSyncPerpetualTaskInfo> deleteQuery =
        wingsPersistence.createQuery(InstanceSyncPerpetualTaskInfo.class, excludeAuthority)
            .filter(InstanceSyncPerpetualTaskInfoKeys.infrastructureMappingId, infrastructureMappingId);

    wingsPersistence.delete(deleteQuery);
  }

  @Override
  public void deletePerpetualTask(String accountId, String infrastructureMappingId, String perpetualTaskId) {
    perpetualTaskService.deleteTask(accountId, perpetualTaskId);

    Optional<InstanceSyncPerpetualTaskInfo> optionalInfo =
        getByAccountIdAndInfrastructureMappingId(accountId, infrastructureMappingId);
    if (!optionalInfo.isPresent()) {
      return;
    }
    InstanceSyncPerpetualTaskInfo info = optionalInfo.get();
    boolean wasFound = info.getPerpetualTaskIds().remove(perpetualTaskId);
    if (!wasFound) {
      return;
    }
    if (info.getPerpetualTaskIds().isEmpty()) {
      delete(infrastructureMappingId);
    } else {
      save(info);
    }
  }

  private void save(String accountId, String infrastructureMappingId, List<String> perpetualTaskIds) {
    Preconditions.checkArgument(
        EmptyPredicate.isNotEmpty(perpetualTaskIds), "perpetualTaskIds must not be empty or null");
    Optional<InstanceSyncPerpetualTaskInfo> infoOptional =
        getByAccountIdAndInfrastructureMappingId(accountId, infrastructureMappingId);
    if (!infoOptional.isPresent()) {
      save(InstanceSyncPerpetualTaskInfo.builder()
               .accountId(accountId)
               .infrastructureMappingId(infrastructureMappingId)
               .perpetualTaskIds(perpetualTaskIds)
               .build());
    } else {
      InstanceSyncPerpetualTaskInfo info = infoOptional.get();
      info.getPerpetualTaskIds().addAll(perpetualTaskIds);
      save(info);
    }
  }

  @Override
  public void pruneByInfrastructureMapping(String appId, String infrastructureMappingId) {
    PerpetualTaskRecord perpetualTaskRecord =
        wingsPersistence.createQuery(PerpetualTaskRecord.class)
            .field(PerpetualTaskRecordKeys.client_params + ".infrastructureMappingId")
            .equal(infrastructureMappingId)
            .get();
    if (perpetualTaskRecord == null) {
      return;
    }
    deletePerpetualTasks(perpetualTaskRecord.getAccountId(), infrastructureMappingId);
  }

  private InfrastructureMapping get(String appId, String infraMappingId) {
    return wingsPersistence.getWithAppId(InfrastructureMapping.class, appId, infraMappingId);
  }
}
